package com.psd.floramind.repository

import android.util.Log
import com.google.gson.GsonBuilder
import com.psd.floramind.model.PracticeBook
import com.psd.floramind.model.PracticeRecord
import java.io.File

/** 旧版数据库里的一条做题记录，仅用于一次性迁移到 practice.json */
data class LegacyAttempt(
    val questionId: Int,
    val correct: Boolean,
    val score: Int,
    val userAnswer: String,
    val timestamp: Long
)

/**
 * 做题记录的文件存储。
 *
 * 每条记录都写在题库包目录下的 `practice.json` 里，例如：
 *   files/packages/动物学/无脊椎动物/practice.json
 *
 * 这样：
 *  - exportFolderToZip 打包整个目录时自动带上做题信息；
 *  - importZipToFolder 解压时自动恢复做题信息；
 *  - rename / move 目录时不用改任何记录表。
 */
class PracticeStore(private val packagesRootDir: File) {

    companion object {
        const val FILE_NAME = "practice.json"
        private const val FORMAT_VERSION = 1
        private const val TAG = "PracticeStore"
    }

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()
    private val lock = Any()

    fun fileOf(packageName: String): File =
        File(File(packagesRootDir, packageName), FILE_NAME)

    /** 读取某个题库包的做题记录 */
    fun read(packageName: String): Map<Int, PracticeRecord> {
        val file = fileOf(packageName)
        if (!file.isFile) return emptyMap()
        synchronized(lock) { return parse(file) }
    }

    /** 扫描所有题库包，返回 包名 -> (题号 -> 记录) */
    fun readAll(): Map<String, Map<Int, PracticeRecord>> {
        val result = mutableMapOf<String, Map<Int, PracticeRecord>>()
        if (!packagesRootDir.isDirectory) return result
        synchronized(lock) {
            packagesRootDir.walkTopDown()
                .filter { it.isFile && it.name.equals(FILE_NAME, ignoreCase = true) }
                .forEach { file ->
                    val dir = file.parentFile ?: return@forEach
                    if (dir == packagesRootDir) return@forEach
                    val pkg = dir.relativeTo(packagesRootDir).invariantSeparatorsPath
                    if (pkg.isEmpty()) return@forEach
                    val records = parse(file)
                    if (records.isNotEmpty()) result[pkg] = records
                }
        }
        return result
    }

    /** 记录一次作答，返回更新后的记录 */
    fun record(
        packageName: String,
        questionId: Int,
        correct: Boolean,
        score: Int,
        userAnswer: String
    ): PracticeRecord = synchronized(lock) {
        val records = read(packageName).toMutableMap()
        val old = records[questionId] ?: PracticeRecord()
        val updated = old.copy(
            seen = old.seen + 1,
            right = old.right + if (correct) 1 else 0,
            wrong = old.wrong + if (correct) 0 else 1,
            lastAnswer = userAnswer,
            lastScore = score,
            lastCorrect = correct,
            lastTime = System.currentTimeMillis()
        )
        records[questionId] = updated
        write(packageName, records)
        updated
    }

    /** 重置某题的记录（错题本里的「移出错题本」也是这个操作） */
    fun reset(packageName: String, questionId: Int) {
        synchronized(lock) {
            val records = read(packageName).toMutableMap()
            if (records.remove(questionId) == null) return
            write(packageName, records)
        }
    }

    /** 把旧数据库里的记录合并进 practice.json，并清空旧表由调用方负责 */
    fun mergeLegacy(legacy: Map<String, List<LegacyAttempt>>) {
        synchronized(lock) {
            legacy.forEach { (pkg, attempts) ->
                if (attempts.isEmpty()) return@forEach
                val records = read(pkg).toMutableMap()
                attempts.sortedBy { it.timestamp }.forEach { attempt ->
                    val old = records[attempt.questionId] ?: PracticeRecord()
                    records[attempt.questionId] = old.copy(
                        seen = old.seen + 1,
                        right = old.right + if (attempt.correct) 1 else 0,
                        wrong = old.wrong + if (attempt.correct) 0 else 1,
                        lastAnswer = attempt.userAnswer,
                        lastScore = attempt.score,
                        lastCorrect = attempt.correct,
                        lastTime = attempt.timestamp
                    )
                }
                write(pkg, records)
            }
        }
    }

    private fun parse(file: File): Map<Int, PracticeRecord> = try {
        val book = gson.fromJson(file.readText(), PracticeBook::class.java)
        book?.records
            ?.mapValues { (_, record) -> record.sanitized() }
            ?: emptyMap()
    } catch (e: Exception) {
        Log.w(TAG, "解析 ${file.path} 失败", e)
        emptyMap()
    }

    private fun write(packageName: String, records: Map<Int, PracticeRecord>) {
        val file = fileOf(packageName)
        if (records.isEmpty()) {
            if (file.exists()) file.delete()
            return
        }
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(
                gson.toJson(PracticeBook(FORMAT_VERSION, System.currentTimeMillis(), records))
            )
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入 ${file.path} 失败", e)
        }
    }
}

private fun PracticeRecord.sanitized(): PracticeRecord = copy(
    seen = seen.coerceAtLeast(0),
    right = right.coerceAtLeast(0),
    wrong = wrong.coerceAtLeast(0),
    lastScore = lastScore.coerceIn(0, 100)
)
