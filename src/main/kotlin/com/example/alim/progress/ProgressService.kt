package com.example.alim.progress

import com.example.alim.child.ChildService
import com.example.alim.lesson.Lesson
import com.example.alim.lesson.LessonService
import com.example.alim.parent.CurrentParentResolver
import com.example.alim.sticker.AchievementMetric
import com.example.alim.sticker.AchievementScopeType
import com.example.alim.sticker.Sticker
import com.example.alim.sticker.StickerService
import com.example.alim.track.Track
import com.example.alim.track.TrackService
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Service
class ProgressService(
	private val progressRepository: ProgressRepository,
	private val childService: ChildService,
	private val lessonService: LessonService,
	private val trackService: TrackService,
	private val stickerService: StickerService,
	private val currentParentResolver: CurrentParentResolver,
) {
	fun getPath(childId: String): PathResult {
		val child = childService.requireOwnedChildForCurrentParent(childId)
		val parent = currentParentResolver.requireParent()
		val tracks = trackService.list()
		val lessons = lessonService.list().filter { it.matchesAge(child.age) }
		val progressByLesson = progressRepository.findByChildId(childId).associateBy { it.lessonId }
		val wallet = progressRepository.findWallet(childId) ?: RewardWallet(childId = childId)

		val trackStates = buildTrackStates(tracks, lessons, progressByLesson)
		val continueLesson = findContinueLesson(trackStates, lessons, progressByLesson)
		val completedToday = progressByLesson.values.count { progress ->
			progress.status == LessonProgressStatus.completed &&
				progress.completedAt?.atZone(ZoneOffset.UTC)?.toLocalDate() == LocalDate.now(ZoneOffset.UTC)
		}
		val reviewAvailable = progressRepository.findSkillsByChildId(childId).isNotEmpty() ||
			progressByLesson.values.any { it.status == LessonProgressStatus.completed }

		return PathResult(
			activeChild = PathChild(
				id = child.id,
				name = child.name,
				avatarId = child.avatarId,
			),
			continueLesson = continueLesson,
			tracks = trackStates,
			softProgress = SoftProgress(
				completedToday = completedToday,
				dailyGoal = parent.preferences.dailyLessonGoal,
			),
			reviewAvailable = reviewAvailable,
			recentAchievement = wallet.lastGrantedAchievementId?.let { achievementId ->
				stickerService.list().find { it.id == achievementId }?.let {
					RecentAchievement(achievementId = it.id, slug = it.slug, title = it.title)
				}
			},
		)
	}

	fun getProgressMap(childId: String): List<LessonProgress> {
		childService.requireOwnedChildForCurrentParent(childId)
		return progressRepository.findByChildId(childId)
	}

	fun upsertProgress(childId: String, lessonId: String, input: ProgressUpsertInput): LessonProgress {
		childService.requireOwnedChildForCurrentParent(childId)
		val lesson = lessonService.getById(lessonId)
		ensureLessonUnlocked(childId, lesson)

		val existing = progressRepository.find(childId, lessonId)
		if (existing?.status == LessonProgressStatus.completed) {
			return existing
		}

		val now = Instant.now()
		if (existing != null && input.clientUpdatedAt != null && input.clientUpdatedAt.isBefore(existing.updatedAt)) {
			return existing
		}

		val contentVersionAtStart =
			if (existing == null || existing.contentVersionAtStart != lesson.contentVersion) {
				lesson.contentVersion
			} else {
				existing.contentVersionAtStart
			}

		val restarted = existing != null && existing.contentVersionAtStart != lesson.contentVersion
		val stepIndex = if (restarted) 0 else input.currentStepIndex.coerceIn(0, lesson.steps.lastIndex.coerceAtLeast(0))
		val completedStepIds = if (restarted) emptyList() else input.completedStepIds.distinct()

		val progress = LessonProgress(
			childId = childId,
			lessonId = lessonId,
			status = LessonProgressStatus.in_progress,
			currentStepIndex = stepIndex,
			completedStepIds = completedStepIds,
			attemptCount = maxOf(existing?.attemptCount ?: 0, input.attemptCount, 1),
			firstTryPracticeCorrect = input.firstTryPracticeCorrect,
			incorrectPracticeRetries = maxOf(
				existing?.incorrectPracticeRetries ?: 0,
				input.incorrectPracticeRetries,
			),
			starsEarned = 0,
			startedAt = existing?.startedAt ?: now,
			completedAt = null,
			updatedAt = now,
			contentVersionAtStart = contentVersionAtStart,
		)
		return progressRepository.save(progress)
	}

	fun completeLesson(childId: String, lessonId: String, input: CompleteLessonInput): CompleteLessonResult {
		childService.requireOwnedChildForCurrentParent(childId)
		val lesson = lessonService.getById(lessonId)
		ensureLessonUnlocked(childId, lesson)

		val existing = progressRepository.find(childId, lessonId)
		if (existing?.status == LessonProgressStatus.completed) {
			val wallet = progressRepository.findWallet(childId) ?: RewardWallet(childId = childId)
			val reconciliation = reconcileAchievements(childId, wallet)
			return CompleteLessonResult(
				progress = existing,
				newlyGrantedAchievements = reconciliation.granted,
				wallet = reconciliation.wallet,
			)
		}

		val now = Instant.now()
		val completedStepIds = (input.completedStepIds.ifEmpty { existing?.completedStepIds.orEmpty() })
			.distinct()
		val allStepsDone = lesson.steps.all { it.stepId in completedStepIds } ||
			completedStepIds.size >= lesson.steps.size
		val firstTry = input.firstTryPracticeCorrect ?: existing?.firstTryPracticeCorrect ?: true
		val stars = when {
			allStepsDone && firstTry -> 60
			allStepsDone -> 40
			else -> 20
		}.coerceIn(20, 60)

		val progress = LessonProgress(
			childId = childId,
			lessonId = lessonId,
			status = LessonProgressStatus.completed,
			currentStepIndex = lesson.steps.lastIndex.coerceAtLeast(0),
			completedStepIds = if (completedStepIds.isEmpty()) lesson.steps.map { it.stepId } else completedStepIds,
			attemptCount = maxOf(existing?.attemptCount ?: 0, input.attemptCount ?: 1, 1),
			firstTryPracticeCorrect = firstTry,
			incorrectPracticeRetries = maxOf(
				existing?.incorrectPracticeRetries ?: 0,
				input.incorrectPracticeRetries ?: 0,
			),
			starsEarned = stars,
			startedAt = existing?.startedAt ?: now,
			completedAt = now,
			updatedAt = now,
			contentVersionAtStart = existing?.contentVersionAtStart ?: lesson.contentVersion,
		)
		progressRepository.save(progress)
		applyStepResults(childId, input.stepResults)

		val wallet = progressRepository.findWallet(childId) ?: RewardWallet(childId = childId)
		val walletWithStars = wallet.copy(
			totalStars = wallet.totalStars + stars,
		)
		progressRepository.saveWallet(walletWithStars)
		val reconciliation = reconcileAchievements(childId, walletWithStars)

		return CompleteLessonResult(
			progress = progress,
			newlyGrantedAchievements = reconciliation.granted,
			wallet = reconciliation.wallet,
		)
	}

	fun getRewards(childId: String): RewardsResult {
		childService.requireOwnedChildForCurrentParent(childId)
		val storedWallet = progressRepository.findWallet(childId) ?: RewardWallet(childId = childId)
		val reconciliation = reconcileAchievements(childId, storedWallet)
		val context = achievementContext(childId, reconciliation.wallet.totalStars)
		val achievements = stickerService.list().filter(Sticker::active).map { sticker ->
			val unlocked = sticker.id in reconciliation.wallet.achievementIds
			val calculatedProgress = achievementProgress(sticker, context)
			AchievementItem(
				id = sticker.id,
				slug = sticker.slug,
				icon = sticker.icon,
				title = sticker.title,
				description = sticker.description,
				unlocked = unlocked,
				unlockedAt = reconciliation.wallet.achievementUnlockedAt[sticker.id]?.toString(),
				progress = if (unlocked) calculatedProgress.copy(current = calculatedProgress.target) else calculatedProgress,
			)
		}
		return RewardsResult(
			totalStars = reconciliation.wallet.totalStars,
			achievements = achievements,
		)
	}

	fun getReview(childId: String): ReviewResult {
		val child = childService.requireOwnedChildForCurrentParent(childId)
		val lessons = lessonService.list()
		val completed = progressRepository.findByChildId(childId)
			.filter { it.status == LessonProgressStatus.completed }
			.mapNotNull { progress -> lessons.find { it.id == progress.lessonId } }
			.filter { it.matchesAge(child.age) }

		val priorities = progressRepository.findSkillsByChildId(childId)
			.sortedWith(compareBy<SkillProgress>({ it.state.reviewPriority }, { it.lastPracticedAt }))
			.map { it.objectiveId }
		val candidates = completed.flatMap { lesson ->
			lesson.steps
				.filter { it.type in setOf("repeat", "show", "listen", "order", "story") }
				.map { step ->
					ReviewStep(
						lessonId = lesson.id,
						lessonTitle = lesson.title,
						stepId = step.stepId,
						type = step.type,
						payload = step.payload,
						assets = step.assets,
					)
				}
		}
		val steps = candidates
			.sortedBy { step ->
				val objectiveId = (step.payload["objectiveId"] as? String)
					?.takeIf { it.isNotBlank() }
					?: "${step.lessonId}:${step.stepId}"
				priorities.indexOf(objectiveId).takeIf { it >= 0 } ?: Int.MAX_VALUE
			}
			.distinctBy { it.stepId }
			.take(4)

		return ReviewResult(
			available = steps.isNotEmpty(),
			sourceLessonIds = steps.map { it.lessonId }.distinct(),
			steps = steps,
		)
	}

	fun completeReview(childId: String, results: List<StepResult>) {
		childService.requireOwnedChildForCurrentParent(childId)
		applyStepResults(childId, results)
	}

	fun getParentSummary(childId: String): ParentSummaryResult {
		childService.requireOwnedChildForCurrentParent(childId)
		val lessons = lessonService.list().associateBy { it.id }
		val completed = progressRepository.findByChildId(childId)
			.filter { it.status == LessonProgressStatus.completed }
			.sortedByDescending { it.completedAt }

		val last = completed.firstOrNull()
		val lastLesson = last?.lessonId?.let { lessons[it] }
		val struggle = progressRepository.findByChildId(childId)
			.filter {
				it.attemptCount >= STRUGGLE_ATTEMPT_THRESHOLD ||
					it.incorrectPracticeRetries >= STRUGGLE_RETRY_THRESHOLD
			}
			.mapNotNull { progress ->
				val lesson = lessons[progress.lessonId] ?: return@mapNotNull null
				StruggleHint(
					lessonId = lesson.id,
					title = lesson.title,
					attemptCount = progress.attemptCount,
					incorrectPracticeRetries = progress.incorrectPracticeRetries,
				)
			}

		return ParentSummaryResult(
			lastCompleted = lastLesson?.let {
				LastCompletedSummary(
					lessonId = it.id,
					title = it.title,
					parentNote = it.parentNote,
					completedAt = last.completedAt?.toString(),
					starsEarned = last.starsEarned,
				)
			},
			struggleHints = struggle,
			skills = progressRepository.findSkillsByChildId(childId).map {
				SkillProgressSummary(
					objectiveId = it.objectiveId,
					title = it.objectiveTitle,
					state = it.state.name,
					successfulAttempts = it.successfulAttempts,
					totalAttempts = it.totalAttempts,
					lastPracticedAt = it.lastPracticedAt.toString(),
				)
			},
		)
	}

	private fun applyStepResults(childId: String, results: List<StepResult>) {
		val now = Instant.now()
		results.filter { it.objectiveId.isNotBlank() }.forEach { result ->
			val existing = progressRepository.findSkill(childId, result.objectiveId)
			val total = (existing?.totalAttempts ?: 0) + result.attempts.coerceAtLeast(1)
			val successful = (existing?.successfulAttempts ?: 0) + if (result.correct) 1 else 0
			val accuracy = successful.toFloat() / total.coerceAtLeast(1)
			val state = when {
				existing?.state == SkillMasteryState.mastered && (!result.correct || result.attempts > 1) ->
					SkillMasteryState.review_due
				successful >= 2 && accuracy >= MASTERY_ACCURACY -> SkillMasteryState.mastered
				total == 1 && !result.correct -> SkillMasteryState.introduced
				else -> SkillMasteryState.practicing
			}
			progressRepository.saveSkill(
				SkillProgress(
					childId = childId,
					objectiveId = result.objectiveId,
					objectiveTitle = result.objectiveTitle.ifBlank { result.objectiveId },
					state = state,
					successfulAttempts = successful,
					totalAttempts = total,
					lastPracticedAt = now,
				),
			)
		}
	}

	private fun ensureLessonUnlocked(childId: String, lesson: Lesson) {
		val child = childService.requireOwnedChildForCurrentParent(childId)
		val tracks = trackService.list()
		val lessons = lessonService.list().filter { it.matchesAge(child.age) }
		val progressByLesson = progressRepository.findByChildId(childId).associateBy { it.lessonId }
		val states = buildTrackStates(tracks, lessons, progressByLesson)
		val status = states
			.flatMap { it.lessons }
			.find { it.lessonId == lesson.id }
			?.status
		if (status == null || status == "locked") {
			throw LessonLockedException()
		}
	}

	private fun buildTrackStates(
		tracks: List<Track>,
		lessons: List<Lesson>,
		progressByLesson: Map<String, LessonProgress>,
	): List<PathTrack> {
		var previousTrackDone = true
		return tracks.map { track ->
			val trackLessons = lessons
				.filter { it.trackId == track.id || it.trackId == track.slug }
				.sortedBy { it.orderInTrack }

			var previousLessonCompleted = previousTrackDone
			val lessonStates = trackLessons.map { lesson ->
				val progress = progressByLesson[lesson.id]
				val status = when {
					progress?.status == LessonProgressStatus.completed -> "completed"
					progress?.status == LessonProgressStatus.in_progress -> "in_progress"
					previousLessonCompleted -> "unlocked"
					else -> "locked"
				}
				previousLessonCompleted = progress?.status == LessonProgressStatus.completed
				PathLesson(
					lessonId = lesson.id,
					slug = lesson.slug,
					title = lesson.title,
					description = lesson.description,
					backgroundImg = lesson.backgroundImg,
					orderInTrack = lesson.orderInTrack,
					status = status,
				)
			}

			val trackStatus = when {
				!previousTrackDone && trackLessons.isNotEmpty() -> "locked"
				trackLessons.isNotEmpty() && lessonStates.all { it.status == "completed" } -> "done"
				lessonStates.any { it.status != "locked" } -> "active"
				else -> "locked"
			}
			previousTrackDone = trackStatus == "done" || trackLessons.isEmpty()

			PathTrack(
				trackId = track.id,
				slug = track.slug,
				title = track.title,
				description = track.description,
				iconColor = track.iconColor,
				backgroundImg = track.backgroundImg,
				order = track.order,
				status = trackStatus,
				lessons = lessonStates,
			)
		}
	}

	private fun findContinueLesson(
		trackStates: List<PathTrack>,
		lessons: List<Lesson>,
		progressByLesson: Map<String, LessonProgress>,
	): ContinueLesson? {
		val inProgress = progressByLesson.values
			.filter { it.status == LessonProgressStatus.in_progress }
			.maxByOrNull { it.updatedAt }
		if (inProgress != null) {
			val lesson = lessons.find { it.id == inProgress.lessonId } ?: return null
			return ContinueLesson(
				lessonId = lesson.id,
				title = lesson.title,
				stepIndex = inProgress.currentStepIndex,
				reason = "resume",
			)
		}

		val next = trackStates
			.flatMap { it.lessons }
			.firstOrNull { it.status == "unlocked" }
			?: return null
		val lesson = lessons.find { it.id == next.lessonId } ?: return null
		return ContinueLesson(
			lessonId = lesson.id,
			title = lesson.title,
			stepIndex = 0,
			reason = "next",
		)
	}

	private fun achievementProgress(
		sticker: Sticker,
		context: AchievementContext,
	): AchievementProgress {
		val rule = sticker.rule
		val current = when (rule.metric) {
			AchievementMetric.LESSONS_COMPLETED -> when (rule.scopeType) {
				AchievementScopeType.GLOBAL -> context.lessons.count { it.id in context.completedLessonIds }
				AchievementScopeType.TRACK -> context.track(rule.scopeId)?.let { track ->
					context.lessonsFor(track).count { it.id in context.completedLessonIds }
				} ?: 0
				AchievementScopeType.LESSON -> 0
			}
			AchievementMetric.TOTAL_STARS -> context.totalStars
			AchievementMetric.TRACKS_COMPLETED -> when (rule.scopeType) {
				AchievementScopeType.GLOBAL -> context.tracks.count(context::isTrackCompleted)
				AchievementScopeType.TRACK -> if (
					context.track(rule.scopeId)?.let(context::isTrackCompleted) == true
				) 1 else 0
				AchievementScopeType.LESSON -> 0
			}
			AchievementMetric.SPECIFIC_LESSON_COMPLETED ->
				if (rule.scopeId != null && rule.scopeId in context.completedLessonIds) 1 else 0
		}
		return AchievementProgress(current = current.coerceAtMost(rule.target), target = rule.target)
	}

	private fun achievementContext(childId: String, totalStars: Int): AchievementContext =
		AchievementContext(
			totalStars = totalStars,
			completedLessonIds = progressRepository.findByChildId(childId)
				.filter { it.status == LessonProgressStatus.completed }
				.mapTo(mutableSetOf()) { it.lessonId },
			tracks = trackService.list(),
			lessons = lessonService.list(),
		)

	private fun reconcileAchievements(childId: String, wallet: RewardWallet): AchievementReconciliation {
		val context = achievementContext(childId, wallet.totalStars)
		val achievementIds = wallet.achievementIds.toMutableSet()
		val unlockedAt = wallet.achievementUnlockedAt.toMutableMap()
		val granted = stickerService.list()
			.filter(Sticker::active)
			.filter { sticker -> achievementProgress(sticker, context).let { it.current >= it.target } }
			.filter { sticker -> achievementIds.add(sticker.id) }
			.map { sticker ->
				unlockedAt[sticker.id] = Instant.now()
				GrantedAchievement(id = sticker.id, slug = sticker.slug, title = sticker.title)
			}
		if (granted.isEmpty()) return AchievementReconciliation(wallet = wallet, granted = emptyList())
		val updatedWallet = wallet.copy(
			achievementIds = achievementIds,
			achievementUnlockedAt = unlockedAt,
			lastGrantedAchievementId = granted.last().id,
		)
		progressRepository.saveWallet(updatedWallet)
		return AchievementReconciliation(wallet = updatedWallet, granted = granted)
	}

	private companion object {
		const val STRUGGLE_ATTEMPT_THRESHOLD = 3
		const val STRUGGLE_RETRY_THRESHOLD = 3
		const val MASTERY_ACCURACY = 0.67f
	}
}

