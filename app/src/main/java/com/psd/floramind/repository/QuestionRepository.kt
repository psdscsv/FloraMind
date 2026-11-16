package com.psd.floramind.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.psd.floramind.database.AppDatabase
import com.psd.floramind.model.PracticeRecord
import com.psd.floramind.model.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 文件夹（虚拟：由包路径前缀推导） */
data class FolderItem(
    val name: String,
    val path: String,        // 完整路径，如 "植物学/被子植物"
    val packageCount: Int
)

/** 包（实际存在的题库） */
data class PackageItem(
    val name: String,
    val fullPath: String,    // = packageName
    val questionCount: Int
)

/** 导入结果 */
data class ImportResult(
    val importedQuestionCount: Int = 0,
    val importedPackages: List<String> = emptyList(),
    val error: String? = null
)

/** 错题本首页：一个题库包 + 该包的错题数量 */
data class MistakePackage(
    val packageName: String,
    val count: Int
)

/** 错题本详情：一道错题及其做题记录 */
data class MistakeEntry(
    val question: Question,
    val record: PracticeRecord
)

class QuestionRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val questionDao = db.questionDao()
    private val recordDao = db.studyRecordDao()

    private val packagesRootDir by lazy { File(context.filesDir, "packages") }
    private val practiceStore by lazy { PracticeStore(packagesRootDir) }

    // ---------- LIKE 转义 ----------
    private fun escapeLike(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    // ================== 内置题库 ==================
    private val builtinPrefs by lazy {
        context.getSharedPreferences("builtin_pkg_prefs", Context.MODE_PRIVATE)
    }

    private val practicePrefs by lazy {
        context.getSharedPreferences("practice_store_prefs", Context.MODE_PRIVATE)
    }

    /**
     * 只在首次安装 / App 版本升级时加载内置题库。
     * 之后所有操作都基于 filesDir/packages/，与外部导入无差别。
     */
    suspend fun loadBuiltInQuestionsIfEmpty() = withContext(Dispatchers.IO) {
        // 旧版把做题记录存在 Room 里，这里一次性搬进各题库包的 practice.json
        migrateLegacyStudyRecordsIfNeeded()

        val currentVersion = context.packageManager
            .getPackageInfo(context.packageName, 0).versionCode
        val lastVersion = builtinPrefs.getInt("builtin_version", -1)
        if (lastVersion == currentVersion) return@withContext

        // 1) 旧单文件题库（可选）
        try {
            val qs = parseQuestionsFromStream(context.assets.open("question_bank.json"))
            questionDao.insertAll(qs.map { it.copy(packageName = "内置题库") })
        } catch (e: Exception) {
            Log.w("Repo", "no question_bank.json: ${e.message}")
        }

        // 2) 递归扫描 builtin_packages
        try {
            val children = context.assets.list("builtin_packages").orEmpty()
            if (children.isNotEmpty()) {
                scanAndImportAssetDir(assetPath = "builtin_packages", targetBaseFolder = "")
            }
        } catch (e: Exception) {
            Log.w("Repo", "scan builtin_packages failed: ${e.message}")
        }

        builtinPrefs.edit().putInt("builtin_version", currentVersion).apply()
    }

    /**
     * 递归扫描 assets 目录：
     * 一旦某个目录里有 questions.json，就视为一个题库：
     *   1. 整个目录复制到 filesDir/packages/<targetFolder>
     *   2. 解析插入数据库，packageName = 相对路径
     *   3. 不再深入它的子目录（子目录都是资源）
     */
    private suspend fun scanAndImportAssetDir(assetPath: String, targetBaseFolder: String) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) return

        // 当前目录是不是一个题库？
        if (children.any { it.equals("questions.json", ignoreCase = true) }) {
            val relative = assetPath.removePrefix("builtin_packages/")
            val pkgPath = when {
                relative.isEmpty() && targetBaseFolder.isEmpty() -> "内置题库"
                targetBaseFolder.isEmpty() -> relative
                relative.isEmpty() -> targetBaseFolder
                else -> "$targetBaseFolder/$relative"
            }

            val destDir = File(packagesRootDir, pkgPath)
            // 升级内置题库时保留做题记录（practice.json），避免版本更新把错题本清空
            val keptPractice = File(destDir, PracticeStore.FILE_NAME)
                .takeIf { destDir.exists() && it.isFile }
                ?.readText()
            if (destDir.exists()) destDir.deleteRecursively()
            destDir.mkdirs()
            copyAssetTree(assetPath, destDir)
            if (keptPractice != null) {
                File(destDir, PracticeStore.FILE_NAME).writeText(keptPractice)
            }

            val jsonFile = File(destDir, "questions.json")
            val questions: List<Question> = try {
                val type = object : TypeToken<List<Question>>() {}.type
                Gson().fromJson<List<Question>>(jsonFile.readText(), type)
            } catch (e: Exception) {
                Log.e("Repo", "parse ${jsonFile.path} failed", e)
                emptyList()
            }
            if (questions.isNotEmpty()) {
                questionDao.insertAll(questions.map { it.copy(packageName = pkgPath) })
                Log.d("Repo", "builtin pkg '$pkgPath' -> ${questions.size} questions")
            }
            return
        }

        // 否则递归
        children.forEach { child ->
            scanAndImportAssetDir("$assetPath/$child", targetBaseFolder)
        }
    }

    /** 把 assets 下整个目录树复制到 destDir */
    private fun copyAssetTree(assetPath: String, destDir: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            // 文件
            destDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destDir).use { input.copyTo(it) }
            }
            return
        }
        destDir.mkdirs()
        children.forEach { child ->
            copyAssetTree("$assetPath/$child", File(destDir, child))
        }
    }
    suspend fun reloadBuiltInPackages() = withContext(Dispatchers.IO) {
        builtinPrefs.edit().remove("builtin_version").apply()
        loadBuiltInQuestionsIfEmpty()
    }
    // ================== 文件夹树 ==================
    /**
     * 返回给定 folderPath 下的：
     *  - 直接子文件夹
     *  - 直接包
     * folderPath = "" 表示根目录。
     */
    suspend fun getFolderChildren(
        folderPath: String
    ): Pair<List<FolderItem>, List<PackageItem>> = withContext(Dispatchers.IO) {
        val all = questionDao.getAllPackageNames()
        val prefix = if (folderPath.isEmpty()) "" else "$folderPath/"

        val subFolders = mutableMapOf<String, Int>()    // 文件夹名 -> 包数量
        val directPackages = mutableListOf<String>()

        all.forEach { pkg ->
            if (prefix.isNotEmpty() && !pkg.startsWith(prefix)) return@forEach
            val remaining = if (prefix.isEmpty()) pkg else pkg.removePrefix(prefix)
            val slash = remaining.indexOf('/')
            if (slash < 0) {
                directPackages += pkg
            } else {
                val name = remaining.substring(0, slash)
                subFolders[name] = (subFolders[name] ?: 0) + 1
            }
        }

        // ★ 合并磁盘目录：数据库中还没有包的"空文件夹"也必须显示
        val diskDir = if (folderPath.isEmpty()) packagesRootDir
        else File(packagesRootDir, folderPath)
        val directPkgNames = directPackages.map { it.substringAfterLast('/') }.toHashSet()
        if (diskDir.exists() && diskDir.isDirectory) {
            diskDir.listFiles()?.forEach { f ->
                if (!f.isDirectory) return@forEach          // 跳过文件
                if (f.name in directPkgNames) return@forEach // 已是题库包，由 packages 展示
                if (f.name !in subFolders) subFolders[f.name] = 0
            }
        }

        val folders = subFolders.map { (name, count) ->
            FolderItem(
                name = name,
                path = if (folderPath.isEmpty()) name else "$folderPath/$name",
                packageCount = count
            )
        }.sortedBy { it.name }

        val packages = directPackages.map { pkg ->
            PackageItem(
                name = pkg.substringAfterLast('/'),
                fullPath = pkg,
                questionCount = questionDao.getQuestionCountByPackage(pkg)
            )
        }.sortedBy { it.name }

        folders to packages
    }

    // ================== 创建 / 重命名 / 删除 ==================

    /** 创建一个空文件夹（磁盘上创建即可，数据库无包则自然为空） */
    suspend fun createFolder(folderPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dir = File(packagesRootDir, folderPath)
            if (dir.exists()) Result.failure(Exception("文件夹已存在")) else {
                dir.mkdirs()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 重命名文件夹（只改最后一段）。
     * 该文件夹下所有包的 packageName 前缀一起被替换，磁盘目录同步移动。
     */
    suspend fun renameFolder(folderPath: String, newName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val parentPath = folderPath.substringBeforeLast('/', "")
                val newPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
                if (newPath == folderPath) return@withContext Result.success(Unit)

                // 冲突检测：目标前缀下已有包？
                val existing = questionDao.getPackageNamesStartingWith("${escapeLike(newPath)}/%")
                if (existing.isNotEmpty() || File(packagesRootDir, newPath).exists()) {
                    return@withContext Result.failure(Exception("目标文件夹已存在"))
                }

                db.withTransaction {
                    val oldPrefix = "$folderPath/"
                    val newPrefix = "$newPath/"
                    val packages = questionDao.getPackageNamesStartingWith("${escapeLike(folderPath)}/%")
                    packages.forEach { oldPkg ->
                        val newPkg = newPrefix + oldPkg.removePrefix(oldPrefix)
                        // 防止目标路径残留旧数据（做题记录随目录一起移动，不用改）
                        questionDao.deleteByPackage(newPkg)
                        questionDao.renameExactPackage(oldPkg, newPkg)
                    }
                }

                moveDirOnDisk(folderPath, newPath)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("QuestionRepo", "renameFolder", e)
                Result.failure(e)
            }
        }

    /** 重命名单个包（只改最后一段） */
    suspend fun renamePackage(packagePath: String, newName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val parentPath = packagePath.substringBeforeLast('/', "")
                val newPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
                if (newPath == packagePath) return@withContext Result.success(Unit)

                db.withTransaction {
                    questionDao.deleteByPackage(newPath)
                    questionDao.renameExactPackage(packagePath, newPath)
                }
                moveDirOnDisk(packagePath, newPath)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("QuestionRepo", "renamePackage", e)
                Result.failure(e)
            }
        }

    /** 删除文件夹（含所有子包） */
    suspend fun deleteFolder(folderPath: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val packages = questionDao.getPackageNamesStartingWith("${escapeLike(folderPath)}/%")
            packages.forEach { pkg ->
                questionDao.deleteByPackage(pkg)
            }
        }
        File(packagesRootDir, folderPath).deleteRecursively()
    }

    /** 删除包（保持原有语义，practice.json 随目录一起删除） */
    suspend fun deletePackage(packageName: String) {
        questionDao.deleteByPackage(packageName)
        File(packagesRootDir, packageName).deleteRecursively()
    }

    /** 移动包 / 文件夹到目标文件夹（targetFolder 为 "" 表示根） */
    suspend fun moveItem(itemPath: String, targetFolder: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val name = itemPath.substringAfterLast('/')
                val newPath = if (targetFolder.isEmpty()) name else "$targetFolder/$name"
                if (newPath == itemPath) return@withContext Result.success(Unit)

                if (File(packagesRootDir, newPath).exists()) {
                    return@withContext Result.failure(Exception("目标位置已存在同名项：$newPath"))
                }
                db.withTransaction {
                    val packages = questionDao.getPackageNamesStartingWith("${escapeLike(itemPath)}/%")
                    val isFolder = packages.isNotEmpty()
                    if (isFolder) {
                        val oldPrefix = "$itemPath/"
                        val newPrefix = "$newPath/"
                        packages.forEach { oldPkg ->
                            val newPkg = newPrefix + oldPkg.removePrefix(oldPrefix)
                            questionDao.deleteByPackage(newPkg)
                            questionDao.renameExactPackage(oldPkg, newPkg)
                        }
                    } else {
                        questionDao.deleteByPackage(newPath)
                        questionDao.renameExactPackage(itemPath, newPath)
                    }
                }
                moveDirOnDisk(itemPath, newPath)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun moveDirOnDisk(oldPath: String, newPath: String) {
        val src = File(packagesRootDir, oldPath)
        val dst = File(packagesRootDir, newPath)
        if (!src.exists()) return
        dst.parentFile?.mkdirs()
        if (dst.exists()) dst.deleteRecursively()
        if (!src.renameTo(dst)) {
            src.copyRecursively(dst, overwrite = true)
            src.deleteRecursively()
        }
    }

    // ================== 导出 / 导入 ==================

    /**
     * 将 folderPath 导出为 ZIP（folderPath="" 时导出整个题库根）。
     * 目录结构会原样写入 ZIP。
     */
    suspend fun exportFolderToZip(folderPath: String): File? = withContext(Dispatchers.IO) {
        val srcDir = if (folderPath.isEmpty()) packagesRootDir else File(packagesRootDir, folderPath)
        if (!srcDir.exists()) return@withContext null

        val zipName = if (folderPath.isEmpty()) "题库_全部.zip"
        else "${folderPath.substringAfterLast('/')}.zip"
        val zipFile = File(context.cacheDir, zipName)

        FileOutputStream(zipFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                srcDir.walkTopDown().forEach { file ->
                    val rel = file.relativeTo(srcDir).path
                    if (rel.isEmpty()) return@forEach
                    val entry = if (file.isDirectory) ZipEntry("$rel/") else ZipEntry(rel)
                    zos.putNextEntry(entry)
                    if (!file.isDirectory) FileInputStream(file).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        zipFile
    }

    /**
     * 导入 ZIP 到 targetFolder。
     * 会先解压到临时目录，再递归查找所有 questions.json，
     * 以每份 questions.json 所在目录（相对于 ZIP 根）作为包路径。
     * targetFolder 非空时，包路径统一加该前缀。
     */
    suspend fun importZipToFolder(uri: Uri, targetFolder: String): ImportResult =
        withContext(Dispatchers.IO) {
            val tempDir = File(context.cacheDir, "import_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            try {
                // 1. 解压
                context.contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val f = File(tempDir, entry.name)
                            if (entry.isDirectory) f.mkdirs() else {
                                f.parentFile?.mkdirs()
                                FileOutputStream(f).use { zip.copyTo(it) }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                } ?: return@withContext ImportResult(error = "无法读取 ZIP")

                // 2. 收集所有 questions.json
                val jsonFiles = mutableListOf<File>()
                tempDir.walkTopDown().forEach {
                    if (it.isFile && it.name.equals("questions.json", true)) jsonFiles += it
                }
                if (jsonFiles.isEmpty()) {
                    return@withContext ImportResult(error = "ZIP 内未找到 questions.json")
                }

                var total = 0
                val importedPaths = mutableListOf<String>()

                jsonFiles.forEach { json ->
                    val relDir = json.parentFile.relativeTo(tempDir).path
                    val pkgName = when {
                        targetFolder.isEmpty() && relDir.isEmpty() -> "未命名题库"
                        targetFolder.isEmpty() -> relDir
                        relDir.isEmpty() -> targetFolder
                        else -> "$targetFolder/$relDir"
                    }

                    val questions: List<Question> = try {
                        Gson().fromJson(
                            json.readText(),
                            object : TypeToken<List<Question>>() {}.type
                        )
                    } catch (e: Exception) {
                        Log.e("QuestionRepo", "parse ${json.path}", e)
                        return@forEach
                    }

                    val processed = questions.map { it.copy(packageName = pkgName) }
                    questionDao.insertAll(processed)
                    total += processed.size

                    // 移动实际资源文件到目标目录
                    val finalDir = File(packagesRootDir, pkgName)
                    finalDir.mkdirs()
                    json.parentFile.listFiles()?.forEach { child ->
                        val dest = File(finalDir, child.name)
                        if (dest.exists()) dest.deleteRecursively()
                        child.copyRecursively(dest, overwrite = true)
                    }
                    importedPaths += pkgName
                }

                ImportResult(total, importedPaths)
            } catch (e: Exception) {
                Log.e("QuestionRepo", "importZipToFolder", e)
                ImportResult(error = "导入失败: ${e.message}")
            } finally {
                tempDir.deleteRecursively()
            }
        }

    // ================== 查询 ==================
    suspend fun getAllPackages(): List<String> = questionDao.getAllPackageNames()
    suspend fun getQuestionCount(pkg: String): Int = questionDao.getQuestionCountByPackage(pkg)
    suspend fun getQuestionsByPackage(pkg: String): List<Question> = questionDao.getQuestionsByPackage(pkg)
    suspend fun getQuestionById(pkg: String, id: Int): Question? = questionDao.getById(pkg, id)
    suspend fun getAllQuestions(): List<Question> = questionDao.getAll()

    fun getImageFile(packageName: String, imageRelativePath: String): File? =
        File(File(packagesRootDir, packageName), imageRelativePath).takeIf { it.exists() }

    // ================== 做题记录（存放在题库包内） ==================

    /** 某个题库包的全部做题记录 */
    suspend fun getPracticeRecords(packageName: String): Map<Int, PracticeRecord> =
        withContext(Dispatchers.IO) { practiceStore.read(packageName) }

    /** 所有题库包的做题记录：包名 -> (题号 -> 记录) */
    suspend fun getAllPracticeRecords(): Map<String, Map<Int, PracticeRecord>> =
        withContext(Dispatchers.IO) { practiceStore.readAll() }

    /** 记录一次作答，返回更新后的记录 */
    suspend fun recordPractice(
        packageName: String,
        questionId: Int,
        correct: Boolean,
        score: Int,
        userAnswer: String
    ): PracticeRecord = withContext(Dispatchers.IO) {
        practiceStore.record(packageName, questionId, correct, score, userAnswer)
    }

    /** 重置某题的做题记录（也用于把题移出错题本） */
    suspend fun resetPractice(packageName: String, questionId: Int) =
        withContext(Dispatchers.IO) {
            practiceStore.reset(packageName, questionId)
        }

    /** 错题本首页数据 */
    suspend fun getMistakePackages(): List<MistakePackage> = withContext(Dispatchers.IO) {
        val valid = questionDao.getAllPackageNames().toSet()
        practiceStore.readAll()
            .mapNotNull { (pkg, records) ->
                if (pkg !in valid) return@mapNotNull null
                val count = records.values.count { it.isMistake }
                if (count == 0) null else MistakePackage(pkg, count)
            }
            .sortedWith(compareByDescending<MistakePackage> { it.count }.thenBy { it.packageName })
    }

    /** 某个题库包下的错题列表（按最近作答时间倒序） */
    suspend fun getMistakeEntries(packageName: String): List<MistakeEntry> =
        withContext(Dispatchers.IO) {
            val records = practiceStore.read(packageName).filterValues { it.isMistake }
            if (records.isEmpty()) return@withContext emptyList()
            val questions = questionDao.getQuestionsByPackage(packageName).associateBy { it.id }
            records.mapNotNull { (questionId, record) ->
                val question = questions[questionId] ?: return@mapNotNull null
                MistakeEntry(question, record)
            }.sortedByDescending { it.record.lastTime }
        }

    /**
     * 旧版本把做题记录存在 Room 的 study_records 表里。
     * 这里把它一次性搬进各题库包的 practice.json，搬完清空旧表。
     */
    private suspend fun migrateLegacyStudyRecordsIfNeeded() {
        if (practicePrefs.getBoolean("legacy_records_migrated", false)) return
        try {
            val legacy = recordDao.getAll()
            if (legacy.isNotEmpty()) {
                val grouped = legacy.groupBy { it.packageName }
                    .mapValues { (_, list) ->
                        list.map {
                            LegacyAttempt(
                                questionId = it.questionId,
                                correct = it.isCorrect,
                                score = it.score,
                                userAnswer = it.userAnswer,
                                timestamp = it.timestamp
                            )
                        }
                    }
                practiceStore.mergeLegacy(grouped)
                recordDao.deleteAll()
                Log.d("Repo", "已迁移 ${legacy.size} 条旧做题记录到 practice.json")
            }
            practicePrefs.edit().putBoolean("legacy_records_migrated", true).apply()
        } catch (e: Exception) {
            Log.e("Repo", "迁移旧做题记录失败", e)
        }
    }

    // 兼容旧调用
    suspend fun loadQuestionsFromAssets(packageName: String = "内置题库") {
        val questions = parseQuestionsFromStream(context.assets.open("question_bank.json"))
        questionDao.insertAll(questions.map { it.copy(packageName = packageName) })
    }
    private fun parseQuestionsFromStream(inputStream: InputStream): List<Question> {
        val type = object : TypeToken<List<Question>>() {}.type
        return InputStreamReader(inputStream).use { reader ->
            Gson().fromJson<List<Question>>(reader, type) ?: emptyList()
        }
    }
}