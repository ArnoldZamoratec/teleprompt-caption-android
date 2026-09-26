package com.arnoldcode.glassprompt.feature.editor

import androidx.compose.foundation.layout.PaddingValues
import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButtonStyle
import com.arnoldcode.glassprompt.core.designsystem.component.GlassDialog
import com.arnoldcode.glassprompt.core.designsystem.component.GlassEmptyState
import com.arnoldcode.glassprompt.core.designsystem.component.GlassIconButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassPanel
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSnackbarHost
import com.arnoldcode.glassprompt.core.designsystem.component.GlassTextField
import com.arnoldcode.glassprompt.core.designsystem.component.GlassTopBar
import com.arnoldcode.glassprompt.core.designsystem.glass.GlassBackdrop
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.domain.script.ScriptStats
import com.arnoldcode.glassprompt.feature.common.durationLabel
import kotlinx.coroutines.launch

@Composable
fun ScriptEditorScreen(
    launchImport: Boolean,
    onBack: () -> Unit,
    onEditProject: () -> Unit,
    onOpenTakes: () -> Unit,
    onRehearse: () -> Unit,
    onRecord: () -> Unit,
    viewModel: ScriptEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // The field owns cursor and selection; the ViewModel only pushes programmatic edits.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(state.text)) }
    LaunchedEffect(viewModel) {
        viewModel.commands.collect { command ->
            field = when (command) {
                is EditorCommand.SetText -> TextFieldValue(
                    text = command.text,
                    selection = command.selection?.let { TextRange(it.first, it.last + 1) } ?: TextRange(command.text.length),
                )
                is EditorCommand.Select -> field.copy(selection = TextRange(command.range.first, command.range.last + 1))
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImportDocument(uri.toString())
    }
    val openPicker = { picker.launch(arrayOf("text/plain")) }
    var autoImportDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (launchImport && !autoImportDone && !state.isLoading && !state.notFound) {
            autoImportDone = true
            openPicker()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    ScriptEditorContent(
        state = state,
        field = field,
        actions = EditorActions(
            onBack = onBack,
            onFieldChange = { value ->
                field = value
                viewModel.onTextChange(value.text)
            },
            onToggleSearch = viewModel::onToggleSearch,
            onQueryChange = viewModel::onQueryChange,
            onReplacementChange = viewModel::onReplacementChange,
            onNextMatch = viewModel::onNextMatch,
            onPreviousMatch = viewModel::onPreviousMatch,
            onReplaceCurrent = viewModel::onReplaceCurrent,
            onReplaceAll = viewModel::onReplaceAll,
            onImportFile = openPicker,
            onPaste = {
                scope.launch {
                    val text = clipboard.getClipEntry()?.firstText(context)
                    viewModel.onPasteFromClipboard(text)
                }
            },
            onImportReplace = viewModel::onImportReplace,
            onImportAppend = viewModel::onImportAppend,
            onImportDismiss = viewModel::onImportDismiss,
            onEditProject = onEditProject,
            onOpenTakes = { viewModel.flush(); onOpenTakes() },
            onRehearse = { viewModel.flush(); onRehearse() },
            onRecord = { viewModel.flush(); onRecord() },
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

private fun ClipEntry.firstText(context: android.content.Context): String? {
    val clip: ClipData = clipData
    return if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
}

internal data class EditorActions(
    val onBack: () -> Unit = {},
    val onFieldChange: (TextFieldValue) -> Unit = {},
    val onToggleSearch: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onReplacementChange: (String) -> Unit = {},
    val onNextMatch: () -> Unit = {},
    val onPreviousMatch: () -> Unit = {},
    val onReplaceCurrent: () -> Unit = {},
    val onReplaceAll: () -> Unit = {},
    val onImportFile: () -> Unit = {},
    val onPaste: () -> Unit = {},
    val onImportReplace: () -> Unit = {},
    val onImportAppend: () -> Unit = {},
    val onImportDismiss: () -> Unit = {},
    val onEditProject: () -> Unit = {},
    val onOpenTakes: () -> Unit = {},
    val onRehearse: () -> Unit = {},
    val onRecord: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScriptEditorContent(
    state: EditorUiState,
    field: TextFieldValue,
    actions: EditorActions,
    modifier: Modifier = Modifier,
) {
    val spacing = GlassTheme.spacing
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.text()
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            actions.onMessageShown()
        }
    }

    Box(modifier.fillMaxSize().testTag("script_editor_screen")) {
        Column(Modifier.fillMaxSize().imePadding()) {
            EditorTopBar(state, actions)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                if (state.search.visible) SearchPanel(state.search, actions)
                when {
                    state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                        CircularProgressIndicator(color = GlassTheme.colors.accent)
                    }
                    state.notFound -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                        GlassEmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = stringResource(R.string.editor_not_found_title),
                            message = stringResource(R.string.editor_not_found_body),
                            actionText = stringResource(R.string.action_back),
                            onAction = actions.onBack,
                        )
                    }
                    else -> ScriptField(state, field, actions.onFieldChange, Modifier.weight(1f))
                }
                StatsRow(state.stats, state.saveStatus)
            }
            if (!WindowInsets.isImeVisible && !state.notFound) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    GlassButton(
                        text = stringResource(R.string.editor_rehearse),
                        icon = Icons.Outlined.Slideshow,
                        onClick = actions.onRehearse,
                        style = GlassButtonStyle.Secondary,
                        enabled = !state.isLoading,
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        text = stringResource(R.string.editor_record),
                        icon = Icons.Outlined.Videocam,
                        onClick = actions.onRecord,
                        enabled = !state.isLoading,
                        modifier = Modifier.weight(1f).testTag("editor_record"),
                    )
                }
            }
        }
        GlassSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding())
    }

    val pending = state.pendingImport
    if (pending != null) {
        GlassDialog(
            title = stringResource(R.string.editor_import_dialog_title),
            message = stringResource(R.string.editor_import_dialog_body),
            onDismissRequest = actions.onImportDismiss,
            confirmText = stringResource(R.string.editor_import_replace),
            onConfirm = actions.onImportReplace,
            dismissText = stringResource(R.string.editor_import_append),
            onDismiss = actions.onImportAppend,
        )
    }
}