private val SkillMasteryState.reviewPriority: Int
	get() = when (this) {
		SkillMasteryState.review_due -> 0
		SkillMasteryState.practicing -> 1
		SkillMasteryState.introduced -> 2
		SkillMasteryState.mastered -> 3
	}

private fun Lesson.matchesAge(age: Int): Boolean =
	ageBand == "all" || ageBand == if (age <= 5) "4-5" else "6-8"

data class ProgressUpsertInput(
	val currentStepIndex: Int,
	val completedStepIds: List<String> = emptyList(),
	val attemptCount: Int = 1,
	val firstTryPracticeCorrect: Boolean = true,
	val incorrectPracticeRetries: Int = 0,
	val clientUpdatedAt: Instant? = null,
)

data class CompleteLessonInput(
	val completedStepIds: List<String> = emptyList(),
	val attemptCount: Int? = null,
	val firstTryPracticeCorrect: Boolean? = null,
	val incorrectPracticeRetries: Int? = null,
	val stepResults: List<StepResult> = emptyList(),
)

data class StepResult(
	val stepId: String,
	val objectiveId: String,
	val objectiveTitle: String,
	val correct: Boolean,
	val attempts: Int,
)

data class PathChild(val id: String, val name: String, val avatarId: String)

data class ContinueLesson(
	val lessonId: String,
	val title: String,
	val stepIndex: Int,
	val reason: String,
)

