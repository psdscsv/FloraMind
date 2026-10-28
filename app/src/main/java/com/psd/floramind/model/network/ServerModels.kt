package com.psd.floramind.model.network

import com.google.gson.annotations.SerializedName

data class ServerPackage(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerializedName("question_count") val questionCount: Int = 0,
    @SerializedName("file_size") val fileSize: Long = 0,
    val uploader: String? = null,
    @SerializedName("upload_time") val uploadTime: Long = 0,
    @SerializedName("download_count") val downloadCount: Int = 0
)

data class ServerPackageListResponse(
    val packages: List<ServerPackage> = emptyList()
)

data class UploadResponse(
    val success: Boolean = false,
    val id: String? = null,
    val message: String? = null,
    @SerializedName("questionCount") val questionCount: Int = 0
)

data class FeedbackRequest(
    val userName: String? = null,
    val content: String,
    val contact: String? = null,
    val appVersion: String = ""
)

data class SimpleResponse(
    val success: Boolean = false,
    val message: String? = null,
    val id: String? = null
)

data class Announcement(
    val id: String,
    val title: String,
    val content: String,
    @SerializedName("updated_time") val updatedTime: Long = 0
)

data class AnnouncementResponse(
    val announcement: Announcement? = null
)