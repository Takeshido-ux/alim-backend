package com.example.alim.sticker

import com.example.alim.common.SlugGenerator
import com.example.alim.lesson.LessonService
import com.example.alim.track.TrackService
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class StickerService(
	private val stickerRepository: StickerRepository,
	private val trackService: TrackService,
	private val lessonService: LessonService,
) {
	fun list(): List<Sticker> = stickerRepository.findAll()

	fun getById(id: String): Sticker =
		stickerRepository.findById(id) ?: throw StickerNotFoundException()

	fun findBySlug(slug: String): Sticker? = stickerRepository.findBySlug(slug)

	fun create(input: StickerWriteInput): Sticker {
		validate(input)
		val slug = SoftSlug.unique(input.title) { stickerRepository.findBySlug(it) != null }
		val now = Instant.now()
		return stickerRepository.save(
			Sticker(
				id = UUID.randomUUID().toString(),
				slug = slug,
				title = input.title.trim(),
				description = input.description.trim(),
				icon = input.icon.trim(),
				rule = input.rule.normalized(),
				active = input.active,
				order = input.order,
				createdAt = now,
				updatedAt = now,
			),
		)
	}

	fun update(id: String, input: StickerWriteInput): Sticker {
		val existing = getById(id)
		validate(input)
		return stickerRepository.save(
			existing.copy(
				title = input.title.trim(),
				description = input.description.trim(),
				icon = input.icon.trim(),
				rule = input.rule.normalized(),
				active = input.active,
				order = input.order,
				updatedAt = Instant.now(),
			),
		)
	}

	fun delete(id: String) {
		if (!stickerRepository.deleteById(id)) {
			throw StickerNotFoundException()
		}
	}

	private fun validate(input: StickerWriteInput) {
		if (input.title.isBlank()) {
			throw InvalidStickerDataException("title is required")
		}
		if (input.description.isBlank()) {
			throw InvalidStickerDataException("description is required")
		}
		if (input.icon.isBlank()) {
			throw InvalidStickerDataException("icon is required")
		}
		if (input.order < 0) {
			throw InvalidStickerDataException("order must be >= 0")
		}
		validateRule(input.rule)
	}

	private fun validateRule(rule: AchievementRule) {
		if (rule.target < 1) {
			throw InvalidStickerDataException("rule.target must be >= 1")
		}
		val valid = when (rule.metric) {
			AchievementMetric.LESSONS_COMPLETED ->
				rule.scopeType == AchievementScopeType.GLOBAL || rule.scopeType == AchievementScopeType.TRACK
			AchievementMetric.TOTAL_STARS -> rule.scopeType == AchievementScopeType.GLOBAL
			AchievementMetric.TRACKS_COMPLETED ->
				rule.scopeType == AchievementScopeType.GLOBAL ||
					(rule.scopeType == AchievementScopeType.TRACK && rule.target == 1)
			AchievementMetric.SPECIFIC_LESSON_COMPLETED ->
				rule.scopeType == AchievementScopeType.LESSON && rule.target == 1
		}
		if (!valid) {
			throw InvalidStickerDataException("rule metric and scope are incompatible")
		}
		val needsScopeId = rule.scopeType != AchievementScopeType.GLOBAL
		if (needsScopeId != !rule.scopeId.isNullOrBlank()) {
			throw InvalidStickerDataException("rule.scopeId does not match rule.scopeType")
		}
		when (rule.scopeType) {
			AchievementScopeType.GLOBAL -> Unit
			AchievementScopeType.TRACK -> if (trackService.list().none { it.id == rule.scopeId }) {
				throw InvalidStickerDataException("rule.scopeId references missing track")
			}
			AchievementScopeType.LESSON -> if (lessonService.list().none { it.id == rule.scopeId }) {
				throw InvalidStickerDataException("rule.scopeId references missing lesson")
			}
		}
	}
}

private fun AchievementRule.normalized() =
	copy(scopeId = scopeId?.trim()?.takeIf(String::isNotEmpty))

private object SoftSlug {
	fun unique(title: String, exists: (String) -> Boolean): String =
		SlugGenerator.unique(SlugGenerator.fromTitle(title), exists)
}

data class StickerWriteInput(
	val title: String,
	val description: String,
	val icon: String,
	val rule: AchievementRule,
	val active: Boolean,
	val order: Int,
)

class StickerNotFoundException : RuntimeException()

class StickerSlugAlreadyExistsException : RuntimeException()

class InvalidStickerDataException(override val message: String) : RuntimeException(message)
