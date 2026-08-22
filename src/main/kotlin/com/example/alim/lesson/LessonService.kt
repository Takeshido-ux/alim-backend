package com.example.alim.lesson

import com.example.alim.common.SlugGenerator
import com.example.alim.sticker.AchievementScopeType
import com.example.alim.sticker.StickerRepository
import com.example.alim.track.TrackNotFoundException
import com.example.alim.track.TrackService
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class LessonService(
	private val lessonRepository: LessonRepository,
	private val trackService: TrackService,
	private val stickerRepository: StickerRepository,
) {
	fun list(): List<Lesson> = lessonRepository.findAll()

	fun getById(id: String): Lesson =
		lessonRepository.findById(id) ?: throw LessonNotFoundException()

	fun create(input: LessonWriteInput): Lesson {
		val normalized = input.normalized()
		validate(normalized)
		val slug = SlugGenerator.unique(SlugGenerator.fromTitle(normalized.title)) { candidate ->
			lessonRepository.findBySlug(candidate) != null
		}

		val now = Instant.now()
		val lesson = Lesson(
			id = UUID.randomUUID().toString(),
			slug = slug,
			trackId = resolveTrackId(normalized.trackId),
			orderInTrack = normalized.orderInTrack,
			title = normalized.title,
			description = normalized.description,
			backgroundImg = normalized.backgroundImg,
			parentNote = normalized.parentNote,
			contentVersion = normalized.contentVersion.ifEmpty { "1" },
			steps = normalized.steps.map { it.toStep() },
			createdAt = now,
			updatedAt = now,
		)
		return lessonRepository.save(lesson)
	}

	fun update(id: String, input: LessonWriteInput): Lesson {
		val existing = getById(id)
		val normalized = input.normalized()
		validate(normalized)

		val updated = existing.copy(
			trackId = resolveTrackId(normalized.trackId),
			orderInTrack = normalized.orderInTrack,
			title = normalized.title,
			description = normalized.description,
			backgroundImg = normalized.backgroundImg,
			parentNote = normalized.parentNote,
			contentVersion = normalized.contentVersion.ifEmpty { existing.contentVersion },
			steps = normalized.steps.map { it.toStep() },
			updatedAt = Instant.now(),
		)
		return lessonRepository.save(updated)
	}

	fun delete(id: String) {
		if (stickerRepository.findAll().any {
				it.rule.scopeType == AchievementScopeType.LESSON && it.rule.scopeId == id
			}) {
			throw InvalidLessonDataException("lesson is referenced by an achievement rule")
		}
		if (!lessonRepository.deleteById(id)) {
			throw LessonNotFoundException()
		}
	}

	private fun resolveTrackId(trackRef: String): String {
		val byId = runCatching { trackService.getById(trackRef) }.getOrNull()
		if (byId != null) {
			return byId.id
		}
		return try {
			trackService.requireBySlug(trackRef).id
		} catch (_: TrackNotFoundException) {
			throw InvalidLessonDataException("trackId does not exist: $trackRef")
		}
	}

	private fun validate(input: LessonWriteInput) {
		if (input.orderInTrack < 1) {
			throw InvalidLessonDataException("orderInTrack must be >= 1")
		}
		if (input.title.isBlank()) {
			throw InvalidLessonDataException("title is required")
		}
		if (input.parentNote.isBlank()) {
			throw InvalidLessonDataException("parentNote is required")
		}
		if (input.description.isBlank()) {
			throw InvalidLessonDataException("description is required")
		}
		if (input.steps.size !in MIN_STEPS..MAX_STEPS) {
			throw InvalidLessonDataException("steps must contain $MIN_STEPS to $MAX_STEPS items")
		}

		val stepIds = mutableSetOf<String>()
		input.steps.forEachIndexed { index, step ->
			if (step.stepId.isBlank()) {
				throw InvalidLessonDataException("steps[$index].stepId is required")
			}
			if (!stepIds.add(step.stepId)) {
				throw InvalidLessonDataException("steps[$index].stepId must be unique within the lesson")
			}
			if (step.type !in ALLOWED_STEP_TYPES) {
				throw InvalidLessonDataException(
					"steps[$index].type must be one of: ${ALLOWED_STEP_TYPES.joinToString()}",
				)
			}
		}
	}

	private fun LessonStepInput.toStep(): LessonStep =
		LessonStep(
			stepId = stepId,
			type = type,
			payload = payload,
			assets = assets,
		)

	private companion object {
		const val MIN_STEPS = 4
		const val MAX_STEPS = 7
		val ALLOWED_STEP_TYPES = setOf("listen", "show", "repeat", "order", "video")
	}
}

private fun LessonWriteInput.normalized(): LessonWriteInput {
	val usedStepIds = steps.mapTo(mutableSetOf()) { it.stepId.trim() }.apply { remove("") }
	return copy(
		trackId = trackId.trim(),
		title = title.trim(),
		description = description.trim(),
		backgroundImg = backgroundImg.trim(),
		parentNote = parentNote.trim(),
		contentVersion = contentVersion.trim(),
		steps = steps.map { step ->
			val stepId = if (step.stepId.isNotBlank()) {
				step.stepId.trim()
			} else {
				var generated: String
				do {
					generated = UUID.randomUUID().toString()
				} while (!usedStepIds.add(generated))
				generated
			}
			step.copy(
				stepId = stepId,
				type = if (step.type.trim() == "choose_good") "show" else step.type.trim(),
				assets = step.assets.map(String::trim).filter(String::isNotEmpty),
			)
		},
	)
}

data class LessonStepInput(
	val stepId: String,
	val type: String,
	val payload: Map<String, Any?> = emptyMap(),
	val assets: List<String> = emptyList(),
)

data class LessonWriteInput(
	val trackId: String,
	val orderInTrack: Int,
	val title: String,
	val description: String,
	val backgroundImg: String,
	val parentNote: String,
	val contentVersion: String = "1",
	val steps: List<LessonStepInput>,
)

class LessonNotFoundException : RuntimeException()

class LessonSlugAlreadyExistsException : RuntimeException()

class InvalidLessonDataException(override val message: String) : RuntimeException(message)
