package com.example.alim.progress

import com.example.alim.child.ChildService
import com.example.alim.lesson.Lesson
import com.example.alim.lesson.LessonService
import com.example.alim.parent.CurrentParentResolver
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
		val lessons = lessonService.list()
		val progressByLesson = progressRepository.findByChildId(childId).associateBy { it.lessonId }
		val wallet = progressRepository.findWallet(childId) ?: RewardWallet(childId = childId)

		val trackStates = buildTrackStates(tracks, lessons, progressByLesson)
		val continueLesson = findContinueLesson(trackStates, lessons, progressByLesson)
		val completedToday = progressByLesson.values.count { progress ->
			progress.status == LessonProgressStatus.completed &&
				progress.completedAt?.atZone(ZoneOffset.UTC)?.toLocalDate() == LocalDate.now(ZoneOffset.UTC)
		}
		val tracksById = tracks.associateBy { it.id }
		val tracksBySlug = tracks.associateBy { it.slug }
		val reviewAvailable = progressByLesson.values.any { progress ->
			if (progress.status != LessonProgressStatus.completed) {
				return@any false
			}
			val lesson = lessons.find { it.id == progress.lessonId } ?: return@any false
			val track = tracksById[lesson.trackId] ?: tracksBySlug[lesson.trackId]
			track?.slug in REVIEW_TRACKS
		}

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
			recentSticker = wallet.lastGrantedStickerId?.let { stickerId ->
				stickerService.list().find { it.id == stickerId }?.let {
					RecentSticker(stickerId = it.id, slug = it.slug, title = it.title)
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
			return CompleteLessonResult(
				progress = existing,
				newlyGrantedStickers = emptyList(),
				wallet = wallet,
			)
		}

		val now = Instant.now()
		val completedStepIds = (input.completedStepIds.ifEmpty { existing?.completedStepIds.orEmpty() })
			.distinct()
		val allStepsDone = lesson.steps.all { it.stepId in completedStepIds } ||
			completedStepIds.size >= lesson.steps.size
		val firstTry = input.firstTryPracticeCorrect ?: existing?.firstTryPracticeCorrect ?: true
		val stars = when {
			allStepsDone && firstTry -> 3
			allStepsDone -> 2
			else -> 1
		}.coerceIn(1, 3)

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

		val wallet = progressRepository.findWallet(childId) ?: RewardWallet(childId = childId)
		val granted = mutableListOf<GrantedSticker>()
		val stickerIds = wallet.stickerIds.toMutableSet()
		var lastGranted: String? = wallet.lastGrantedStickerId

		fun grantSticker(stickerIdOrSlug: String) {
			val sticker = runCatching { stickerService.getById(stickerIdOrSlug) }.getOrNull()
				?: stickerService.findBySlug(stickerIdOrSlug)
				?: return
			if (stickerIds.add(sticker.id)) {
				lastGranted = sticker.id
				granted += GrantedSticker(id = sticker.id, slug = sticker.slug, title = sticker.title)
			}
		}

		val anyCompletedBefore = progressRepository.findByChildId(childId)
			.any { it.lessonId != lessonId && it.status == LessonProgressStatus.completed }
		if (!anyCompletedBefore) {
			grantSticker(FIRST_STEP_STICKER_SLUG)
		}

		val track = trackService.list().find { it.id == lesson.trackId || it.slug == lesson.trackId }
		track?.stickerMilestones?.get(lesson.orderInTrack)?.let { grantSticker(it) }

		val updatedWallet = wallet.copy(
			totalStars = wallet.totalStars + stars,
			stickerIds = stickerIds,
			lastGrantedStickerId = lastGranted,
		)
		progressRepository.saveWallet(updatedWallet)

		return CompleteLessonResult(
			progress = progress,
			newlyGrantedStickers = granted,
			wallet = updatedWallet,
		)
	}

	fun getRewards(childId: String): RewardsResult {
		childService.requireOwnedChildForCurrentParent(childId)
		val wallet = progressRepository.findWallet(childId) ?: RewardWallet(childId = childId)
		val stickers = stickerService.list().map { sticker ->
			RewardStickerItem(
				id = sticker.id,
				slug = sticker.slug,
				title = sticker.title,
				earned = sticker.id in wallet.stickerIds,
			)
		}
		return RewardsResult(
			totalStars = wallet.totalStars,
			lastGrantedStickerId = wallet.lastGrantedStickerId,
			stickers = stickers,
		)
	}

	fun getReview(childId: String): ReviewResult {
		childService.requireOwnedChildForCurrentParent(childId)
		val lessons = lessonService.list()
		val tracks = trackService.list()
		val tracksById = tracks.associateBy { it.id }
		val tracksBySlug = tracks.associateBy { it.slug }
		val completed = progressRepository.findByChildId(childId)
			.filter { it.status == LessonProgressStatus.completed }
			.mapNotNull { progress -> lessons.find { it.id == progress.lessonId } }
			.filter { lesson ->
				val track = tracksById[lesson.trackId] ?: tracksBySlug[lesson.trackId]
				track?.slug in REVIEW_TRACKS
			}
			.take(4)

		val steps = completed.flatMap { lesson ->
			lesson.steps
				.filter { it.type in setOf("repeat", "show", "choose_good", "listen") }
				.take(1)
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
		}.take(4)

		return ReviewResult(
			available = steps.isNotEmpty(),
			sourceLessonIds = completed.map { it.id },
			steps = steps,
		)
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
		)
	}

	private fun ensureLessonUnlocked(childId: String, lesson: Lesson) {
		val tracks = trackService.list()
		val lessons = lessonService.list()
		val progressByLesson = progressRepository.findByChildId(childId).associateBy { it.lessonId }
		val states = buildTrackStates(tracks, lessons, progressByLesson)
		val status = states
			.flatMap { it.lessons }
			.find { it.lessonId == lesson.id }
			?.status
		if (status == "locked") {
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

	private companion object {
		val REVIEW_TRACKS = setOf("adab", "dua")
		const val FIRST_STEP_STICKER_SLUG = "first_step"
		const val STRUGGLE_ATTEMPT_THRESHOLD = 3
		const val STRUGGLE_RETRY_THRESHOLD = 3
	}
}

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

data class RecentSticker(val stickerId: String, val slug: String, val title: String)

data class PathResult(
	val activeChild: PathChild,
	val continueLesson: ContinueLesson?,
	val tracks: List<PathTrack>,
	val softProgress: SoftProgress,
	val reviewAvailable: Boolean,
	val recentSticker: RecentSticker?,
)

data class GrantedSticker(val id: String, val slug: String, val title: String)

data class CompleteLessonResult(
	val progress: LessonProgress,
	val newlyGrantedStickers: List<GrantedSticker>,
	val wallet: RewardWallet,
)

data class RewardStickerItem(
	val id: String,
	val slug: String,
	val title: String,
	val earned: Boolean,
)

data class RewardsResult(
	val totalStars: Int,
	val lastGrantedStickerId: String?,
	val stickers: List<RewardStickerItem>,
)

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
)

class LessonLockedException : RuntimeException()

class InvalidProgressDataException(override val message: String) : RuntimeException(message)
