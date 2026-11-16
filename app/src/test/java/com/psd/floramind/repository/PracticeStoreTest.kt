package com.psd.floramind.repository

import com.psd.floramind.model.PracticeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * practice.json 的读写回归测试：
 * 做题记录必须能原样写进题库包目录、再原样读回（导出/导入依赖这一点）。
 */
class PracticeStoreTest {

    private fun newRoot(): File = Files.createTempDirectory("practice_store").toFile()

    @Test
    fun recordThenRead_roundTrip() {
        val root = newRoot()
        val store = PracticeStore(root)

        val first = store.record("动物学/无脊椎动物", 216, correct = false, score = 0, userAnswer = "错误选项")
        assertEquals(1, first.seen)
        assertEquals(0, first.right)
        assertEquals(1, first.wrong)
        assertFalse(first.lastCorrect)
        assertTrue(first.isMistake)

        val second = store.record("动物学/无脊椎动物", 216, correct = true, score = 100, userAnswer = "正确选项")
        assertEquals(2, second.seen)
        assertEquals(1, second.right)
        assertEquals(1, second.wrong)
        assertTrue(second.lastCorrect)
        assertFalse("答对后应移出错题本", second.isMistake)

        // 文件确实落在题库包目录里
        assertTrue(File(root, "动物学/无脊椎动物/practice.json").isFile)

        // 重新读一遍
        val loaded = store.read("动物学/无脊椎动物")[216]
        assertEquals(second, loaded)
    }

    @Test
    fun readAll_findsNestedPackages() {
        val root = newRoot()
        val store = PracticeStore(root)
        store.record("动物学/无脊椎动物", 1, correct = true, score = 100, userAnswer = "A")
        store.record("动物学/脊椎动物", 1, correct = false, score = 0, userAnswer = "B")

        val all = store.readAll()
        assertEquals(setOf("动物学/无脊椎动物", "动物学/脊椎动物"), all.keys)
        assertEquals(1, all["动物学/脊椎动物"]?.get(1)?.wrong)
    }

    @Test
    fun reset_removesRecordAndFile() {
        val root = newRoot()
        val store = PracticeStore(root)
        store.record("pkg", 7, correct = false, score = 0, userAnswer = "x")
        assertTrue(File(root, "pkg/practice.json").isFile)

        store.reset("pkg", 7)

        assertTrue(store.read("pkg").isEmpty())
        assertFalse("记录清空后不再留下空文件", File(root, "pkg/practice.json").exists())
    }

    @Test
    fun mergeLegacy_aggregatesOldAttempts() {
        val root = newRoot()
        val store = PracticeStore(root)
        store.mergeLegacy(
            mapOf(
                "旧题库" to listOf(
                    LegacyAttempt(5, correct = false, score = 0, userAnswer = "旧答案1", timestamp = 1000L),
                    LegacyAttempt(5, correct = true, score = 100, userAnswer = "旧答案2", timestamp = 2000L),
                    LegacyAttempt(6, correct = false, score = 30, userAnswer = "旧答案3", timestamp = 1500L)
                )
            )
        )

        val records = store.read("旧题库")
        assertEquals(2, records.size)
        val q5 = records.getValue(5)
        assertEquals(2, q5.seen)
        assertEquals(1, q5.right)
        assertEquals(1, q5.wrong)
        assertEquals("旧答案2", q5.lastAnswer)
        assertTrue(q5.lastCorrect)

        val q6 = records.getValue(6)
        assertEquals(1, q6.wrong)
        assertTrue(q6.isMistake)
    }

    @Test
    fun read_missingFile_returnsEmpty() {
        val store = PracticeStore(newRoot())
        assertTrue(store.read("不存在/的题库").isEmpty())
        assertNull(store.read("不存在/的题库")[1])
    }

    @Test
    fun writtenJson_usesReadableKeys() {
        val root = newRoot()
        val store = PracticeStore(root)
        store.record("pkg", 42, correct = true, score = 100, userAnswer = "答案")
        val text = File(root, "pkg/practice.json").readText()
        assertTrue(text.contains("\"42\""))
        assertTrue(text.contains("答案"))
    }

    @Test
    fun repeatedRecords_areCumulative() {
        val root = newRoot()
        val store = PracticeStore(root)
        repeat(3) { store.record("pkg", 1, correct = false, score = 0, userAnswer = "x") }
        val record: PracticeRecord = store.read("pkg").getValue(1)
        assertEquals(3, record.wrong)
        assertEquals(3, record.seen)
    }
}
