package com.psd.floramind.model

/** 自定义题型定义的固定文件名（放在题库包目录下，和 questions.json 同级） */
const val QUESTION_TYPES_FILE = "question_types.json"

/** 自定义题型的作答方式 */
enum class CustomLayout {
    /** 一道大题拆成多个小问（默认） */
    PARTS,

    /** 一段完整作答 */
    SINGLE,

    /** 多选题：勾选多个选项后自动判定 */
    MULTI_CHOICE,

    /** 图文题：展示图片 + 描述，写文字作答 */
    IMAGE_TEXT;

    companion object {
        /** 未知/拼错时按 PARTS 兜底，避免题库包写错就崩 */
        fun from(raw: String?): CustomLayout = when (raw?.trim()?.lowercase()) {
            "single" -> SINGLE
            "multi_choice" -> MULTI_CHOICE
            "image_text" -> IMAGE_TEXT
            else -> PARTS
        }
    }
}

/**
 * 题库包自定义的题型。
 *
 * 题库包目录下放一个 question_types.json，例如：
 * ```json
 * { "id": "multi_part", "name": "多小问", "layout": "parts", "grading": "self" }
 * ```
 * 该包里的题目用 "type": "CUSTOM" + "customType": "multi_part" 引用它。
 *
 * layout（作答方式）：
 *  - parts        ：一道大题拆成多个小问，每个小问一个作答框（默认）
 *  - single       ：一段完整作答（类似论述题，但题型名/评分方式可由题库自定义）
 *  - multi_choice ：多选题，options 给选项、correctOptions 给正确选项，自动判分
 *  - image_text   ：展示 imageName 指定的图片 + stem 描述，写文字作答
 *
 * grading（评分方式）：
 *  - self   ：显示答案后自己判对错（默认）
 *  - ai     ：额外提供「AI 评分」按钮（需要配置 API Key；multi_choice 自动判分不需要）
 */
data class CustomQuestionType(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val layout: String = "parts",
    val grading: String = "self"
) {
    val layoutKind: CustomLayout get() = CustomLayout.from(layout)
    val isPartsLayout: Boolean get() = layoutKind == CustomLayout.PARTS
    val isSingleLayout: Boolean get() = layoutKind == CustomLayout.SINGLE
    val isMultiChoiceLayout: Boolean get() = layoutKind == CustomLayout.MULTI_CHOICE
    val isImageTextLayout: Boolean get() = layoutKind == CustomLayout.IMAGE_TEXT
    val useAiGrading: Boolean get() = grading.equals("ai", true)

    /** 解析失败/字段缺失时也能安全显示 */
    fun safeName(): String = name.ifBlank { id.ifBlank { "自定义题型" } }
}

/**
 * 题库包里写错字段（比如 "name": null）时不至于让做题页崩溃。
 * id 为空的类型直接丢弃。
 */
fun CustomQuestionType.sanitizedOrNull(): CustomQuestionType? {
    val safeId = (id as String?).orEmpty().trim()
    if (safeId.isEmpty()) return null
    val safeName = (name as String?).orEmpty().trim().ifEmpty { safeId }
    val safeLayout = (layout as String?).orEmpty().trim().ifEmpty { "parts" }
    val safeGrading = (grading as String?).orEmpty().trim().ifEmpty { "self" }
    return copy(
        id = safeId,
        name = safeName,
        description = (description as String?)?.trim()?.ifEmpty { null },
        layout = safeLayout,
        grading = safeGrading
    )
}

/** 自定义题型里的一个小问 */
data class QuestionPart(
    val stem: String = "",
    val answer: String? = null,
    val keywords: List<String>? = null
)

/** 该题是不是题库包自定义题型 */
val Question.isCustomType: Boolean get() = type == QuestionType.CUSTOM

/**
 * 取出一道题实际要作答的小问。
 * 自定义题型没写 parts 时退化成「整题一个作答框」，不会白屏。
 */
fun Question.resolvedParts(): List<QuestionPart> {
    val declared = parts.orEmpty().mapNotNull { part ->
        @Suppress("USELESS_CAST")
        val partStem = (part.stem as String?).orEmpty()
        if (partStem.isBlank() && part.answer.isNullOrBlank()) {
            null
        } else {
            part.copy(stem = partStem)
        }
    }
    if (declared.isNotEmpty()) return declared
    return listOf(
        QuestionPart(
            stem = stem,
            answer = correctAnswer,
            keywords = keywords
        )
    )
}

/**
 * 把多小问的作答拼成一段文本（带编号），用于保存做题记录和 AI 评分。
 * 空作答会跳过。
 */
fun Question.composePartsAnswer(answers: List<String>): String {
    val parts = resolvedParts()
    return parts.indices.mapNotNull { index ->
        val text = answers.getOrNull(index)?.trim().orEmpty()
        if (text.isEmpty()) null else "（${index + 1}）$text"
    }.joinToString("\n")
}

/**
 * 多选题判定：所选选项与 correctOptions 完全一致（顺序无关）才算对。
 * 没配置 correctOptions 时返回 false（此时界面不会显示「提交答案」）。
 */
fun Question.isMultiChoiceCorrect(selected: Collection<String>): Boolean {
    val correct = correctOptions.orEmpty()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
    if (correct.isEmpty()) return false
    val picked = selected.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    return picked == correct
}

/**
 * 多选题的正确答案：返回「选项字母 -> 选项内容」，例如 A to "身体分头、胸、腹"。
 * 选项里找不到答案的条目会被忽略。
 */
fun Question.correctOptionEntries(): List<Pair<String, String>> =
    correctOptions.orEmpty().mapNotNull { answer ->
        val index = options.orEmpty().indexOfFirst { it.trim() == answer.trim() }
        if (index in 0..25) ('A' + index).toString() to answer else null
    }

/** 多选题正确答案的字母列表（A、B…），用于答案区展示 */
fun Question.correctOptionLetters(): List<String> = correctOptionEntries().map { it.first }
