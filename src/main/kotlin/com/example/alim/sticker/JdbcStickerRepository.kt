package com.example.alim.sticker

import com.example.alim.persistence.toTimestamp
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "jdbc")
class JdbcStickerRepository(
	private val jdbc: JdbcTemplate,
) : StickerRepository {
	private val mapper = RowMapper { rs, _ ->
		Sticker(
			id = rs.getString("id"),
			slug = rs.getString("slug"),
			title = rs.getString("title"),
			description = rs.getString("description"),
			icon = rs.getString("icon"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
	}

	override fun findAll(): List<Sticker> =
		jdbc.query(
			"""
			SELECT id, slug, title, description, icon, created_at, updated_at
			FROM stickers
			ORDER BY slug
			""".trimIndent(),
			mapper,
		)

	override fun findById(id: String): Sticker? =
		jdbc.query(
			"""
			SELECT id, slug, title, description, icon, created_at, updated_at
			FROM stickers
			WHERE id = ?
			""".trimIndent(),
			mapper,
			id,
		).firstOrNull()

	override fun findBySlug(slug: String): Sticker? =
		jdbc.query(
			"""
			SELECT id, slug, title, description, icon, created_at, updated_at
			FROM stickers
			WHERE slug = ?
			""".trimIndent(),
			mapper,
			slug,
		).firstOrNull()

	override fun save(sticker: Sticker): Sticker {
		jdbc.update(
			"""
			INSERT INTO stickers (id, slug, title, description, icon, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (id) DO UPDATE SET
				slug = EXCLUDED.slug,
				title = EXCLUDED.title,
				description = EXCLUDED.description,
				icon = EXCLUDED.icon,
				created_at = EXCLUDED.created_at,
				updated_at = EXCLUDED.updated_at
			""".trimIndent(),
			sticker.id,
			sticker.slug,
			sticker.title,
			sticker.description,
			sticker.icon,
			sticker.createdAt.toTimestamp(),
			sticker.updatedAt.toTimestamp(),
		)
		return sticker
	}

	override fun deleteById(id: String): Boolean =
		jdbc.update("DELETE FROM stickers WHERE id = ?", id) > 0

	override fun deleteByIds(ids: Collection<String>): Int =
		ids.sumOf { id -> jdbc.update("DELETE FROM stickers WHERE id = ?", id) }
}
