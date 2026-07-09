package com.seoktaedev.tteona.core.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.seoktaedev.tteona.core.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

/**
 * iOS Core/Services/ProfileImageService.swift의 Kotlin 이식본.
 * WAS 업로드 라우트가 512px 리샘플 + Firestore profileImageUrl 갱신까지 처리한다.
 */
object ProfileImageService {

    /** 갤러리에서 고른 이미지를 축소 JPEG로 업로드하고 URL 반환 (실패 시 null) */
    suspend fun upload(context: Context, uid: String, imageUri: Uri): String? =
        withContext(Dispatchers.IO) {
            val jpeg = downscaledJpeg(context, imageUri) ?: return@withContext null
            runCatching {
                val part = MultipartBody.Part.createFormData(
                    name = "image",
                    filename = "$uid.jpg",
                    body = jpeg.toRequestBody("image/jpeg".toMediaType()),
                )
                ApiClient.api.uploadAvatar(uid, part).url
            }.onFailure { Log.w("ProfileImageService", "upload 실패", it) }.getOrNull()
        }

    // iOS ImageUploadHelper.downscaledJPEG 대응 — 원본 대신 축소본 전송
    // (서버가 어차피 리샘플하고, 업로드 용량 한도도 안전). 코스 썸네일 업로드와 공유.
    internal fun downscaledJpeg(context: Context, uri: Uri, maxDim: Int = 1024): ByteArray? {
        return try {
            // 1차: 경계만 읽어 샘플링 비율 결정 (대용량 원본의 OOM 방지)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= maxDim || bounds.outHeight / (sampleSize * 2) >= maxDim) {
                sampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val sampled = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            // 2차: 정확히 maxDim 이하로 스케일
            val scale = minOf(1f, maxDim.toFloat() / maxOf(sampled.width, sampled.height))
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    sampled,
                    (sampled.width * scale).toInt(),
                    (sampled.height * scale).toInt(),
                    true,
                )
            } else sampled

            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            Log.w("ProfileImageService", "이미지 축소 실패", e)
            null
        }
    }
}