data class PathLesson(
	val lessonId: String,
	val slug: String,
	val title: String,
	val description: String,
	val backgroundImg: String,
	val orderInTrack: Int,
	val status: String,
)

data class PathTrack(
	val trackId: String,
	val slug: String,
	val title: String,
	val description: String,
	val iconColor: String,
	val backgroundImg: String,
	val order: Int,
	val status: String,
	val lessons: List<PathLesson>,
)

data class SoftProgress(val completedToday: Int, val dailyGoal: Int)

data class RecentAchievement(val achievementId: String, val slug: String, val title: String)

data class PathResult(
	val activeChild: PathChild,
	val continueLesson: ContinueLesson?,
	val tracks: List<PathTrack>,
	val softProgress: SoftProgress,
	val reviewAvailable: Boolean,
	val recentAchievement: RecentAchievement?,
)

data class GrantedAchievement(val id: String, val slug: String, val title: String)

data class CompleteLessonResult(
	val progress: LessonProgress,
	val newlyGrantedAchievements: List<GrantedAchievement>,
	val wallet: RewardWallet,
)

data class AchievementProgress(
	val current: Int,
	val target: Int,
)

data class AchievementItem(
	val id: String,
	val slug: String,
	val icon: String,
	val title: String,
	val description: String,
	val unlocked: Boolean,
	val unlockedAt: String?,
	val progress: AchievementProgress,
)

