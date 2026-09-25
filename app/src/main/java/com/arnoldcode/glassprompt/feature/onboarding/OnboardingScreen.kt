package com.arnoldcode.glassprompt.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButton
import com.arnoldcode.glassprompt.core.designsystem.glass.GlassBackdrop
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import kotlinx.coroutines.launch

private data class OnboardingPage(@param:StringRes val title: Int, @param:StringRes val body: Int)

private val Pages = listOf(
    OnboardingPage(R.string.onboarding_1_title, R.string.onboarding_1_body),
    OnboardingPage(R.string.onboarding_2_title, R.string.onboarding_2_body),
    OnboardingPage(R.string.onboarding_3_title, R.string.onboarding_3_body),
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) { if (finished) onFinished() }
    OnboardingContent(onStart = viewModel::onStart)
}

@Composable
internal fun OnboardingContent(onStart: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState { Pages.size }
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == Pages.lastIndex

    Box(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .testTag("onboarding_screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = GlassTheme.spacing.xs),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onStart, modifier = Modifier.alpha(if (isLastPage) 0f else 1f), enabled = !isLastPage) {
                    Text(stringResource(R.string.action_skip), color = GlassTheme.colors.textSecondary)
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page -> OnboardingPageContent(page) }
            PageIndicator(pagerState)
            Spacer(Modifier.height(GlassTheme.spacing.lg))
            GlassButton(
                text = stringResource(if (isLastPage) R.string.onboarding_start else R.string.action_next),
                onClick = {
                    if (isLastPage) onStart() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GlassTheme.spacing.lg)
                    .testTag("onboarding_primary_button"),
            )
            Spacer(Modifier.height(GlassTheme.spacing.lg))
        }
    }
}

@Composable
private fun OnboardingPageContent(page: Int) {
    val content = Pages[page]
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = GlassTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            OnboardingIllustration(page)
        }
        Text(
            text = stringResource(content.title),
            style = MaterialTheme.typography.headlineLarge,
            color = GlassTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(GlassTheme.spacing.sm))
        Text(
            text = stringResource(content.body),
            style = MaterialTheme.typography.bodyLarge,
            color = GlassTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(GlassTheme.spacing.xl))
    }
}

@Composable
private fun PageIndicator(state: PagerState) {
    val description = stringResource(R.string.onboarding_page_indicator, state.currentPage + 1, state.pageCount)
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(state.pageCount) { index ->
            val selected = index == state.currentPage
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) GlassTheme.colors.accent else GlassTheme.colors.glassFillStrong,
                label = "dotColor",
            )
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .background(color, GlassTheme.shapes.pill),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingPreview() {
    GlassTheme(darkTheme = true) {
        GlassBackdrop { OnboardingContent(onStart = {}) }
    }
}
