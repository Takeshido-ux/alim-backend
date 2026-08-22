package com.example.alim.sticker

import java.time.Instant

data class Sticker(
	val id: String,
	val slug: String,
	val title: String,
	val description: String,
	val icon: String,
	val rule: AchievementRule,
	val active: Boolean,
	val order: Int,
	val createdAt: Instant,
	val updatedAt: Instant,
)

data class AchievementRule(
	val metric: AchievementMetric,
	val target: Int,
	val scopeType: AchievementScopeType,
	val scopeId: String? = null,
)

enum class AchievementMetric {
	LESSONS_COMPLETED,
	TOTAL_STARS,
	TRACKS_COMPLETED,
	SPECIFIC_LESSON_COMPLETED,
}

enum class AchievementScopeType {
	GLOBAL,
	TRACK,
	LESSON,
}
