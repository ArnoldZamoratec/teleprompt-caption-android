package com.arnoldcode.glassprompt.data.templates

import android.content.Context
import androidx.annotation.StringRes
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.domain.model.ScriptTemplate
import com.arnoldcode.glassprompt.domain.model.TemplateCategory
import com.arnoldcode.glassprompt.domain.repository.TemplateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Built-in templates. Text lives in `res/values/templates.xml` so it can be translated. */
@Singleton
class ResourceTemplateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TemplateRepository {

    private class Definition(
        val id: String,
        val category: TemplateCategory,
        @param:StringRes val title: Int,
        @param:StringRes val description: Int,
        @param:StringRes val body: Int,
    )

    private val definitions = listOf(
        Definition("tutorial_steps", TemplateCategory.TUTORIAL, R.string.tpl_tutorial_title, R.string.tpl_tutorial_desc, R.string.tpl_tutorial_body),
        Definition("product_review", TemplateCategory.REVIEW, R.string.tpl_review_title, R.string.tpl_review_desc, R.string.tpl_review_body),
        Definition("short_ad", TemplateCategory.AD, R.string.tpl_ad_title, R.string.tpl_ad_desc, R.string.tpl_ad_body),
        Definition("channel_intro", TemplateCategory.INTRO, R.string.tpl_intro_title, R.string.tpl_intro_desc, R.string.tpl_intro_body),
        Definition("personal_story", TemplateCategory.STORY, R.string.tpl_story_title, R.string.tpl_story_desc, R.string.tpl_story_body),
        Definition("explainer", TemplateCategory.EDUCATIONAL, R.string.tpl_explainer_title, R.string.tpl_explainer_desc, R.string.tpl_explainer_body),
        Definition("quick_tips", TemplateCategory.EDUCATIONAL, R.string.tpl_tips_title, R.string.tpl_tips_desc, R.string.tpl_tips_body),
        Definition("launch_announcement", TemplateCategory.AD, R.string.tpl_launch_title, R.string.tpl_launch_desc, R.string.tpl_launch_body),
    )

    override fun templates(): List<ScriptTemplate> = definitions.map { it.load() }

    override fun template(id: String): ScriptTemplate? = definitions.firstOrNull { it.id == id }?.load()

    private fun Definition.load() = ScriptTemplate(
        id = id,
        category = category,
        title = context.getString(title),
        description = context.getString(description),
        body = context.getString(body),
    )
}
