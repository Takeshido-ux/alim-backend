package com.example.alim.track

import com.example.alim.common.SlugGenerator
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class TrackService(
	private val trackRepository: TrackRepository,
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
				stickerMilestones = input.stickerMilestones,
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
				stickerMilestones = input.stickerMilestones,
				updatedAt = Instant.now(),
			),
		)
	}

	fun delete(id: String) {
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
		input.stickerMilestones.forEach { (lessonOrder, stickerId) ->
			if (lessonOrder < 1) {
				throw InvalidTrackDataException("stickerMilestones keys must be >= 1")
			}
			if (stickerId.isBlank()) {
				throw InvalidTrackDataException("stickerMilestones values must not be blank")
			}
		}
	}
}

data class TrackWriteInput(
	val order: Int,
	val title: String,
	val stickerMilestones: Map<Int, String> = emptyMap(),
)

class TrackNotFoundException : RuntimeException()

class TrackSlugAlreadyExistsException : RuntimeException()

class InvalidTrackDataException(override val message: String) : RuntimeException(message)
