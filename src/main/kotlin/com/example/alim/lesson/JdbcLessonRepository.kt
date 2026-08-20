package com.example.alim.lesson

import com.example.alim.persistence.JsonColumns
import com.example.alim.persistence.toTimestamp
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "jdbc")
class JdbcLessonRepository(
	private val jdbc: JdbcTemplate,
	private val json: JsonColumns,
) : LessonRepository {
	private val mapper = RowMapper { rs, _ ->
		Lesson(
			id = rs.getString("id"),
			slug = rs.getString("slug"),
			trackId = rs.getString("track_id"),
			orderInTrack = rs.getInt("order_in_track"),
			title = rs.getString("title"),
			description = rs.getString("description"),
			backgroundImg = rs.getString("background_img"),
			parentNote = rs.getString("parent_note"),
			contentVersion = rs.getString("content_version"),
			steps = json.lessonSteps(rs.getString("steps")),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
	}

	override fun findAll(): List<Lesson> =
		jdbc.query(
			"""
			SELECT id, slug, track_id, order_in_track, title, description, background_img, parent_note, content_version,
			       steps::text, created_at, updated_at
			FROM lessons
			ORDER BY track_id, order_in_track, slug
			""".trimIndent(),
			mapper,
		)

	override fun findById(id: String): Lesson? =
		jdbc.query(
			"""
			SELECT id, slug, track_id, order_in_track, title, description, background_img, parent_note, content_version,
			       steps::text, created_at, updated_at
			FROM lessons
			WHERE id = ?
			""".trimIndent(),
			mapper,
			id,
		).firstOrNull()

	override fun findBySlug(slug: String): Lesson? =
		jdbc.query(
			"""
			SELECT id, slug, track_id, order_in_track, title, description, background_img, parent_note, content_version,
			       steps::text, created_at, updated_at
			FROM lessons
			WHERE slug = ?
			""".trimIndent(),
			mapper,
			slug,
		).firstOrNull()

	override fun save(lesson: Lesson): Lesson {
		jdbc.update(
			"""
			INSERT INTO lessons (
				id, slug, track_id, order_in_track, title, description, background_img, parent_note,
				content_version, steps, created_at, updated_at
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (id) DO UPDATE SET
				slug = EXCLUDED.slug,
				track_id = EXCLUDED.track_id,
				order_in_track = EXCLUDED.order_in_track,
				title = EXCLUDED.title,
				description = EXCLUDED.description,
				background_img = EXCLUDED.background_img,
				parent_note = EXCLUDED.parent_note,
				content_version = EXCLUDED.content_version,
				steps = EXCLUDED.steps,
				created_at = EXCLUDED.created_at,
				updated_at = EXCLUDED.updated_at
			""".trimIndent(),
			lesson.id,
			lesson.slug,
			lesson.trackId,
			lesson.orderInTrack,
			lesson.title,
			lesson.description,
			lesson.backgroundImg,
			lesson.parentNote,
			lesson.contentVersion,
			json.toJsonb(lesson.steps),
			lesson.createdAt.toTimestamp(),
			lesson.updatedAt.toTimestamp(),
		)
		return lesson
	}

	override fun deleteById(id: String): Boolean =
		jdbc.update("DELETE FROM lessons WHERE id = ?", id) > 0

	override fun deleteByIds(ids: Collection<String>): Int =
		ids.sumOf { id -> jdbc.update("DELETE FROM lessons WHERE id = ?", id) }
}
