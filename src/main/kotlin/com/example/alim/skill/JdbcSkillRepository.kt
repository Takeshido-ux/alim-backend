package com.example.alim.skill

import com.example.alim.persistence.toTimestamp
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "jdbc")
class JdbcSkillRepository(
	private val jdbc: JdbcTemplate,
) : SkillRepository {
	private val mapper = RowMapper { rs, _ ->
		Skill(
			id = rs.getString("id"),
			title = rs.getString("title"),
			requiredSuccesses = rs.getInt("required_successes"),
			minAccuracyPercent = rs.getInt("min_accuracy_percent"),
			requiredLessonCount = rs.getInt("required_lesson_count"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
	}

	override fun findAll(): List<Skill> = jdbc.query(
		"SELECT id, title, required_successes, min_accuracy_percent, required_lesson_count, created_at, updated_at FROM skills ORDER BY title",
		mapper,
	)

	override fun findById(id: String): Skill? = jdbc.query(
		"SELECT id, title, required_successes, min_accuracy_percent, required_lesson_count, created_at, updated_at FROM skills WHERE id = ?",
		mapper,
		id,
	).firstOrNull()

	override fun save(skill: Skill): Skill {
		jdbc.update(
			"""
			INSERT INTO skills (id, title, required_successes, min_accuracy_percent, required_lesson_count, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (id) DO UPDATE SET
				title = EXCLUDED.title,
				required_successes = EXCLUDED.required_successes,
				min_accuracy_percent = EXCLUDED.min_accuracy_percent,
				required_lesson_count = EXCLUDED.required_lesson_count,
				updated_at = EXCLUDED.updated_at
			""".trimIndent(),
			skill.id,
			skill.title,
			skill.requiredSuccesses,
			skill.minAccuracyPercent,
			skill.requiredLessonCount,
			skill.createdAt.toTimestamp(),
			skill.updatedAt.toTimestamp(),
		)
		return skill
	}

	override fun deleteById(id: String): Boolean =
		jdbc.update("DELETE FROM skills WHERE id = ?", id) > 0

	override fun deleteByIds(ids: Collection<String>): Int =
		ids.count(::deleteById)
}
