package com.example.alim.cartoon

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "jdbc")
class JdbcCartoonFavoriteRepository(
	private val jdbc: JdbcTemplate,
) : CartoonFavoriteRepository {
	override fun findCartoonIds(childId: String): Set<String> =
		jdbc.queryForList(
			"SELECT cartoon_id FROM cartoon_favorites WHERE child_id = ?",
			String::class.java,
			childId,
		).filterNotNull().toSet()

	override fun exists(childId: String, cartoonId: String): Boolean {
		val count = jdbc.queryForObject(
			"SELECT COUNT(*) FROM cartoon_favorites WHERE child_id = ? AND cartoon_id = ?",
			Long::class.java,
			childId,
			cartoonId,
		) ?: 0L
		return count > 0L
	}

	override fun add(childId: String, cartoonId: String) {
		jdbc.update(
			"""
			INSERT INTO cartoon_favorites (child_id, cartoon_id)
			VALUES (?, ?)
			ON CONFLICT (child_id, cartoon_id) DO NOTHING
			""".trimIndent(),
			childId,
			cartoonId,
		)
	}

	override fun remove(childId: String, cartoonId: String) {
		jdbc.update(
			"DELETE FROM cartoon_favorites WHERE child_id = ? AND cartoon_id = ?",
			childId,
			cartoonId,
		)
	}

	override fun deleteByCartoonId(cartoonId: String) {
		jdbc.update("DELETE FROM cartoon_favorites WHERE cartoon_id = ?", cartoonId)
	}
}
