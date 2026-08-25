package com.example.alim.skill

import com.example.alim.lesson.LessonRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class SkillService(
	private val skillRepository: SkillRepository,
	private val lessonRepository: LessonRepository,
) {
	fun list(): List<Skill> = skillRepository.findAll()

	fun getById(id: String): Skill = skillRepository.findById(id) ?: throw SkillNotFoundException()

	fun create(input: SkillWriteInput): Skill {
		val normalized = input.normalized()
		validate(normalized)
		val now = Instant.now()
		return skillRepository.save(
			Skill(
				id = UUID.randomUUID().toString(),
				title = normalized.title,
				requiredSuccesses = normalized.requiredSuccesses,
				minAccuracyPercent = normalized.minAccuracyPercent,
				requiredLessonCount = normalized.requiredLessonCount,
				createdAt = now,
				updatedAt = now,
			),
		)
	}

	fun update(id: String, input: SkillWriteInput): Skill {
		val existing = getById(id)
		val normalized = input.normalized()
		validate(normalized)
		return skillRepository.save(
			existing.copy(
				title = normalized.title,
				requiredSuccesses = normalized.requiredSuccesses,
				minAccuracyPercent = normalized.minAccuracyPercent,
				requiredLessonCount = normalized.requiredLessonCount,
				updatedAt = Instant.now(),
			),
		)
	}

	fun delete(id: String) {
		getById(id)
		if (lessonRepository.findAll().any { lesson ->
				lesson.steps.any { step -> step.payload.skillId() == id }
			}
		) {
			throw InvalidSkillDataException("skill is used by lesson steps")
		}
		if (!skillRepository.deleteById(id)) throw SkillNotFoundException()
	}

	private fun validate(input: SkillWriteInput) {
		if (input.title.isBlank()) throw InvalidSkillDataException("title is required")
		if (input.requiredSuccesses !in 1..20) throw InvalidSkillDataException("requiredSuccesses must be between 1 and 20")
		if (input.minAccuracyPercent !in 1..100) throw InvalidSkillDataException("minAccuracyPercent must be between 1 and 100")
		if (input.requiredLessonCount !in 1..20) throw InvalidSkillDataException("requiredLessonCount must be between 1 and 20")
	}
}

data class SkillWriteInput(
	val title: String,
	val requiredSuccesses: Int = 2,
	val minAccuracyPercent: Int = 67,
	val requiredLessonCount: Int = 1,
)

private fun SkillWriteInput.normalized() = copy(
	title = title.trim(),
)

class SkillNotFoundException : RuntimeException()
class InvalidSkillDataException(override val message: String) : RuntimeException(message)
