package com.example.alim.child

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface ChildRepository {
	fun findById(id: String): ChildProfile?

	fun findByParentId(parentId: String): List<ChildProfile>

	fun findActiveByParentId(parentId: String): List<ChildProfile>

	fun save(child: ChildProfile): ChildProfile

	fun deleteById(id: String): Boolean
}

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "memory", matchIfMissing = true)
class InMemoryChildRepository : ChildRepository {
	private val children = ConcurrentHashMap<String, ChildProfile>()

	override fun findById(id: String): ChildProfile? = children[id]

	override fun findByParentId(parentId: String): List<ChildProfile> =
		children.values
			.filter { it.parentId == parentId }
			.sortedBy { it.createdAt }

	override fun findActiveByParentId(parentId: String): List<ChildProfile> =
		children.values
			.filter { it.parentId == parentId && it.archivedAt == null }
			.sortedBy { it.createdAt }

	override fun save(child: ChildProfile): ChildProfile {
		children[child.id] = child
		return child
	}

	override fun deleteById(id: String): Boolean = children.remove(id) != null
}
