package com.example.alim.cartoon

import com.example.alim.persistence.toTimestamp
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "jdbc")
class JdbcCartoonTagRepository(
	private val jdbc: JdbcTemplate,
) : CartoonTagRepository {
	private val mapper = RowMapper { rs, _ ->
		CartoonTag(
			id = rs.getString("id"),
			title = rs.getString("title"),
			icon = rs.getString("icon"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
	}

	override fun findAll(): List<CartoonTag> =
		jdbc.query(
			"""
			SELECT id, title, icon, created_at, updated_at
			FROM cartoon_tags
			ORDER BY created_at, id
			""".trimIndent(),
			mapper,
		)

	override fun findById(id: String): CartoonTag? =
		jdbc.query(
			"""
			SELECT id, title, icon, created_at, updated_at
			FROM cartoon_tags
			WHERE id = ?
			""".trimIndent(),
			mapper,
			id,
		).firstOrNull()

	override fun save(tag: CartoonTag): CartoonTag {
		jdbc.update(
			"""
			INSERT INTO cartoon_tags (id, title, icon, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?)
			ON CONFLICT (id) DO UPDATE SET
				title = EXCLUDED.title,
				icon = EXCLUDED.icon,
				created_at = EXCLUDED.created_at,
				updated_at = EXCLUDED.updated_at
			""".trimIndent(),
			tag.id,
			tag.title,
			tag.icon,
			tag.createdAt.toTimestamp(),
			tag.updatedAt.toTimestamp(),
		)
		return tag
	}

	override fun deleteById(id: String): Boolean =
		jdbc.update("DELETE FROM cartoon_tags WHERE id = ?", id) > 0

	override fun deleteByIds(ids: Collection<String>): Int =
		ids.sumOf { id -> jdbc.update("DELETE FROM cartoon_tags WHERE id = ?", id) }
}
