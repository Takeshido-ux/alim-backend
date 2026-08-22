package com.example.alim.track

import com.example.alim.persistence.toTimestamp
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "jdbc")
class JdbcTrackRepository(
	private val jdbc: JdbcTemplate,
) : TrackRepository {
	private val mapper = RowMapper { rs, _ ->
		Track(
			id = rs.getString("id"),
			slug = rs.getString("slug"),
			order = rs.getInt("sort_order"),
			title = rs.getString("title"),
			description = rs.getString("description"),
			iconColor = rs.getString("icon_color"),
			backgroundImg = rs.getString("background_img"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
	}

	override fun findAll(): List<Track> =
		jdbc.query(
			"""
			SELECT id, slug, sort_order, title, description, icon_color, background_img, created_at, updated_at
			FROM tracks
			ORDER BY sort_order, slug
			""".trimIndent(),
			mapper,
		)

	override fun findById(id: String): Track? =
		jdbc.query(
			"""
			SELECT id, slug, sort_order, title, description, icon_color, background_img, created_at, updated_at
			FROM tracks
			WHERE id = ?
			""".trimIndent(),
			mapper,
			id,
		).firstOrNull()

	override fun findBySlug(slug: String): Track? =
		jdbc.query(
			"""
			SELECT id, slug, sort_order, title, description, icon_color, background_img, created_at, updated_at
			FROM tracks
			WHERE slug = ?
			""".trimIndent(),
			mapper,
			slug,
		).firstOrNull()

	override fun save(track: Track): Track {
		jdbc.update(
			"""
			INSERT INTO tracks (id, slug, sort_order, title, description, icon_color, background_img, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (id) DO UPDATE SET
				slug = EXCLUDED.slug,
				sort_order = EXCLUDED.sort_order,
				title = EXCLUDED.title,
				description = EXCLUDED.description,
				icon_color = EXCLUDED.icon_color,
				background_img = EXCLUDED.background_img,
				created_at = EXCLUDED.created_at,
				updated_at = EXCLUDED.updated_at
			""".trimIndent(),
			track.id,
			track.slug,
			track.order,
			track.title,
			track.description,
			track.iconColor,
			track.backgroundImg,
			track.createdAt.toTimestamp(),
			track.updatedAt.toTimestamp(),
		)
		return track
	}

	override fun deleteById(id: String): Boolean =
		jdbc.update("DELETE FROM tracks WHERE id = ?", id) > 0

	override fun deleteByIds(ids: Collection<String>): Int =
		ids.sumOf { id -> jdbc.update("DELETE FROM tracks WHERE id = ?", id) }
}
