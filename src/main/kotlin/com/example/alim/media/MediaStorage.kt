package com.example.alim.media

import java.io.InputStream
import java.nio.file.Path

data class StoredObject(
	val storageKey: String,
	val sizeBytes: Long,
)

data class MediaResource(
	val inputStream: InputStream,
	val contentType: String,
	val sizeBytes: Long,
	val originalFilename: String,
)

/** Local/remote-agnostic handle for range-capable playback. */
data class SeekableMedia(
	val path: Path,
	val contentType: String,
	val sizeBytes: Long,
	val originalFilename: String,
)

interface MediaStorage {
	fun store(
		id: String,
		filename: String,
		contentType: String,
		content: InputStream,
		sizeBytes: Long,
	): StoredObject

	fun load(storageKey: String, contentType: String, originalFilename: String): MediaResource?

	fun loadByFileId(fileId: String): MediaResource?

	fun resolve(storageKey: String, contentType: String, originalFilename: String): SeekableMedia?

	fun resolveByFileId(fileId: String): SeekableMedia?

	fun existsByFileId(fileId: String): Boolean

	fun delete(storageKey: String)

	fun deleteByFileId(fileId: String)
}
