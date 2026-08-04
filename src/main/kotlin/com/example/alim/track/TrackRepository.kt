package com.example.alim.track

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface TrackRepository {
	fun findAll(): List<Track>

	fun findById(id: String): Track?

	fun findBySlug(slug: String): Track?

	fun save(track: Track): Track

	fun deleteById(id: String): Boolean
}

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "memory", matchIfMissing = true)
class InMemoryTrackRepository : TrackRepository {
	private val tracksById = ConcurrentHashMap<String, Track>()
	private val trackIdsBySlug = ConcurrentHashMap<String, String>()

	override fun findAll(): List<Track> =
		tracksById.values.sortedWith(compareBy({ it.order }, { it.slug }))

	override fun findById(id: String): Track? = tracksById[id]

	override fun findBySlug(slug: String): Track? =
		trackIdsBySlug[slug]?.let { tracksById[it] }

	override fun save(track: Track): Track {
		val previous = tracksById[track.id]
		if (previous != null && previous.slug != track.slug) {
			trackIdsBySlug.remove(previous.slug)
		}
		tracksById[track.id] = track
		trackIdsBySlug[track.slug] = track.id
		return track
	}

	override fun deleteById(id: String): Boolean {
		val removed = tracksById.remove(id) ?: return false
		trackIdsBySlug.remove(removed.slug)
		return true
	}
}
