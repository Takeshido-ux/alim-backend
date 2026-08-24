package com.example.alim.child

import com.example.alim.auth.UserRepository
import com.example.alim.parent.CurrentParentResolver
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ChildService(
	private val childRepository: ChildRepository,
	private val userRepository: UserRepository,
	private val currentParentResolver: CurrentParentResolver,
) {
	fun listChildren(): ChildrenListResult {
		val parent = currentParentResolver.requireParent()
		val items = childRepository.findActiveByParentId(parent.id)
		val activeChildId = parent.activeChildId?.takeIf { id -> items.any { it.id == id } }
		return ChildrenListResult(items = items, activeChildId = activeChildId)
	}

	fun createChild(name: String, age: Int, avatarId: String): ChildProfile {
		val parent = currentParentResolver.requireParent()
		validateChildFields(name, age, avatarId)
		val normalizedAge = age.toAgeBandValue()

		val child = ChildProfile(
			id = UUID.randomUUID().toString(),
			parentId = parent.id,
			name = name.trim(),
			age = normalizedAge,
			avatarId = avatarId,
			createdAt = Instant.now(),
		)
		childRepository.save(child)

		if (parent.activeChildId == null) {
			userRepository.update(parent.copy(activeChildId = child.id))
		}

		return child
	}

	fun updateChild(childId: String, name: String?, age: Int?, avatarId: String?): ChildProfile {
		val parent = currentParentResolver.requireParent()
		val existing = requireOwnedChild(parent.id, childId)

		val nextName = name?.trim() ?: existing.name
		val nextAge = (age ?: existing.age).toAgeBandValue()
		val nextAvatarId = avatarId ?: existing.avatarId
		validateChildFields(nextName, nextAge, nextAvatarId)

		return childRepository.save(
			existing.copy(
				name = nextName,
				age = nextAge,
				avatarId = nextAvatarId,
			),
		)
	}

	fun archiveChild(childId: String) {
		val parent = currentParentResolver.requireParent()
		val existing = requireOwnedChild(parent.id, childId)
		childRepository.save(existing.copy(archivedAt = Instant.now()))

		if (parent.activeChildId == childId) {
			val nextActive = childRepository.findActiveByParentId(parent.id)
				.firstOrNull()
				?.id
			userRepository.update(parent.copy(activeChildId = nextActive))
		}
	}

	fun activateChild(childId: String): String {
		val parent = currentParentResolver.requireParent()
		requireOwnedChild(parent.id, childId)
		userRepository.update(parent.copy(activeChildId = childId))
		return childId
	}

	fun requireOwnedChildForCurrentParent(childId: String): ChildProfile {
		val parent = currentParentResolver.requireParent()
		return requireOwnedChild(parent.id, childId)
	}

	private fun requireOwnedChild(parentId: String, childId: String): ChildProfile {
		val child = childRepository.findById(childId)
			?: throw ChildNotFoundException()
		if (child.parentId != parentId || child.archivedAt != null) {
			throw ChildNotFoundException()
		}
		return child
	}

	private fun validateChildFields(name: String, age: Int, avatarId: String) {
		val trimmed = name.trim()
		if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) {
			throw InvalidChildDataException("Name must be 1 to $MAX_NAME_LENGTH characters")
		}
		if (age !in MIN_AGE_VALUE..MAX_AGE_VALUE) {
			throw InvalidChildDataException("Age band must be 4–5 or 6–8")
		}
		if (avatarId !in ALLOWED_AVATARS) {
			throw InvalidChildDataException("Avatar must be one of: ${ALLOWED_AVATARS.joinToString()}")
		}
	}

	private companion object {
		const val MAX_NAME_LENGTH = 24
		const val MIN_AGE_VALUE = 4
		const val MAX_AGE_VALUE = 8
		val ALLOWED_AVATARS = setOf("sun", "moon", "star", "leaf")
	}
}

private fun Int.toAgeBandValue(): Int = if (this <= 5) 5 else 7

data class ChildrenListResult(
	val items: List<ChildProfile>,
	val activeChildId: String?,
)

class ChildNotFoundException : RuntimeException()

class InvalidChildDataException(override val message: String) : RuntimeException(message)
