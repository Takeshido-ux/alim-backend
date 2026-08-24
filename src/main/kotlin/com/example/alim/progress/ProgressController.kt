package com.example.alim.progress

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class ProgressUpsertRequest(
	@field:Min(value = 0, message = "currentStepIndex must be >= 0")
	val currentStepIndex: Int,
	val completedStepIds: List<String> = emptyList(),
	@field:Min(value = 0, message = "attemptCount must be >= 0")
	val attemptCount: Int = 1,
	val firstTryPracticeCorrect: Boolean = true,
	@field:Min(value = 0, message = "incorrectPracticeRetries must be >= 0")
	val incorrectPracticeRetries: Int = 0,
	val clientUpdatedAt: String? = null,
)

data class StepResultRequest(
	val stepId: String,
	val skillId: String,
	val correct: Boolean,
	@field:Min(value = 1, message = "attempts must be >= 1")
	val attempts: Int = 1,
)

data class CompleteLessonRequest(
	val completedStepIds: List<String> = emptyList(),
	val attemptCount: Int? = null,
	val firstTryPracticeCorrect: Boolean? = null,
	val incorrectPracticeRetries: Int? = null,
	val stepResults: List<StepResultRequest> = emptyList(),
)

data class LessonProgressResponse(
	val childId: String,
	val lessonId: String,
	val status: String,
	val currentStepIndex: Int,
	val completedStepIds: List<String>,
	val attemptCount: Int,
	val firstTryPracticeCorrect: Boolean,
	val incorrectPracticeRetries: Int,
	val starsEarned: Int,
	val startedAt: String?,
	val completedAt: String?,
	val updatedAt: String,
	val contentVersionAtStart: String,
)

data class ProgressMapResponse(
	val items: List<LessonProgressResponse>,
)

data class CompleteLessonResponse(
	val progress: LessonProgressResponse,
	val newlyGrantedAchievements: List<GrantedAchievementResponse>,
	val totalStars: Int,
)

data class GrantedAchievementResponse(
	val id: String,
	val slug: String,
	val title: String,
)

@RestController
@RequestMapping("/api/children/{childId}")
class ProgressController(
	private val progressService: ProgressService,
) {
	@GetMapping("/path")
	fun path(@PathVariable childId: String): PathResult =
		progressService.getPath(childId)

	@GetMapping("/progress")
	fun progress(@PathVariable childId: String): ProgressMapResponse =
		ProgressMapResponse(items = progressService.getProgressMap(childId).map { it.toResponse() })

	@PutMapping("/lessons/{lessonId}/progress")
	fun upsert(
		@PathVariable childId: String,
		@PathVariable lessonId: String,
		@Valid @RequestBody request: ProgressUpsertRequest,
	): LessonProgressResponse =
		progressService.upsertProgress(
			childId = childId,
			lessonId = lessonId,
			input = ProgressUpsertInput(
				currentStepIndex = request.currentStepIndex,
				completedStepIds = request.completedStepIds,
				attemptCount = request.attemptCount,
				firstTryPracticeCorrect = request.firstTryPracticeCorrect,
				incorrectPracticeRetries = request.incorrectPracticeRetries,
				clientUpdatedAt = request.clientUpdatedAt?.let(Instant::parse),
			),
		).toResponse()

	@PostMapping("/lessons/{lessonId}/complete")
	fun complete(
		@PathVariable childId: String,
		@PathVariable lessonId: String,
		@RequestBody(required = false) request: CompleteLessonRequest?,
	): CompleteLessonResponse {
		val body = request ?: CompleteLessonRequest()
		val result = progressService.completeLesson(
			childId = childId,
			lessonId = lessonId,
			input = CompleteLessonInput(
				completedStepIds = body.completedStepIds,
				attemptCount = body.attemptCount,
				firstTryPracticeCorrect = body.firstTryPracticeCorrect,
				incorrectPracticeRetries = body.incorrectPracticeRetries,
				stepResults = body.stepResults.map {
					StepResult(it.stepId, it.skillId, it.correct, it.attempts)
				},
			),
		)
		return CompleteLessonResponse(
			progress = result.progress.toResponse(),
			newlyGrantedAchievements = result.newlyGrantedAchievements.map {
				GrantedAchievementResponse(it.id, it.slug, it.title)
			},
			totalStars = result.wallet.totalStars,
		)
	}

	@GetMapping("/rewards")
	fun rewards(@PathVariable childId: String): RewardsResult =
		progressService.getRewards(childId)

	@GetMapping("/review")
	fun review(@PathVariable childId: String): ReviewResult =
		progressService.getReview(childId)

	@PostMapping("/review/complete")
	fun completeReview(
		@PathVariable childId: String,
		@RequestBody results: List<StepResultRequest>,
	) {
		progressService.completeReview(
			childId,
			results.map { StepResult(it.stepId, it.skillId, it.correct, it.attempts) },
		)
	}

	@GetMapping("/parent-summary")
	fun parentSummary(@PathVariable childId: String): ParentSummaryResult =
		progressService.getParentSummary(childId)
}

private fun LessonProgress.toResponse() =
	LessonProgressResponse(
		childId = childId,
		lessonId = lessonId,
		status = status.name,
		currentStepIndex = currentStepIndex,
		completedStepIds = completedStepIds,
		attemptCount = attemptCount,
		firstTryPracticeCorrect = firstTryPracticeCorrect,
		incorrectPracticeRetries = incorrectPracticeRetries,
		starsEarned = starsEarned,
		startedAt = startedAt?.toString(),
		completedAt = completedAt?.toString(),
		updatedAt = updatedAt.toString(),
		contentVersionAtStart = contentVersionAtStart,
	)
