package com.example.alim.cartoon

import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class CartoonService(
	private val cartoonRepository: CartoonRepository,
	private val tagRepository: CartoonTagRepository,
	private val favoriteRepository: CartoonFavoriteRepository,
) {
	fun list(): List<Cartoon> = cartoonRepository.findAll()

	fun getById(id: String): Cartoon =
		cartoonRepository.findById(id) ?: throw CartoonNotFoundException()

	fun create(input: CartoonWriteInput): Cartoon {
		val episodes = input.episodes.withGeneratedIds()
		validate(input, episodes)
		val now = Instant.now()
		return cartoonRepository.save(
			Cartoon(
				id = UUID.randomUUID().toString(),
				title = input.title.trim(),
				description = input.description.trim(),
				img = input.img.trim(),
				video = input.video.trim(),
				tagIds = input.tagIds.normalized(),
				episodes = episodes,
				createdAt = now,
				updatedAt = now,
			),
		)
	}

	fun update(id: String, input: CartoonWriteInput): Cartoon {
		val existing = getById(id)
		val episodes = input.episodes.withGeneratedIds()
		validate(input, episodes)
		return cartoonRepository.save(
			existing.copy(
				title = input.title.trim(),
				description = input.description.trim(),
				img = input.img.trim(),
				video = input.video.trim(),
				tagIds = input.tagIds.normalized(),
				episodes = episodes,
				updatedAt = Instant.now(),
			),
		)
	}

	fun delete(id: String) {
		if (!cartoonRepository.deleteById(id)) {
			throw CartoonNotFoundException()
		}
		favoriteRepository.deleteByCartoonId(id)
	}

	private fun validate(input: CartoonWriteInput, episodes: List<CartoonEpisode>) {
		if (input.title.isBlank()) {
			throw InvalidCartoonDataException("title is required")
		}
		val knownTagIds = tagRepository.findAll().mapTo(mutableSetOf()) { it.id }
		input.tagIds.normalized().forEach { tagId ->
			if (tagId !in knownTagIds) {
				throw InvalidCartoonDataException("unknown tag: $tagId")
			}
		}
		if (episodes.map { it.id }.toSet().size != episodes.size) {
			throw InvalidCartoonDataException("episode id must be unique")
		}
		episodes.forEachIndexed { index, episode ->
			if (episode.title.isBlank()) {
				throw InvalidCartoonDataException("episodes[$index].title is required")
			}
		}
	}
}

data class CartoonWriteInput(
	val title: String,
	val description: String,
	val img: String,
	val video: String,
	val tagIds: List<String>,
	val episodes: List<CartoonEpisode>,
)

private fun List<String>.normalized(): List<String> {
	val seen = mutableSetOf<String>()
	return map { it.trim() }
		.filter { it.isNotEmpty() && seen.add(it) }
}

private fun List<CartoonEpisode>.withGeneratedIds(): List<CartoonEpisode> {
	val usedIds = mapTo(mutableSetOf()) { it.id.trim() }.apply { remove("") }
	return map { episode ->
		val trimmed = episode.copy(
			id = episode.id.trim(),
			title = episode.title.trim(),
			description = episode.description.trim(),
			img = episode.img.trim(),
			video = episode.video.trim(),
		)
		if (trimmed.id.isNotBlank()) {
			trimmed
		} else {
			var generated: String
			do {
				generated = UUID.randomUUID().toString()
			} while (!usedIds.add(generated))
			trimmed.copy(id = generated)
		}
	}
}

class CartoonNotFoundException : RuntimeException()

class CartoonTagNotFoundException : RuntimeException()

class InvalidCartoonDataException(override val message: String) : RuntimeException(message)
