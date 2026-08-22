package com.example.alim.cartoon

import java.time.Instant

data class CartoonTag(
	val id: String,
	val title: String,
	val icon: String,
	val createdAt: Instant,
	val updatedAt: Instant,
)

data class CartoonEpisode(
	val id: String,
	val title: String,
	val description: String = "",
	val img: String = "",
	val video: String = "",
)

data class Cartoon(
	val id: String,
	val title: String,
	val description: String,
	val img: String,
	val video: String,
	val tagIds: List<String>,
	val episodes: List<CartoonEpisode>,
	val createdAt: Instant,
	val updatedAt: Instant,
)
