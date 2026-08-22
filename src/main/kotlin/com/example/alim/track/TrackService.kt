package com.example.alim.track

import com.example.alim.common.SlugGenerator
import com.example.alim.lesson.LessonRepository
import com.example.alim.sticker.AchievementScopeType
import com.example.alim.sticker.StickerRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class TrackService(
	private val trackRepository: TrackRepository,
	private val lessonRepository: LessonRepository,
	private val stickerRepository: StickerRepository,
) {
	fun list(): List<Track> = trackRepository.findAll()

	fun getById(id: String): Track =
		trackRepository.findById(id) ?: throw TrackNotFoundException()

	fun requireBySlug(slug: String): Track =
		trackRepository.findBySlug(slug) ?: throw TrackNotFoundException()

	fun create(input: TrackWriteInput): Track {
		validate(input)
		val slug = SlugGenerator.unique(SlugGenerator.fromTitle(input.title)) { candidate ->
			trackRepository.findBySlug(candidate) != null
		}

		val now = Instant.now()
		return trackRepository.save(
			Track(
				id = UUID.randomUUID().toString(),
				slug = slug,
				order = input.order,
				title = input.title.trim(),
				description = input.description.trim(),
				iconColor = input.iconColor.trim(),
				backgroundImg = input.backgroundImg.trim(),
				createdAt = now,
				updatedAt = now,
			),
		)
	}

	fun update(id: String, input: TrackWriteInput): Track {
		val existing = getById(id)
		validate(input)
		return trackRepository.save(
			existing.copy(
				order = input.order,
				title = input.title.trim(),
				description = input.description.trim(),
				iconColor = input.iconColor.trim(),
				backgroundImg = input.backgroundImg.trim(),
				updatedAt = Instant.now(),
			),
		)
	}

	fun delete(id: String) {
		val lessonIds = lessonRepository.findAll()
			.filter { it.trackId == id }
			.mapTo(mutableSetOf()) { it.id }
		val referenced = stickerRepository.findAll().any {
			(it.rule.scopeType == AchievementScopeType.TRACK && it.rule.scopeId == id) ||
				(
					it.rule.scopeType == AchievementScopeType.LESSON &&
					it.rule.scopeId != null &&
					it.rule.scopeId in lessonIds
				)
		}
		if (referenced) {
			throw InvalidTrackDataException("track is referenced by an achievement rule")
		}
		if (!trackRepository.deleteById(id)) {
			throw TrackNotFoundException()
		}
	}

	private fun validate(input: TrackWriteInput) {
		if (input.order < 1) {
			throw InvalidTrackDataException("order must be >= 1")
		}
		if (input.title.isBlank()) {
			throw InvalidTrackDataException("title is required")
		}
		if (input.description.isBlank()) {
			throw InvalidTrackDataException("description is required")
		}
		if (!HEX_COLOR.matches(input.iconColor.trim())) {
			throw InvalidTrackDataException("iconColor must be a #RRGGBB value")
		}
		if (input.backgroundImg.isBlank()) {
			throw InvalidTrackDataException("backgroundImg is required")
		}
	}
}

data class TrackWriteInput(
	val order: Int,
	val title: String,
	val description: String,
	val iconColor: String,
	val backgroundImg: String,
)

private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

class TrackNotFoundException : RuntimeException()

class TrackSlugAlreadyExistsException : RuntimeException()

class InvalidTrackDataException(override val message: String) : RuntimeException(message)
