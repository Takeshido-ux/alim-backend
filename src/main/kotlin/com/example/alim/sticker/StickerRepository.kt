package com.example.alim.sticker

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface StickerRepository {
	fun findAll(): List<Sticker>

	fun findById(id: String): Sticker?

	fun findBySlug(slug: String): Sticker?

	fun save(sticker: Sticker): Sticker

	fun deleteById(id: String): Boolean

	fun deleteByIds(ids: Collection<String>): Int
}

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "memory", matchIfMissing = true)
class InMemoryStickerRepository : StickerRepository {
	private val stickersById = ConcurrentHashMap<String, Sticker>()
	private val stickerIdsBySlug = ConcurrentHashMap<String, String>()

	override fun findAll(): List<Sticker> =
		stickersById.values.sortedBy { it.slug }

	override fun findById(id: String): Sticker? = stickersById[id]

	override fun findBySlug(slug: String): Sticker? =
		stickerIdsBySlug[slug]?.let { stickersById[it] }

	override fun save(sticker: Sticker): Sticker {
		val previous = stickersById[sticker.id]
		if (previous != null && previous.slug != sticker.slug) {
			stickerIdsBySlug.remove(previous.slug)
		}
		stickersById[sticker.id] = sticker
		stickerIdsBySlug[sticker.slug] = sticker.id
		return sticker
	}

	override fun deleteById(id: String): Boolean {
		val removed = stickersById.remove(id) ?: return false
		stickerIdsBySlug.remove(removed.slug)
		return true
	}

	override fun deleteByIds(ids: Collection<String>): Int =
		ids.count { deleteById(it) }
}
