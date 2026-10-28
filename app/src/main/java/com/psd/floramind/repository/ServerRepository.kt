package com.psd.floramind.repository

import android.content.Context
import com.psd.floramind.data.DataStoreManager
import com.psd.floramind.model.network.*
import com.psd.floramind.network.ServerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class ServerRepository(
    private val context: Context,
    private val dataStore: DataStoreManager
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    @Volatile private var cachedApi: ServerApi? = null
    @Volatile private var cachedBaseUrl: String = ""

    private suspend fun getApi(): ServerApi {
        val raw = dataStore.serverUrlFlow.first()
        if (raw.isBlank()) {
            throw IllegalStateException("请先在设置中配置服务器地址")
        }
        val baseUrl = if (raw.endsWith("/")) raw else "$raw/"
        if (cachedApi != null && cachedBaseUrl == baseUrl) {
            return cachedApi!!
        }
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ServerApi::class.java)
        cachedBaseUrl = baseUrl
        cachedApi = api
        return api
    }

    suspend fun listPackages(): Result<List<ServerPackage>> = withContext(Dispatchers.IO) {
        try {
            Result.success(getApi().listPackages().packages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadPackage(
        zipFile: File,
        name: String,
        description: String,
        uploader: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val api = getApi()
            val part = MultipartBody.Part.createFormData(
                "file",
                zipFile.name,
                zipFile.asRequestBody("application/zip".toMediaTypeOrNull())
            )
            val textType = "text/plain".toMediaTypeOrNull()
            val resp = api.uploadPackage(
                file = part,
                name = name.toRequestBody(textType),
                description = description.toRequestBody(textType),
                uploader = uploader.toRequestBody(textType)
            )
            if (resp.success) {
                Result.success("上传成功，服务器识别到 ${resp.questionCount} 道题")
            } else {
                Result.failure(Exception(resp.message ?: "上传失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadPackage(pkg: ServerPackage): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val api = getApi()
                val resp = api.downloadPackage(pkg.id)
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("下载失败: HTTP ${resp.code()}")
                    )
                }
                val body = resp.body()
                    ?: return@withContext Result.failure(Exception("服务器返回为空"))
                val safeName = pkg.name.replace(
                    Regex("[^a-zA-Z0-9_\\u4e00-\\u9fa5]"), "_"
                )
                val outFile = File(
                    context.cacheDir,
                    "server_${pkg.id}_$safeName.zip"
                )
                outFile.outputStream().use { out ->
                    body.byteStream().use { it.copyTo(out) }
                }
                Result.success(outFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun submitFeedback(
        userName: String?,
        content: String,
        contact: String?,
        appVersion: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val api = getApi()
            val resp = api.submitFeedback(
                FeedbackRequest(userName, content, contact, appVersion)
            )
            if (resp.success) Result.success("反馈已提交，感谢！")
            else Result.failure(Exception(resp.message ?: "提交失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}