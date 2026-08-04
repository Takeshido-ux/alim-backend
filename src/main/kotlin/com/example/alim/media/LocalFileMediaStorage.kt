package com.example.alim.media

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

@Component
class LocalFileMediaStorage(
	@Value("\${media.storage.root-dir:./data/media}") rootDir: String,
) : MediaStorage {
	private val rootPath: Path = Path.of(rootDir).toAbsolutePath().normalize()

	init {
		Files.createDirectories(rootPath)
	}

	override fun store(
		id: String,
		filename: String,
		contentType: String,
		content: InputStream,
		sizeBytes: Long,
	): StoredObject {
		val extension = filename.substringAfterLast('.', "").lowercase()
		val storageKey = if (extension.isBlank()) id else "$id.$extension"
		val target = resolveInsideRoot(storageKey)

		content.use { input ->
			Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
		}

		return StoredObject(
			storageKey = storageKey,
			sizeBytes = Files.size(target),
		)
	}

	override fun load(
		storageKey: String,
		contentType: String,
		originalFilename: String,
	): MediaResource? =
		resolve(storageKey, contentType, originalFilename)?.toResource()

	override fun loadByFileId(fileId: String): MediaResource? =
		resolveByFileId(fileId)?.toResource()

	override fun resolve(
		storageKey: String,
		contentType: String,
		originalFilename: String,
	): SeekableMedia? {
		val target = resolveInsideRoot(storageKey)
		if (!Files.exists(target) || !Files.isRegularFile(target)) {
			return null
		}
		return SeekableMedia(
			path = target,
			contentType = contentType,
			sizeBytes = Files.size(target),
			originalFilename = originalFilename,
		)
	}

	override fun resolveByFileId(fileId: String): SeekableMedia? {
		val file = findFileById(fileId) ?: return null
		val extension = file.extension.lowercase()
		return SeekableMedia(
			path = file,
			contentType = guessContentType(extension),
			sizeBytes = Files.size(file),
			originalFilename = file.name,
		)
	}

	override fun existsByFileId(fileId: String): Boolean = findFileById(fileId) != null

	override fun delete(storageKey: String) {
		val target = resolveInsideRoot(storageKey)
		Files.deleteIfExists(target)
	}

	override fun deleteByFileId(fileId: String) {
		findFileById(fileId)?.let(Files::deleteIfExists)
	}

	private fun SeekableMedia.toResource(): MediaResource =
		MediaResource(
			inputStream = Files.newInputStream(path),
			contentType = contentType,
			sizeBytes = sizeBytes,
			originalFilename = originalFilename,
		)

	private fun findFileById(fileId: String): Path? {
		val exact = resolveInsideRoot(fileId)
		if (Files.exists(exact) && Files.isRegularFile(exact)) {
			return exact
		}
		if (!Files.exists(rootPath)) {
			return null
		}
		return Files.list(rootPath).use { stream ->
			stream
				.filter { it.isRegularFile() && it.nameWithoutExtension == fileId }
				.findFirst()
				.orElse(null)
		}
	}

	private fun resolveInsideRoot(name: String): Path {
		val target = rootPath.resolve(name).normalize()
		require(target.startsWith(rootPath)) { "Invalid storage path" }
		return target
	}

	private fun guessContentType(extension: String): String =
		when (extension) {
			"jpg", "jpeg" -> "image/jpeg"
			"png" -> "image/png"
			"gif" -> "image/gif"
			"webp" -> "image/webp"
			"mp4" -> "video/mp4"
			"mov" -> "video/quicktime"
			"webm" -> "video/webm"
			"mkv" -> "video/x-matroska"
			else -> "application/octet-stream"
		}
}
