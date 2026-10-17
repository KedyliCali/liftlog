package com.behlul.liftlog.data.repository

import android.content.Context
import com.behlul.liftlog.data.remote.LiftLogApi
import com.behlul.liftlog.data.remote.dto.ProfileDto
import com.behlul.liftlog.data.remote.dto.UpdateProfileRequestDto
import com.behlul.liftlog.domain.model.Profile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface ProfileRepository {
    suspend fun getProfile(): Result<Profile>
    suspend fun updateProfile(displayName: String?, heightCm: Double?, bodyWeightKg: Double?): Result<Profile>
    suspend fun uploadAvatar(jpeg: ByteArray): Result<Profile>
    suspend fun deleteAvatar(): Result<Unit>

    /** JPEG bytes of the photo for [version]; downloaded once, then served from the on-disk cache. */
    suspend fun getAvatar(version: Long): Result<ByteArray>
}

@Singleton
class RemoteProfileRepository @Inject constructor(
    private val api: LiftLogApi,
    private val json: Json,
    @ApplicationContext private val context: Context,
) : ProfileRepository {

    private val cacheDir get() = File(context.cacheDir, "avatars").apply { mkdirs() }

    override suspend fun getProfile() = apiCall(json) { api.getProfile().toDomain() }

    override suspend fun updateProfile(displayName: String?, heightCm: Double?, bodyWeightKg: Double?) = apiCall(json) {
        api.updateProfile(UpdateProfileRequestDto(displayName, heightCm, bodyWeightKg)).toDomain()
    }

    override suspend fun uploadAvatar(jpeg: ByteArray) = apiCall(json) {
        val profile = api.uploadAvatar(jpeg.toRequestBody("image/jpeg".toMediaType())).toDomain()
        // We already have the bytes, so seed the cache instead of downloading them right back
        profile.avatarVersion?.let { writeCache(it, jpeg) }
        profile
    }

    override suspend fun deleteAvatar() = apiCall(json) {
        api.deleteAvatar()
        clearCache()
    }

    override suspend fun getAvatar(version: Long): Result<ByteArray> {
        val cached = File(cacheDir, "$version.jpg")
        if (cached.exists()) return Result.success(withContext(Dispatchers.IO) { cached.readBytes() })
        return apiCall(json) {
            val bytes = api.getAvatar().use { it.bytes() }
            writeCache(version, bytes)
            bytes
        }
    }

    private suspend fun writeCache(version: Long, bytes: ByteArray) = withContext(Dispatchers.IO) {
        clearCache() // only the current photo is ever needed
        File(cacheDir, "$version.jpg").writeBytes(bytes)
    }

    private suspend fun clearCache() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { it.delete() }
        Unit
    }
}

private fun ProfileDto.toDomain() = Profile(displayName, heightCm, bodyWeightKg, avatarVersion)
