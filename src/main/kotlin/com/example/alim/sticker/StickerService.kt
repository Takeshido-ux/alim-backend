package com.example.alim.sticker

import com.example.alim.common.SlugGenerator
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class StickerService(
	private val stickerRepository: StickerRepository,
) {
	fun list(): List<Sticker> = stickerRepository.findAll()

	fun getById(id: String): Sticker =
		stickerRepository.findById(id) ?: throw StickerNotFoundException()

	fun findBySlug(slug: String): Sticker? = stickerRepository.findBySlug(slug)

	fun create(input: StickerWriteInput): Sticker {
		validate(input)
		val slug = SoftSlug.unique(input.title) { stickerRepository.findBySlug(it) != null }
		val now = Instant.now()
		return stickerRepository.save(
			Sticker(
				id = UUID.randomUUID().toString(),
				slug = slug,
				title = input.title.trim(),
				description = input.description.trim(),
				icon = input.icon.trim(),
				createdAt = now,
				updatedAt = now,
			),
		)
	}

	fun update(id: String, input: StickerWriteInput): Sticker {
		val existing = getById(id)
		validate(input)
		return stickerRepository.save(
			existing.copy(
				title = input.title.trim(),
				description = input.description.trim(),
				icon = input.icon.trim(),
				updatedAt = Instant.now(),
			),
		)
	}

	fun delete(id: String) {
		if (!stickerRepository.deleteById(id)) {
			throw StickerNotFoundException()
		}
	}

	private fun validate(input: StickerWriteInput) {
		if (input.title.isBlank()) {
			throw InvalidStickerDataException("title is required")
		}
		if (input.description.isBlank()) {
			throw InvalidStickerDataException("description is required")
		}
		if (input.icon.isBlank()) {
			throw InvalidStickerDataException("icon is required")
		}
	}
}

private object SoftSlug {
	fun unique(title: String, exists: (String) -> Boolean): String =
		SlugGenerator.unique(SlugGenerator.fromTitle(title), exists)
}

data class StickerWriteInput(
	val title: String,
	val description: String,
	val icon: String,
)

class StickerNotFoundException : RuntimeException()

class StickerSlugAlreadyExistsException : RuntimeException()

class InvalidStickerDataException(override val message: String) : RuntimeException(message)
