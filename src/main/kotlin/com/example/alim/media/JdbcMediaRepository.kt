package com.example.alim.media

import com.example.alim.persistence.toTimestamp
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "jdbc")
class JdbcMediaRepository(
	private val jdbc: JdbcTemplate,
) : MediaRepository {
	private val mapper = RowMapper { rs, _ ->
		MediaAsset(
			id = rs.getString("id"),
			originalFilename = rs.getString("original_filename"),
			contentType = rs.getString("content_type"),
			sizeBytes = rs.getLong("size_bytes"),
			storageKey = rs.getString("storage_key"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
		)
	}

	override fun findById(id: String): MediaAsset? =
		jdbc.query(
			"""
			SELECT id, original_filename, content_type, size_bytes, storage_key, created_at
			FROM media_assets
			WHERE id = ?
			""".trimIndent(),
			mapper,
			id,
		).firstOrNull()

	override fun findAll(): List<MediaAsset> =
		jdbc.query(
			"""
			SELECT id, original_filename, content_type, size_bytes, storage_key, created_at
			FROM media_assets
			ORDER BY created_at DESC
			""".trimIndent(),
			mapper,
		)

	override fun save(asset: MediaAsset): MediaAsset {
		jdbc.update(
			"""
			INSERT INTO media_assets (id, original_filename, content_type, size_bytes, storage_key, created_at)
			VALUES (?, ?, ?, ?, ?, ?)
			ON CONFLICT (id) DO UPDATE SET
				original_filename = EXCLUDED.original_filename,
				content_type = EXCLUDED.content_type,
				size_bytes = EXCLUDED.size_bytes,
				storage_key = EXCLUDED.storage_key,
				created_at = EXCLUDED.created_at
			""".trimIndent(),
			asset.id,
			asset.originalFilename,
			asset.contentType,
			asset.sizeBytes,
			asset.storageKey,
			asset.createdAt.toTimestamp(),
		)
		return asset
	}

	override fun deleteById(id: String): MediaAsset? {
		val existing = findById(id) ?: return null
		jdbc.update("DELETE FROM media_assets WHERE id = ?", id)
		return existing
	}
}
