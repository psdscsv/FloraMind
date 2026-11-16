package com.psd.floramind.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController

/**
 * 顶部返回箭头专用：每次进入页面只允许 pop 一次。
 *
 * 以前直接在 onClick 里写 navController.popBackStack()，快速连点两下时
 * 第二次点击也会执行 pop，把 MainScreen 一起弹出返回栈，NavHost 变成空白。
 * 这里用一次性标记拦住第二次点击；页面重新进入时会拿到新的标记。
 */
@Composable
fun rememberBackAction(navController: NavController): () -> Unit {
    var handled by remember { mutableStateOf(false) }
    return remember(navController) {
        {
            // 双重保险：
            // 1) handled 拦住同一页面上的第二次点击；
            // 2) previousBackStackEntry == null 说明下面已经没有页面（比如 MainScreen
            //    已经被误弹出去），此时绝不能再 pop，否则返回栈清空就只剩白屏。
            if (!handled && navController.previousBackStackEntry != null) {
                handled = true
                navController.popBackStack()
            }
        }
    }
}
