package com.psd.floramind.session

import com.psd.floramind.model.Question

/**
 * 进程内的临时练习会话。用于从筛选界面传递题目列表到 PracticeScreen。
 * 不属于状态持久化，导航返回后会被下一次赋值覆盖。
 */
object PracticeSessionHolder {
    var pendingQuestions: List<Question> = emptyList()
    var startIndex: Int = 0
}