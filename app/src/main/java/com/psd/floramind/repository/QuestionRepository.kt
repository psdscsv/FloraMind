package com.psd.floramind.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.psd.floramind.database.AppDatabase
import com.psd.floramind.model.Question
import com.psd.floramind.model.StudyRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class QuestionRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val questionDao = db.questionDao()
    private val recordDao = db.studyRecordDao()

    // 私有存储根目录
    private val packagesRootDir by lazy { File(context.filesDir, "packages") }

    // ----- 初始化：加载内置题库（若数据库为空） -----
    suspend fun loadBuiltInQuestionsIfEmpty() {
        val count = questionDao.getAll().size
        Log.d("Repo", "Current question count: $count")
        if (count == 0) {
            Log.d("Repo", "Loading built-in questions from assets")
            loadQuestionsFromAssets("内置题库")
            val newCount = questionDao.getAll().size
            Log.d("Repo", "After loading: $newCount questions")
        }
    }

    // 从 assets 加载指定包名（内置）
    suspend fun loadQuestionsFromAssets(packageName: String = "内置题库") {
        val inputStream = context.assets.open("question_bank.json")
        val questions = parseQuestionsFromStream(inputStream)
        val processed = questions.map { it.copy(packageName = packageName) }
        questionDao.insertAll(processed)
    }

    // ----- 外部导入：从 ZIP 压缩包导入 -----
    suspend fun importPackageFromZip(uri: Uri, packageName: String): Int {
        return withContext(Dispatchers.IO) {
            val targetDir = File(packagesRootDir, packageName)
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        val entryFile = File(targetDir, entry.name)
                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()
                            FileOutputStream(entryFile).use { out ->
                                zipStream.copyTo(out)
                            }
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                }
            } ?: return@withContext 0

            // 解析 questions.json
            val jsonFile = File(targetDir, "questions.json")
            if (!jsonFile.exists()) return@withContext 0
            val jsonString = jsonFile.readText()
            val questions = parseQuestionsFromJson(jsonString)
            val processed = questions.map { it.copy(packageName = packageName) }
            questionDao.insertAll(processed)
            processed.size
        }
    }

    // ----- 导出：将指定包打包为 ZIP -----
    suspend fun exportPackageToZip(packageName: String): File? {
        return withContext(Dispatchers.IO) {
            val srcDir = File(packagesRootDir, packageName)
            if (!srcDir.exists()) return@withContext null

            val zipFile = File(context.cacheDir, "$packageName.zip")
            FileOutputStream(zipFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    srcDir.walkTopDown().forEach { file ->
                        val relativePath = file.relativeTo(srcDir).path
                        val entry = if (file.isDirectory) ZipEntry("$relativePath/") else ZipEntry(relativePath)
                        zos.putNextEntry(entry)
                        if (!file.isDirectory) {
                            FileInputStream(file).use { it.copyTo(zos) }
                        }
                        zos.closeEntry()
                    }
                }
            }
            zipFile
        }
    }

    // 获取所有题库包名（从数据库查）
    suspend fun getAllPackages(): List<String> = questionDao.getAllPackageNames()

    // 获取某包下题目数量
    suspend fun getQuestionCount(packageName: String): Int = questionDao.getQuestionCountByPackage(packageName)

    // 获取某包所有题目
    suspend fun getQuestionsByPackage(packageName: String): List<Question> = questionDao.getQuestionsByPackage(packageName)

    // 根据复合主键获取题目
    suspend fun getQuestionById(packageName: String, id: Int): Question? = questionDao.getById(packageName, id)

    // 通用获取所有（可能不需要，但保留）
    suspend fun getAllQuestions(): List<Question> = questionDao.getAll()

    // 获取题目的图片文件（用于加载）
    fun getImageFile(packageName: String, imageRelativePath: String): File? {
        val baseDir = File(packagesRootDir, packageName)
        return File(baseDir, imageRelativePath).takeIf { it.exists() }
    }

    // ----- 记录 -----
    suspend fun saveRecord(record: StudyRecord) {
        recordDao.insert(record)
    }

    suspend fun getMistakes(): List<StudyRecord> = recordDao.getMistakes()

    fun getRecordsForQuestion(packageName: String, questionId: Int): Flow<List<StudyRecord>> = flow {
        // 需要修改 StudyRecordDao 增加按包和题号查询，这里暂略，实际可添加
        // 但当前项目未使用，可保留
        emit(emptyList())
    }

    // ----- 辅助解析 -----
    private fun parseQuestionsFromStream(inputStream: InputStream): List<Question> {
        val reader = InputStreamReader(inputStream)
        val type = object : TypeToken<List<Question>>() {}.type
        return Gson().fromJson(reader, type)
    }

    private fun parseQuestionsFromJson(json: String): List<Question> {
        val type = object : TypeToken<List<Question>>() {}.type
        return Gson().fromJson(json, type)
    }
}