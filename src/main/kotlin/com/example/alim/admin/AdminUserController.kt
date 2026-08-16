package com.example.alim.admin

import com.example.alim.auth.UserAccount
import com.example.alim.auth.UserRepository
import com.example.alim.child.ChildProfile
import com.example.alim.child.ChildRepository
import com.example.alim.lesson.LessonService
import com.example.alim.parent.FamilyPreferences
import com.example.alim.progress.LessonProgress
import com.example.alim.progress.LessonProgressStatus
import com.example.alim.progress.ProgressRepository
import com.example.alim.sticker.StickerService
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class AdminUsersListResponse(
	val items: List<AdminUserSummaryResponse>,
)

data class AdminUserSummaryResponse(
	val id: String,
	val phone: String,
	val createdAt: String,
	val activeChildId: String?,
	val childrenCount: Int,
	val activeChildrenCount: Int,
	val totalStars: Int,
	val completedLessons: Int,
	val inProgressLessons: Int,
	val lastActivityAt: String?,
)

data class AdminUserDetailsResponse(
	val id: String,
	val phone: String,
	val createdAt: String,
	val activeChildId: String?,
	val preferences: AdminPreferencesResponse,
	val children: List<AdminUserChildResponse>,
)

data class AdminPreferencesResponse(
	val uiLanguage: String,
	val voiceLanguage: String,
	val remindersEnabled: Boolean,
	val dailyLessonGoal: Int,
)

data class AdminUserChildResponse(
	val id: String,
	val name: String,
	val age: Int,
	val avatarId: String,
	val createdAt: String,
	val archivedAt: String?,
	val isActive: Boolean,
	val totalStars: Int,
	val earnedStickers: List<AdminUserStickerResponse>,
	val progress: List<AdminUserLessonProgressResponse>,
)

data class AdminUserStickerResponse(
	val id: String,
	val slug: String,
	val title: String,
)

data class AdminUserLessonProgressResponse(
	val lessonId: String,
	val lessonTitle: String,
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

@RestController
@RequestMapping("/api/admin/users")
class AdminUserController(
	private val adminUserService: AdminUserService,
) {
	@GetMapping
	fun list(): AdminUsersListResponse = AdminUsersListResponse(items = adminUserService.list())

	@GetMapping("/{id}")
	fun get(@PathVariable id: String): AdminUserDetailsResponse = adminUserService.get(id)
}

@Service
class AdminUserService(
	private val userRepository: UserRepository,
	private val childRepository: ChildRepository,
	private val progressRepository: ProgressRepository,
	private val lessonService: LessonService,
	private val stickerService: StickerService,
) {
	fun list(): List<AdminUserSummaryResponse> = userRepository.findAll().map { user ->
		val children = childRepository.findByParentId(user.id)
		val progress = children.flatMap { child -> progressRepository.findByChildId(child.id) }
		AdminUserSummaryResponse(
			id = user.id,
			phone = user.phoneNumber,
			createdAt = user.createdAt.asApiValue(),
			activeChildId = user.activeChildId,
			childrenCount = children.size,
			activeChildrenCount = children.count { it.archivedAt == null },
			totalStars = children.sumOf { progressRepository.findWallet(it.id)?.totalStars ?: 0 },
			completedLessons = progress.count { it.status == LessonProgressStatus.completed },
			inProgressLessons = progress.count { it.status == LessonProgressStatus.in_progress },
			lastActivityAt = progress.maxOfOrNull { it.updatedAt }?.asApiValue(),
		)
	}

	fun get(id: String): AdminUserDetailsResponse {
		val user = userRepository.findById(id) ?: throw AdminUserNotFoundException()
		val lessonTitles = lessonService.list().associate { it.id to it.title }
		val stickers = stickerService.list().associateBy { it.id }
		return AdminUserDetailsResponse(
			id = user.id,
			phone = user.phoneNumber,
			createdAt = user.createdAt.asApiValue(),
			activeChildId = user.activeChildId,
			preferences = user.preferences.toResponse(),
			children = childRepository.findByParentId(user.id).map { child ->
				child.toResponse(
					activeChildId = user.activeChildId,
					lessonTitles = lessonTitles,
					stickers = stickers,
				)
			},
		)
	}

	private fun ChildProfile.toResponse(
		activeChildId: String?,
		lessonTitles: Map<String, String>,
		stickers: Map<String, com.example.alim.sticker.Sticker>,
	): AdminUserChildResponse {
		val wallet = progressRepository.findWallet(id)
		return AdminUserChildResponse(
			id = id,
			name = name,
			age = age,
			avatarId = avatarId,
			createdAt = createdAt.asApiValue(),
			archivedAt = archivedAt?.asApiValue(),
			isActive = id == activeChildId,
			totalStars = wallet?.totalStars ?: 0,
			earnedStickers = wallet?.stickerIds.orEmpty().mapNotNull { stickerId ->
				stickers[stickerId]?.let { sticker ->
					AdminUserStickerResponse(
						id = sticker.id,
						slug = sticker.slug,
						title = sticker.title,
					)
				}
			}.sortedBy { it.title },
			progress = progressRepository.findByChildId(id)
				.sortedByDescending { it.updatedAt }
				.map { progress -> progress.toResponse(lessonTitles) },
		)
	}

	private fun LessonProgress.toResponse(
		lessonTitles: Map<String, String>,
	): AdminUserLessonProgressResponse =
		AdminUserLessonProgressResponse(
			lessonId = lessonId,
			lessonTitle = lessonTitles[lessonId] ?: "Удалённый урок",
			status = status.name,
			currentStepIndex = currentStepIndex,
			completedStepIds = completedStepIds,
			attemptCount = attemptCount,
			firstTryPracticeCorrect = firstTryPracticeCorrect,
			incorrectPracticeRetries = incorrectPracticeRetries,
			starsEarned = starsEarned,
			startedAt = startedAt?.asApiValue(),
			completedAt = completedAt?.asApiValue(),
			updatedAt = updatedAt.asApiValue(),
			contentVersionAtStart = contentVersionAtStart,
		)

	private fun FamilyPreferences.toResponse(): AdminPreferencesResponse =
		AdminPreferencesResponse(
			uiLanguage = uiLanguage,
			voiceLanguage = voiceLanguage,
			remindersEnabled = remindersEnabled,
			dailyLessonGoal = dailyLessonGoal,
		)

	private fun Instant.asApiValue(): String = toString()
}

class AdminUserNotFoundException : RuntimeException()
