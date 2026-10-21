package com.psd.floramind.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.psd.floramind.database.AppDatabase
import com.psd.floramind.model.Question
import com.psd.floramind.model.StudyRecord
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

class QuestionRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val questionDao = db.questionDao()
    private val recordDao = db.studyRecordDao()

    private val packagesRootDir by lazy { File(context.filesDir, "packages") }

    // ---------- LIKE 转义 ----------
    private fun escapeLike(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    // ================== 内置题库 ==================
    private val builtinPrefs by lazy {
        context.getSharedPreferences("builtin_pkg_prefs", Context.MODE_PRIVATE)
    }

    /**
     * 只在首次安装 / App 版本升级时加载内置题库。
     * 之后所有操作都基于 filesDir/packages/，与外部导入无差别。
     */
    suspend fun loadBuiltInQuestionsIfEmpty() = withContext(Dispatchers.IO) {
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
            if (destDir.exists()) destDir.deleteRecursively()
            destDir.mkdirs()
            copyAssetTree(assetPath, destDir)

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

        val folders = subFolders.map { (name, count) ->
            FolderItem(name = name, path = if (folderPath.isEmpty()) name else "$folderPath/$name", packageCount = count)
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
                if (existing.isNotEmpty()) {
                    return@withContext Result.failure(Exception("目标文件夹已存在"))
                }

                db.withTransaction {
                    val oldPrefix = "$folderPath/"
                    val newPrefix = "$newPath/"
                    val packages = questionDao.getPackageNamesStartingWith("${escapeLike(folderPath)}/%")
                    packages.forEach { oldPkg ->
                        val newPkg = newPrefix + oldPkg.removePrefix(oldPrefix)
                        // 防止目标路径残留旧数据
                        questionDao.deleteByPackage(newPkg)
                        recordDao.deleteByPackage(newPkg)
                        questionDao.renameExactPackage(oldPkg, newPkg)
                        recordDao.renameExactPackage(oldPkg, newPkg)
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
                    recordDao.deleteByPackage(newPath)
                    questionDao.renameExactPackage(packagePath, newPath)
                    recordDao.renameExactPackage(packagePath, newPath)
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
                recordDao.deleteByPackage(pkg)
            }
        }
        File(packagesRootDir, folderPath).deleteRecursively()
    }

    /** 删除包（保持原有语义） */
    suspend fun deletePackage(packageName: String) {
        questionDao.deleteByPackage(packageName)
        recordDao.deleteByPackage(packageName)
        File(packagesRootDir, packageName).deleteRecursively()
    }

    /** 移动包 / 文件夹到目标文件夹（targetFolder 为 "" 表示根） */
    suspend fun moveItem(itemPath: String, targetFolder: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val name = itemPath.substringAfterLast('/')
                val newPath = if (targetFolder.isEmpty()) name else "$targetFolder/$name"
                if (newPath == itemPath) return@withContext Result.success(Unit)

                db.withTransaction {
                    val packages = questionDao.getPackageNamesStartingWith("${escapeLike(itemPath)}/%")
                    val isFolder = packages.isNotEmpty()
                    if (isFolder) {
                        val oldPrefix = "$itemPath/"
                        val newPrefix = "$newPath/"
                        packages.forEach { oldPkg ->
                            val newPkg = newPrefix + oldPkg.removePrefix(oldPrefix)
                            questionDao.deleteByPackage(newPkg)
                            recordDao.deleteByPackage(newPkg)
                            questionDao.renameExactPackage(oldPkg, newPkg)
                            recordDao.renameExactPackage(oldPkg, newPkg)
                        }
                    } else {
                        questionDao.deleteByPackage(newPath)
                        recordDao.deleteByPackage(newPath)
                        questionDao.renameExactPackage(itemPath, newPath)
                        recordDao.renameExactPackage(itemPath, newPath)
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

    // ================== 学习记录 ==================
    suspend fun saveRecord(record: StudyRecord) = recordDao.insert(record)
    suspend fun getMistakes(): List<StudyRecord> = recordDao.getMistakes()
    suspend fun getMistakesByPackage(pkg: String) = recordDao.getMistakesByPackage(pkg)
    suspend fun getMistakeCount(pkg: String) = recordDao.getMistakeCountByPackage(pkg)
    suspend fun deleteMistake(id: Long) = recordDao.deleteById(id)

    suspend fun getMistakePackages(): List<String> {
        val valid = questionDao.getAllPackageNames().toSet()
        return recordDao.getDistinctMistakePackages().filter { it in valid }
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