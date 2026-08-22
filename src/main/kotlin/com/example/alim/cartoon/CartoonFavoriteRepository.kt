package com.example.alim.cartoon

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface CartoonFavoriteRepository {
	fun findCartoonIds(childId: String): Set<String>

	fun exists(childId: String, cartoonId: String): Boolean

	fun add(childId: String, cartoonId: String)

	fun remove(childId: String, cartoonId: String)

	fun deleteByCartoonId(cartoonId: String)
}

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "memory", matchIfMissing = true)
class InMemoryCartoonFavoriteRepository : CartoonFavoriteRepository {
	private val favorites = ConcurrentHashMap.newKeySet<String>()

	private fun key(childId: String, cartoonId: String) = "$childId::$cartoonId"

	override fun findCartoonIds(childId: String): Set<String> =
		favorites.mapNotNull { entry ->
			val parts = entry.split("::", limit = 2)
			if (parts.size == 2 && parts[0] == childId) parts[1] else null
		}.toSet()

	override fun exists(childId: String, cartoonId: String): Boolean =
		favorites.contains(key(childId, cartoonId))

	override fun add(childId: String, cartoonId: String) {
		favorites.add(key(childId, cartoonId))
	}

	override fun remove(childId: String, cartoonId: String) {
		favorites.remove(key(childId, cartoonId))
	}

	override fun deleteByCartoonId(cartoonId: String) {
		favorites.removeIf { it.endsWith("::$cartoonId") }
	}
}
