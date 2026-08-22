package com.example.alim.cartoon

import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class CartoonTagService(
	private val tagRepository: CartoonTagRepository,
	private val cartoonRepository: CartoonRepository,
) {
	fun list(): List<CartoonTag> = tagRepository.findAll()

	fun getById(id: String): CartoonTag =
		tagRepository.findById(id) ?: throw CartoonTagNotFoundException()

	fun create(input: CartoonTagWriteInput): CartoonTag {
		validate(input)
		val now = Instant.now()
		return tagRepository.save(
			CartoonTag(
				id = UUID.randomUUID().toString(),
				title = input.title.trim(),
				icon = input.icon.trim(),
				createdAt = now,
				updatedAt = now,
			),
		)
	}

	fun update(id: String, input: CartoonTagWriteInput): CartoonTag {
		val existing = getById(id)
		validate(input)
		return tagRepository.save(
			existing.copy(
				title = input.title.trim(),
				icon = input.icon.trim(),
				updatedAt = Instant.now(),
			),
		)
	}

	fun delete(id: String) {
		if (!tagRepository.deleteById(id)) {
			throw CartoonTagNotFoundException()
		}
		cartoonRepository.removeTagFromAll(id)
	}

	private fun validate(input: CartoonTagWriteInput) {
		if (input.title.isBlank()) {
			throw InvalidCartoonDataException("title is required")
		}
		if (input.icon.isBlank()) {
			throw InvalidCartoonDataException("icon is required")
		}
	}
}

data class CartoonTagWriteInput(
	val title: String,
	val icon: String,
)