@Composable
private fun EditorTopBar(state: EditorUiState, actions: EditorActions) {
    var menuOpen by remember { mutableStateOf(false) }
    GlassTopBar(
        title = state.projectName.ifEmpty { stringResource(R.string.editor_title) },
        navigationIcon = {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), actions.onBack)
        },
        actions = {
            GlassIconButton(
                icon = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.editor_search),
                onClick = actions.onToggleSearch,
                selected = state.search.visible,
                enabled = !state.isLoading && !state.notFound,
            )
            Box {
                GlassIconButton(
                    icon = Icons.Outlined.FileOpen,
                    contentDescription = stringResource(R.string.editor_import),
                    onClick = { menuOpen = true },
                    enabled = !state.isLoading && !state.notFound,
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = GlassTheme.colors.backgroundElevated,
                    shape = GlassTheme.shapes.medium,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_import_file)) },
                        leadingIcon = { Icon(Icons.Outlined.FileOpen, contentDescription = null) },
                        onClick = { menuOpen = false; actions.onImportFile() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_import_clipboard)) },
                        leadingIcon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null) },
                        onClick = { menuOpen = false; actions.onPaste() },
                    )
                }
            }
            GlassIconButton(
                icon = Icons.Outlined.VideoLibrary,
                contentDescription = stringResource(R.string.editor_takes),
                onClick = actions.onOpenTakes,
                enabled = !state.isLoading && !state.notFound,
                modifier = Modifier.testTag("editor_takes"),
            )
            GlassIconButton(
                icon = Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.editor_project_settings),
                onClick = actions.onEditProject,
                enabled = !state.isLoading && !state.notFound,
            )
        },
    )
}

@Composable
private fun SearchPanel(search: SearchState, actions: EditorActions) {
    val spacing = GlassTheme.spacing
    val queryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { queryFocus.requestFocus() }
    GlassPanel(Modifier.fillMaxWidth().padding(top = spacing.sm), contentPadding = PaddingValues(spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            GlassTextField(
                value = search.query,
                onValueChange = actions.onQueryChange,
                placeholder = stringResource(R.string.editor_search_placeholder),
                focusRequester = queryFocus,
                modifier = Modifier.weight(1f).testTag("editor_search_query"),
            )
            val counter = if (search.matches.isEmpty()) {
                stringResource(R.string.editor_search_none)
            } else {
                stringResource(R.string.editor_search_counter, search.current + 1, search.matches.size)
            }
            Text(
                counter,
                style = MaterialTheme.typography.labelMedium,
                color = GlassTheme.colors.textSecondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            GlassIconButton(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.editor_search_previous), actions.onPreviousMatch, size = 40.dp, enabled = search.matches.isNotEmpty())
            GlassIconButton(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.editor_search_next), actions.onNextMatch, size = 40.dp, enabled = search.matches.isNotEmpty())
        }
        Spacer(Modifier.size(spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            GlassTextField(
                value = search.replacement,
                onValueChange = actions.onReplacementChange,
                placeholder = stringResource(R.string.editor_replace_placeholder),
                modifier = Modifier.weight(1f),
            )
            GlassIconButton(Icons.Outlined.FindReplace, stringResource(R.string.editor_replace), actions.onReplaceCurrent, size = 40.dp, enabled = search.current >= 0)
            GlassIconButton(Icons.Outlined.DoneAll, stringResource(R.string.editor_replace_all), actions.onReplaceAll, size = 40.dp, enabled = search.matches.isNotEmpty())
        }
    }
}