data class RewardsResult(
	val totalStars: Int,
	val achievements: List<AchievementItem>,
)

private data class AchievementReconciliation(
	val wallet: RewardWallet,
	val granted: List<GrantedAchievement>,
)

private data class AchievementContext(
	val totalStars: Int,
	val completedLessonIds: Set<String>,
	val tracks: List<Track>,
	val lessons: List<Lesson>,
) {
	fun track(idOrSlug: String?): Track? =
		tracks.find { it.id == idOrSlug || it.slug == idOrSlug }

	fun lessonsFor(track: Track): List<Lesson> =
		lessons.filter { it.trackId == track.id || it.trackId == track.slug }

	fun isTrackCompleted(track: Track): Boolean {
		val trackLessons = lessonsFor(track)
		return trackLessons.isNotEmpty() && trackLessons.all { it.id in completedLessonIds }
	}
}

data class ReviewStep(
	val lessonId: String,
	val lessonTitle: String,
	val stepId: String,
	val type: String,
	val payload: Map<String, Any?>,
	val assets: List<String>,
)

data class ReviewResult(
	val available: Boolean,
	val sourceLessonIds: List<String>,
	val steps: List<ReviewStep>,
)

data class LastCompletedSummary(
	val lessonId: String,
	val title: String,
	val parentNote: String,
	val completedAt: String?,
	val starsEarned: Int,
)

data class StruggleHint(
	val lessonId: String,
	val title: String,
	val attemptCount: Int,
	val incorrectPracticeRetries: Int,
)

data class ParentSummaryResult(
	val lastCompleted: LastCompletedSummary?,
	val struggleHints: List<StruggleHint>,
	val skills: List<SkillProgressSummary>,
)

data class SkillProgressSummary(
	val objectiveId: String,
	val title: String,
	val state: String,
	val successfulAttempts: Int,
	val totalAttempts: Int,
	val lastPracticedAt: String,
)

class LessonLockedException : RuntimeException()

class InvalidProgressDataException(override val message: String) : RuntimeException(message)
