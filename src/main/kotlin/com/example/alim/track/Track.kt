package com.example.alim.track

import java.time.Instant

data class Track(
	val id: String,
	val slug: String,
	val order: Int,
	val title: String,
	val description: String,
	val iconColor: String,
	val backgroundImg: String,
	/** orderInTrack -> sticker slug granted when that lesson is completed */
	val stickerMilestones: Map<Int, String> = emptyMap(),
	val createdAt: Instant,
	val updatedAt: Instant,
)
