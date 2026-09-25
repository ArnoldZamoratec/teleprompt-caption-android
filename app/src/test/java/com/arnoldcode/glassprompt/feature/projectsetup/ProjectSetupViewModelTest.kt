package com.arnoldcode.glassprompt.feature.projectsetup

import androidx.lifecycle.SavedStateHandle
import com.arnoldcode.glassprompt.domain.model.CameraLens
import com.arnoldcode.glassprompt.domain.model.TeleprompterSettings
import com.arnoldcode.glassprompt.domain.model.VideoResolution
import com.arnoldcode.glassprompt.domain.usecase.CreateProjectUseCase
import com.arnoldcode.glassprompt.domain.usecase.GetProjectUseCase
import com.arnoldcode.glassprompt.domain.usecase.GetTemplatesUseCase
import com.arnoldcode.glassprompt.domain.usecase.UpdateProjectUseCase
import com.arnoldcode.glassprompt.testing.FakeProjectRepository
import com.arnoldcode.glassprompt.testing.FakeTemplateRepository
import com.arnoldcode.glassprompt.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class ProjectSetupViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeProjectRepository()

    private fun viewModel(vararg args: Pair<String, Any?>) = ProjectSetupViewModel(
        SavedStateHandle(mapOf(*args)),
        GetProjectUseCase(repository),
        CreateProjectUseCase(repository),
        UpdateProjectUseCase(repository),
        GetTemplatesUseCase(FakeTemplateRepository()),
    )

    @Test
    fun `blank name shows an error and creates nothing`() {
        val vm = viewModel()
        vm.onSave()
        assertThat(vm.uiState.value.showNameError).isTrue()
        assertThat(vm.uiState.value.result).isNull()
    }

    @Test
    fun `create persists settings and reports the new id`() {
        val vm = viewModel()
        vm.onNameChange("Mi video")
        vm.onLensSelected(CameraLens.BACK)
        vm.onResolutionSelected(VideoResolution.HD_720)
        vm.onFrameRateSelected(60)
        vm.onSpeedChange(9f) // clamped
        vm.onMirrorChange(true)

        vm.onSave()

        val result = vm.uiState.value.result as SetupResult.Created
        val project = repository.project(result.projectId)!!
        assertThat(project.name).isEqualTo("Mi video")
        assertThat(project.recording.lens).isEqualTo(CameraLens.BACK)
        assertThat(project.recording.resolution).isEqualTo(VideoResolution.HD_720)
        assertThat(project.recording.frameRate).isEqualTo(60)
        assertThat(project.teleprompter.speed).isEqualTo(TeleprompterSettings.SpeedRange.endInclusive)
        assertThat(project.teleprompter.mirror).isTrue()
    }

    @Test
    fun `template prefills the name and seeds the script body`() {
        val vm = viewModel(ProjectSetupViewModel.ARG_TEMPLATE_ID to "review")
        assertThat(vm.uiState.value.name).isEqualTo("Reseña")
        assertThat(vm.uiState.value.templateTitle).isEqualTo("Reseña")

        vm.onSave()

        val id = (vm.uiState.value.result as SetupResult.Created).projectId
        assertThat(repository.script(repository.project(id)!!.scriptId)?.body).isEqualTo("Lo bueno y lo malo.")
    }

    @Test
    fun `editing loads the project and saves changes in place`() {
        val existing = repository.seed("Original")
        val vm = viewModel(ProjectSetupViewModel.ARG_PROJECT_ID to existing.id)
        assertThat(vm.uiState.value.isEditing).isTrue()
        assertThat(vm.uiState.value.name).isEqualTo("Original")

        vm.onNameChange("Renombrado")
        vm.onFontSizeChange(50f)
        vm.onSave()

        assertThat(vm.uiState.value.result).isEqualTo(SetupResult.Updated)
        assertThat(repository.project(existing.id)?.name).isEqualTo("Renombrado")
        assertThat(repository.project(existing.id)?.teleprompter?.fontSizeSp).isEqualTo(50f)
    }

    @Test
    fun `missing project to edit shows an error`() {
        val vm = viewModel(ProjectSetupViewModel.ARG_PROJECT_ID to "missing")
        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }
}
