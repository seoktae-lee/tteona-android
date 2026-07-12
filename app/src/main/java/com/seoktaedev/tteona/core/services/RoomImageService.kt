package com.seoktaedev.tteona.core.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.seoktaedev.tteona.core.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 방 대표 이미지 업로드 — iOS Core/Services/RoomImageService.swift의 이식본.
 * 서버가 512 정사각 리샘플 후 rooms 문서의 imageUrl을 갱신하므로
 * myRooms 스냅샷 리스너를 통해 전 멤버 화면에 즉시 반영된다.
 */
object RoomImageService {

    /** 갤러리에서 고른 이미지를 축소 JPEG로 업로드하고 URL 반환 (실패 시 null) */
    suspend fun upload(context: Context, roomId: String, imageUri: Uri): String? =
        withContext(Dispatchers.IO) {
            val jpeg = ProfileImageService.downscaledJpeg(context, imageUri) ?: return@withContext null
            runCatching {
                val part = MultipartBody.Part.createFormData(
                    name = "image",
                    filename = "$roomId.jpg",
                    body = jpeg.toRequestBody("image/jpeg".toMediaType()),
                )
                ApiClient.api.uploadRoomImage(roomId, part).url
            }.onFailure { Log.w("RoomImageService", "upload 실패", it) }.getOrNull()
        }
}
