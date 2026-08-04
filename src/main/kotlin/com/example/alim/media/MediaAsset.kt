package com.example.alim.media

import java.time.Instant

data class MediaAsset(
	val id: String,
	val originalFilename: String,
	val contentType: String,
	val sizeBytes: Long,
	val storageKey: String,
	val createdAt: Instant,
)
