package com.example.alim.sticker

import java.time.Instant

data class Sticker(
	val id: String,
	val slug: String,
	val title: String,
	val createdAt: Instant,
	val updatedAt: Instant,
)