/**
 * The script body. The field grows with its content inside a vertical scroll (so search can
 * scroll a match into view) and is at least as tall as the viewport so any tap focuses it.
 */
@Composable
private fun ScriptField(
    state: EditorUiState,
    field: TextFieldValue,
    onFieldChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val spacing = GlassTheme.spacing
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val highlight = remember(state.search.matches, state.search.current, colors) {
        if (state.search.matches.isEmpty()) {
            VisualTransformation.None
        } else {
            MatchHighlightTransformation(state.search.matches, state.search.current, colors.accent.copy(alpha = 0.28f), colors.accent.copy(alpha = 0.6f))
        }
    }
    // Scroll the current search match into view.
    val currentMatch = state.search.matches.getOrNull(state.search.current)
    LaunchedEffect(currentMatch, layout) {
        val result = layout ?: return@LaunchedEffect
        val match = currentMatch ?: return@LaunchedEffect
        if (match.first >= result.layoutInput.text.length) return@LaunchedEffect
        val top = result.getBoundingBox(match.first).top.toInt()
        scroll.animateScrollTo((top - 120).coerceAtLeast(0))
    }
    val placeholder = stringResource(R.string.editor_placeholder)
    val fieldDescription = stringResource(R.string.editor_field_description)

    GlassPanel(modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
                BasicTextField(
                    value = field,
                    onValueChange = onFieldChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f),
                    cursorBrush = SolidColor(colors.accent),
                    visualTransformation = highlight,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    onTextLayout = { layout = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = viewport)
                        .padding(spacing.md)
                        .testTag("editor_field")
                        .semantics { contentDescription = fieldDescription },
                    decorationBox = { inner ->
                        Box {
                            if (field.text.isEmpty()) {
                                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.textTertiary)
                            }
                            inner()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StatsRow(stats: ScriptStats, saveStatus: SaveStatus) {
    val colors = GlassTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GlassTheme.spacing.xs).testTag("editor_stats"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.editor_words, stats.words, stats.words) + " · " +
                pluralStringResource(R.plurals.editor_characters, stats.characters, stats.characters) + " · " +
                durationLabel(stats.readingTimeSeconds),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        val (icon, label, tint) = when (saveStatus) {
            SaveStatus.Saved -> Triple(Icons.Outlined.CloudDone, stringResource(R.string.editor_saved), colors.success)
            SaveStatus.Pending, SaveStatus.Saving -> Triple(Icons.Outlined.CloudSync, stringResource(R.string.editor_saving), colors.textTertiary)
            SaveStatus.Error -> Triple(Icons.Outlined.CloudOff, stringResource(R.string.editor_save_error), colors.error)
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(GlassTheme.spacing.xxs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, modifier = Modifier.testTag("editor_save_status"))
    }
}

@Composable
private fun EditorMessage.text(): String = when (this) {
    is EditorMessage.Replaced -> pluralStringResource(R.plurals.editor_replaced, count, count)
    EditorMessage.Imported -> stringResource(R.string.editor_imported)
    EditorMessage.ClipboardEmpty -> stringResource(R.string.editor_clipboard_empty)
    is EditorMessage.Error -> stringResource(res)
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ScriptEditorPreview() {
    val text = "Hola, bienvenidos a mi canal.\n\nHoy les muestro cómo grabar sin olvidar ni una palabra."
    GlassTheme(darkTheme = true) {
        GlassBackdrop {
            ScriptEditorContent(
                state = EditorUiState(
                    isLoading = false,
                    projectName = "Reseña de cámara",
                    text = text,
                    stats = ScriptStats.of(text),
                    search = SearchState(visible = true, query = "hoy", matches = listOf(31..33), current = 0),
                ),
                field = TextFieldValue(text),
                actions = EditorActions(),
            )
        }
    }
}
