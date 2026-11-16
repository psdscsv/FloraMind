package com.psd.floramind.ui.screens

import com.psd.floramind.model.QuestionType

/** 题型中文名（全局共用，避免每个页面重复定义） */
fun QuestionType.label(): String = when (this) {
    QuestionType.DEFINITION -> "名词解释"
    QuestionType.FILL_BLANK -> "填空"
    QuestionType.CHOICE -> "选择"
    QuestionType.SHORT_ANSWER -> "简答"
    QuestionType.DIAGRAM -> "填图"
    QuestionType.ESSAY -> "论述"
}
