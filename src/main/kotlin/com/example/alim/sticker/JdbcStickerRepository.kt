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
			rule = AchievementRule(
				metric = AchievementMetric.valueOf(rs.getString("rule_metric")),
				target = rs.getInt("rule_target"),
				scopeType = AchievementScopeType.valueOf(rs.getString("rule_scope_type")),
				scopeId = rs.getString("rule_scope_id"),
			),
			active = rs.getBoolean("is_active"),
			order = rs.getInt("sort_order"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
	}

	override fun findAll(): List<Sticker> =
		jdbc.query(
			"""
			SELECT id, slug, title, description, icon, rule_metric, rule_target,
			       rule_scope_type, rule_scope_id, is_active, sort_order, created_at, updated_at
			FROM achievements
			ORDER BY sort_order, slug
			""".trimIndent(),
			mapper,
		)

	override fun findById(id: String): Sticker? =
		jdbc.query(
			"""
			SELECT id, slug, title, description, icon, rule_metric, rule_target,
			       rule_scope_type, rule_scope_id, is_active, sort_order, created_at, updated_at
			FROM achievements
			WHERE id = ?
			""".trimIndent(),
			mapper,
			id,
		).firstOrNull()

	override fun findBySlug(slug: String): Sticker? =
		jdbc.query(
			"""
			SELECT id, slug, title, description, icon, rule_metric, rule_target,
			       rule_scope_type, rule_scope_id, is_active, sort_order, created_at, updated_at
			FROM achievements
			WHERE slug = ?
			""".trimIndent(),
			mapper,
			slug,
		).firstOrNull()

	override fun save(sticker: Sticker): Sticker {
		jdbc.update(
			"""
			INSERT INTO achievements (
				id, slug, title, description, icon, rule_metric, rule_target,
				rule_scope_type, rule_scope_id, is_active, sort_order, created_at, updated_at
			)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (id) DO UPDATE SET
				slug = EXCLUDED.slug,
				title = EXCLUDED.title,
				description = EXCLUDED.description,
				icon = EXCLUDED.icon,
				rule_metric = EXCLUDED.rule_metric,
				rule_target = EXCLUDED.rule_target,
				rule_scope_type = EXCLUDED.rule_scope_type,
				rule_scope_id = EXCLUDED.rule_scope_id,
				is_active = EXCLUDED.is_active,
				sort_order = EXCLUDED.sort_order,
				created_at = EXCLUDED.created_at,
				updated_at = EXCLUDED.updated_at
			""".trimIndent(),
			sticker.id,
			sticker.slug,
			sticker.title,
			sticker.description,
			sticker.icon,
			sticker.rule.metric.name,
			sticker.rule.target,
			sticker.rule.scopeType.name,
			sticker.rule.scopeId,
			sticker.active,
			sticker.order,
			sticker.createdAt.toTimestamp(),
			sticker.updatedAt.toTimestamp(),
		)
		return sticker
	}

	override fun deleteById(id: String): Boolean =
		jdbc.update("DELETE FROM achievements WHERE id = ?", id) > 0

	override fun deleteByIds(ids: Collection<String>): Int =
		ids.sumOf { id -> jdbc.update("DELETE FROM achievements WHERE id = ?", id) }
}
