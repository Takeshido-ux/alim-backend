package com.example.alim.media

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

@Service
class MediaService(
	private val mediaStorage: MediaStorage,
	private val mediaRepository: MediaRepository,
	@Value("\${media.public-base-url:http://localhost:8080}")
	private val publicBaseUrl: String,
) {
	fun upload(file: MultipartFile): MediaAsset {
		if (file.isEmpty) {
			throw InvalidMediaException("File is empty")
		}
		val originalFilename = file.originalFilename?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
			?: "file"
		val declaredContentType = file.contentType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
		val contentType = when {
			isAllowed(declaredContentType) -> declaredContentType
			else -> audioContentTypeFromFilename(originalFilename)
				?: throw InvalidMediaException("Only image, audio and video files are allowed")
		}
		val id = UUID.randomUUID().toString()

		val stored = file.inputStream.use { input ->
			mediaStorage.store(
				id = id,
				filename = originalFilename,
				contentType = contentType,
				content = input,
				sizeBytes = file.size,
			)
		}

		val asset = MediaAsset(
			id = id,
			originalFilename = originalFilename,
			contentType = contentType,
			sizeBytes = stored.sizeBytes,
			storageKey = stored.storageKey,
			createdAt = Instant.now(),
		)
		return mediaRepository.save(asset)
	}

	fun list(): List<MediaAsset> = mediaRepository.findAll()

	fun getById(id: String): MediaAsset =
		mediaRepository.findById(id) ?: throw MediaNotFoundException()

	fun resolve(id: String): SeekableMedia {
		val asset = mediaRepository.findById(id)
		if (asset != null) {
			return mediaStorage.resolve(asset.storageKey, asset.contentType, asset.originalFilename)
				?: mediaStorage.resolveByFileId(id)
				?: throw MediaNotFoundException()
		}
		return mediaStorage.resolveByFileId(id) ?: throw MediaNotFoundException()
	}

	fun delete(id: String) {
		val removed = mediaRepository.deleteById(id)
		val existedOnDisk = mediaStorage.existsByFileId(id)
		if (removed != null) {
			mediaStorage.delete(removed.storageKey)
		}
		mediaStorage.deleteByFileId(id)
		if (removed == null && !existedOnDisk) {
			throw MediaNotFoundException()
		}
	}

	fun publicUrl(asset: MediaAsset): String =
		"${normalizedPublicBaseUrl()}/api/media/${asset.id}"

	fun publicUrlById(id: String): String =
		"${normalizedPublicBaseUrl()}/api/media/$id"

	private fun normalizedPublicBaseUrl(): String {
		val base = publicBaseUrl.trim().trimEnd('/')
		if (base.startsWith("http://") || base.startsWith("https://")) {
			return base
		}
		return "https://$base"
	}

	private fun isAllowed(contentType: String): Boolean =
		contentType.startsWith("image/") ||
			contentType.startsWith("audio/") ||
			contentType == "application/ogg"

	private fun audioContentTypeFromFilename(filename: String): String? =
		when (filename.substringAfterLast('.', "").lowercase()) {
			"mp3" -> "audio/mpeg"
			"m4a" -> "audio/mp4"
			"aac" -> "audio/aac"
			"wav" -> "audio/wav"
			"ogg", "oga" -> "audio/ogg"
			"opus" -> "audio/opus"
			"flac" -> "audio/flac"
			else -> null
		}
}

class MediaNotFoundException : RuntimeException()

class InvalidMediaException(override val message: String) : RuntimeException(message)
