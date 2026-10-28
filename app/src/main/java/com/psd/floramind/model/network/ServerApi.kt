package com.psd.floramind.network

import com.psd.floramind.model.network.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ServerApi {
    @GET("api/packages")
    suspend fun listPackages(): ServerPackageListResponse

    @Multipart
    @POST("api/packages/upload")
    suspend fun uploadPackage(
        @Part file: MultipartBody.Part,
        @Part("name") name: RequestBody,
        @Part("description") description: RequestBody,
        @Part("uploader") uploader: RequestBody
    ): UploadResponse

    @Streaming
    @GET("api/packages/{id}/download")
    suspend fun downloadPackage(@Path("id") id: String): Response<ResponseBody>

    @POST("api/feedback")
    suspend fun submitFeedback(@Body request: FeedbackRequest): SimpleResponse
}