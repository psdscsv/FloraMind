package com.psd.floramind.model

/**
 * 单题的做题记录。
 *
 * 数据不再存 Room，而是直接写进题库包目录下的 practice.json：
 *  - 导出 ZIP / 上传到服务器时天然携带做题信息；
 *  - 重命名、移动、删除题库包时记录跟着目录一起走，不需要同步数据库。
 */
data class PracticeRecord(
    /** 累计作答次数 */
    val seen: Int = 0,
    /** 累计答对次数 */
    val right: Int = 0,
    /** 累计答错次数 */
    val wrong: Int = 0,
    /** 最近一次作答内容 */
    val lastAnswer: String? = null,
    /** 最近一次得分（选择题 100/0，主观题 100/0 或 AI 评分） */
    val lastScore: Int = 0,
    /** 最近一次是否判对 */
    val lastCorrect: Boolean = false,
    /** 最近一次作答时间（毫秒） */
    val lastTime: Long = 0L
) {
    val isDone: Boolean get() = seen > 0

    /** 错题：做错过，且最近一次仍做错（答对一次即移出错题本） */
    val isMistake: Boolean get() = wrong > 0 && !lastCorrect
}

/** practice.json 的根结构 */
data class PracticeBook(
    val version: Int = 1,
    val updatedAt: Long = 0L,
    val records: Map<Int, PracticeRecord>? = null
)

/** 题目在练习会话中的唯一键：题号在不同题库里会重复，必须带上包名 */
val Question.practiceKey: String get() = "$packageName#$id"
