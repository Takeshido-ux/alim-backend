package com.example.alim.child

import com.example.alim.persistence.toInstantOrNull
import com.example.alim.persistence.toTimestamp
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "jdbc")
class JdbcChildRepository(
	private val jdbc: JdbcTemplate,
) : ChildRepository {
	private val mapper = RowMapper { rs, _ ->
		ChildProfile(
			id = rs.getString("id"),
			parentId = rs.getString("parent_id"),
			name = rs.getString("name"),
			age = rs.getInt("age"),
			avatarId = rs.getString("avatar_id"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			archivedAt = rs.getTimestamp("archived_at").toInstantOrNull(),
		)
	}

	override fun findById(id: String): ChildProfile? =
		jdbc.query(
			"""
			SELECT id, parent_id, name, age, avatar_id, created_at, archived_at
			FROM children
			WHERE id = ?
			""".trimIndent(),
			mapper,
			id,
		).firstOrNull()

	override fun findActiveByParentId(parentId: String): List<ChildProfile> =
		jdbc.query(
			"""
			SELECT id, parent_id, name, age, avatar_id, created_at, archived_at
			FROM children
			WHERE parent_id = ? AND archived_at IS NULL
			ORDER BY created_at
			""".trimIndent(),
			mapper,
			parentId,
		)

	override fun save(child: ChildProfile): ChildProfile {
		jdbc.update(
			"""
			INSERT INTO children (id, parent_id, name, age, avatar_id, created_at, archived_at)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (id) DO UPDATE SET
				parent_id = EXCLUDED.parent_id,
				name = EXCLUDED.name,
				age = EXCLUDED.age,
				avatar_id = EXCLUDED.avatar_id,
				created_at = EXCLUDED.created_at,
				archived_at = EXCLUDED.archived_at
			""".trimIndent(),
			child.id,
			child.parentId,
			child.name,
			child.age,
			child.avatarId,
			child.createdAt.toTimestamp(),
			child.archivedAt?.toTimestamp(),
		)
		return child
	}
}
