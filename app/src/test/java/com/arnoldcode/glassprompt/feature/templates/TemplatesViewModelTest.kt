package com.arnoldcode.glassprompt.feature.templates

import com.arnoldcode.glassprompt.domain.model.TemplateCategory
import com.arnoldcode.glassprompt.domain.usecase.GetTemplatesUseCase
import com.arnoldcode.glassprompt.testing.FakeTemplateRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TemplatesViewModelTest {

    @Test
    fun `filters by category and manages the preview`() {
        val vm = TemplatesViewModel(GetTemplatesUseCase(FakeTemplateRepository()))
        assertThat(vm.uiState.value.visible).hasSize(3)
        assertThat(vm.uiState.value.categories)
            .containsExactly(TemplateCategory.TUTORIAL, TemplateCategory.REVIEW, TemplateCategory.EDUCATIONAL).inOrder()

        vm.onCategorySelected(TemplateCategory.REVIEW)
        assertThat(vm.uiState.value.visible.map { it.id }).containsExactly("review")

        vm.onPreview(vm.uiState.value.visible.single())
        assertThat(vm.uiState.value.preview?.id).isEqualTo("review")
        vm.onPreviewDismiss()
        assertThat(vm.uiState.value.preview).isNull()
    }
}
