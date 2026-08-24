package com.example.alim.lesson

import java.time.Instant

data class LessonStep(
	val stepId: String,
	val type: String,
	val payload: Map<String, Any?> = emptyMap(),
	val assets: List<String> = emptyList(),
)

data class Lesson(
	val id: String,
	val slug: String,
	val trackId: String,
	val orderInTrack: Int,
	val title: String,
	val description: String,
	val backgroundImg: String,
	val parentNote: String,
	val ageBand: String = "all",
	val contentVersion: String,
	val steps: List<LessonStep>,
	val createdAt: Instant,
	val updatedAt: Instant,
)
