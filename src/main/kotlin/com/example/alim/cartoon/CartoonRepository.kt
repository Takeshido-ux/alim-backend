package com.example.alim.cartoon

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface CartoonRepository {
	fun findAll(): List<Cartoon>

	fun findById(id: String): Cartoon?

	fun save(cartoon: Cartoon): Cartoon

	fun deleteById(id: String): Boolean

	fun removeTagFromAll(tagId: String)
}

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "memory", matchIfMissing = true)
class InMemoryCartoonRepository : CartoonRepository {
	private val cartoonsById = ConcurrentHashMap<String, Cartoon>()

	override fun findAll(): List<Cartoon> =
		cartoonsById.values.sortedWith(compareBy({ it.createdAt }, { it.id }))

	override fun findById(id: String): Cartoon? = cartoonsById[id]

	override fun save(cartoon: Cartoon): Cartoon {
		cartoonsById[cartoon.id] = cartoon
		return cartoon
	}

	override fun deleteById(id: String): Boolean = cartoonsById.remove(id) != null

	override fun removeTagFromAll(tagId: String) {
		cartoonsById.replaceAll { _, cartoon ->
			if (tagId in cartoon.tagIds) {
				cartoon.copy(tagIds = cartoon.tagIds.filterNot { it == tagId })
			} else {
				cartoon
			}
		}
	}
}
