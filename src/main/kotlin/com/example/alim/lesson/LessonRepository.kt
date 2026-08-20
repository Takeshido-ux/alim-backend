package com.example.alim.lesson

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface LessonRepository {
	fun findAll(): List<Lesson>

	fun findById(id: String): Lesson?

	fun findBySlug(slug: String): Lesson?

	fun save(lesson: Lesson): Lesson

	fun deleteById(id: String): Boolean

	fun deleteByIds(ids: Collection<String>): Int
}

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "memory", matchIfMissing = true)
class InMemoryLessonRepository : LessonRepository {
	private val lessonsById = ConcurrentHashMap<String, Lesson>()
	private val lessonIdsBySlug = ConcurrentHashMap<String, String>()

	override fun findAll(): List<Lesson> =
		lessonsById.values.sortedWith(
			compareBy({ it.trackId }, { it.orderInTrack }, { it.slug }),
		)

	override fun findById(id: String): Lesson? = lessonsById[id]

	override fun findBySlug(slug: String): Lesson? =
		lessonIdsBySlug[slug]?.let { lessonsById[it] }

	override fun save(lesson: Lesson): Lesson {
		val previous = lessonsById[lesson.id]
		if (previous != null && previous.slug != lesson.slug) {
			lessonIdsBySlug.remove(previous.slug)
		}
		lessonsById[lesson.id] = lesson
		lessonIdsBySlug[lesson.slug] = lesson.id
		return lesson
	}

	override fun deleteById(id: String): Boolean {
		val removed = lessonsById.remove(id) ?: return false
		lessonIdsBySlug.remove(removed.slug)
		return true
	}

	override fun deleteByIds(ids: Collection<String>): Int =
		ids.count { deleteById(it) }
}
