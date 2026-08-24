package com.example.alim.progress

import java.time.Instant

enum class LessonProgressStatus {
	not_started,
	in_progress,
	completed,
}

data class LessonProgress(
	val childId: String,
	val lessonId: String,
	val status: LessonProgressStatus,
	val currentStepIndex: Int = 0,
	val completedStepIds: List<String> = emptyList(),
	val attemptCount: Int = 0,
	val firstTryPracticeCorrect: Boolean = true,
	val incorrectPracticeRetries: Int = 0,
	val starsEarned: Int = 0,
	val startedAt: Instant? = null,
	val completedAt: Instant? = null,
	val updatedAt: Instant,
	val contentVersionAtStart: String,
)

data class RewardWallet(
	val childId: String,
	val totalStars: Int = 0,
	val achievementIds: Set<String> = emptySet(),
	val achievementUnlockedAt: Map<String, Instant> = emptyMap(),
	val lastGrantedAchievementId: String? = null,
)

enum class SkillMasteryState {
	introduced,
	practicing,
	mastered,
	review_due,
}

data class SkillProgress(
	val childId: String,
	val objectiveId: String,
	val objectiveTitle: String,
	val state: SkillMasteryState,
	val successfulAttempts: Int,
	val totalAttempts: Int,
	val lastPracticedAt: Instant,
)
