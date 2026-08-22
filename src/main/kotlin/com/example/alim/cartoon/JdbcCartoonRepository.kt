package com.example.alim.cartoon

import com.example.alim.persistence.JsonColumns
import com.example.alim.persistence.toTimestamp
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "jdbc")
class JdbcCartoonRepository(
	private val jdbc: JdbcTemplate,
	private val json: JsonColumns,
) : CartoonRepository {
	private val mapper = RowMapper { rs, _ ->
		Cartoon(
			id = rs.getString("id"),
			title = rs.getString("title"),
			description = rs.getString("description"),
			img = rs.getString("img"),
			video = rs.getString("video"),
			tagIds = json.stringList(rs.getString("tag_ids")),
			episodes = json.cartoonEpisodes(rs.getString("episodes")),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
	}

	override fun findAll(): List<Cartoon> =
		jdbc.query(
			"""
			SELECT id, title, description, img, video, tag_ids::text, episodes::text, created_at, updated_at
			FROM cartoons
			ORDER BY created_at, id
			""".trimIndent(),
			mapper,
		)

	override fun findById(id: String): Cartoon? =
		jdbc.query(
			"""
			SELECT id, title, description, img, video, tag_ids::text, episodes::text, created_at, updated_at
			FROM cartoons
			WHERE id = ?
			""".trimIndent(),
			mapper,
			id,
		).firstOrNull()

	override fun save(cartoon: Cartoon): Cartoon {
		jdbc.update(
			"""
			INSERT INTO cartoons (id, title, description, img, video, tag_ids, episodes, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (id) DO UPDATE SET
				title = EXCLUDED.title,
				description = EXCLUDED.description,
				img = EXCLUDED.img,
				video = EXCLUDED.video,
				tag_ids = EXCLUDED.tag_ids,
				episodes = EXCLUDED.episodes,
				created_at = EXCLUDED.created_at,
				updated_at = EXCLUDED.updated_at
			""".trimIndent(),
			cartoon.id,
			cartoon.title,
			cartoon.description,
			cartoon.img,
			cartoon.video,
			json.toJsonb(cartoon.tagIds),
			json.toJsonb(cartoon.episodes),
			cartoon.createdAt.toTimestamp(),
			cartoon.updatedAt.toTimestamp(),
		)
		return cartoon
	}

	override fun deleteById(id: String): Boolean =
		jdbc.update("DELETE FROM cartoons WHERE id = ?", id) > 0

	override fun removeTagFromAll(tagId: String) {
		val now = Instant.now()
		findAll()
			.filter { tagId in it.tagIds }
			.forEach { cartoon ->
				save(
					cartoon.copy(
						tagIds = cartoon.tagIds.filterNot { it == tagId },
						updatedAt = now,
					),
				)
			}
	}
}
