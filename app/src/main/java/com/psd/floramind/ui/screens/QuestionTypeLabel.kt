package com.psd.floramind.ui.screens

import com.psd.floramind.model.CustomQuestionType
import com.psd.floramind.model.Question
import com.psd.floramind.model.QuestionType

/** 题型中文名（全局共用，避免每个页面重复定义） */
fun QuestionType.label(): String = when (this) {
    QuestionType.DEFINITION -> "名词解释"
    QuestionType.FILL_BLANK -> "填空"
    QuestionType.CHOICE -> "选择"
    QuestionType.SHORT_ANSWER -> "简答"
    QuestionType.DIAGRAM -> "填图"
    QuestionType.ESSAY -> "论述"
    QuestionType.CUSTOM -> "自定义"
}

/**
 * 自定义题型的显示名：优先用题库包 question_types.json 里的 name，
 * 其次是题目里写的 customType id，最后兜底「自定义题型」。
 */
fun Question.customTypeName(definition: CustomQuestionType? = null): String {
    definition?.let { return it.safeName() }
    return this.customType?.trim()?.takeIf { it.isNotBlank() } ?: "自定义题型"
}

/** 列表型页面使用：id -> 题型定义 */
fun Question.displayTypeName(
    customTypes: Map<String, CustomQuestionType> = emptyMap()
): String = if (type == QuestionType.CUSTOM) {
    customTypeName(customTypes[customType.orEmpty()])
} else {
    type.label()
}
