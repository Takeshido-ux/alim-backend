package com.example.alim.media

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface MediaRepository {
	fun findById(id: String): MediaAsset?

	fun findAll(): List<MediaAsset>

	fun save(asset: MediaAsset): MediaAsset

	fun deleteById(id: String): MediaAsset?
}

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "memory", matchIfMissing = true)
class InMemoryMediaRepository : MediaRepository {
	private val assets = ConcurrentHashMap<String, MediaAsset>()

	override fun findById(id: String): MediaAsset? = assets[id]

	override fun findAll(): List<MediaAsset> =
		assets.values.sortedByDescending { it.createdAt }

	override fun save(asset: MediaAsset): MediaAsset {
		assets[asset.id] = asset
		return asset
	}

	override fun deleteById(id: String): MediaAsset? = assets.remove(id)
}
