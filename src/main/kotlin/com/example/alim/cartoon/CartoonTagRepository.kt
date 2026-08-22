package com.example.alim.cartoon

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface CartoonTagRepository {
	fun findAll(): List<CartoonTag>

	fun findById(id: String): CartoonTag?

	fun save(tag: CartoonTag): CartoonTag

	fun deleteById(id: String): Boolean

	fun deleteByIds(ids: Collection<String>): Int
}

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "memory", matchIfMissing = true)
class InMemoryCartoonTagRepository : CartoonTagRepository {
	private val tagsById = ConcurrentHashMap<String, CartoonTag>()

	override fun findAll(): List<CartoonTag> =
		tagsById.values.sortedWith(compareBy({ it.createdAt }, { it.id }))

	override fun findById(id: String): CartoonTag? = tagsById[id]

	override fun save(tag: CartoonTag): CartoonTag {
		tagsById[tag.id] = tag
		return tag
	}

	override fun deleteById(id: String): Boolean = tagsById.remove(id) != null

	override fun deleteByIds(ids: Collection<String>): Int =
		ids.count { deleteById(it) }
}
