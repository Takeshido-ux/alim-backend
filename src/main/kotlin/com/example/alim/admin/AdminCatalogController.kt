package com.example.alim.admin

import com.example.alim.lesson.Lesson
import com.example.alim.lesson.LessonRepository
import com.example.alim.lesson.LessonStep
import com.example.alim.sticker.Sticker
import com.example.alim.sticker.StickerRepository
import com.example.alim.track.Track
import com.example.alim.track.TrackRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class AdminCatalogSnapshot(
	val tracks: List<AdminCatalogTrack>,
	val lessons: List<AdminCatalogLesson>,
	val stickers: List<AdminCatalogSticker>,
)

data class AdminCatalogTrack(
	val id: String,
	val slug: String,
	val order: Int,
	val title: String,
	val description: String,
	val iconColor: String,
	val backgroundImg: String,
	val stickerMilestones: Map<Int, String> = emptyMap(),
	val createdAt: String = "",
	val updatedAt: String = "",
)

data class AdminCatalogLesson(
	val id: String,
	val slug: String,
	val trackId: String,
	val orderInTrack: Int,
	val title: String,
	val description: String,
	val backgroundImg: String = "",
	val parentNote: String,
	val contentVersion: String,
	val steps: List<AdminCatalogLessonStep>,
	val createdAt: String = "",
	val updatedAt: String = "",
)

data class AdminCatalogLessonStep(
	val stepId: String,
	val type: String,
	val payload: Map<String, Any?> = emptyMap(),
	val assets: List<String> = emptyList(),
)

data class AdminCatalogSticker(
	val id: String,
	val slug: String,
	val title: String,
	val createdAt: String = "",
	val updatedAt: String = "",
)

@RestController
@RequestMapping("/api/admin/catalog")
class AdminCatalogController(
	private val adminCatalogService: AdminCatalogService,
) {
	@GetMapping
	fun get(): AdminCatalogSnapshot = adminCatalogService.get()

	@PutMapping
	fun replace(@RequestBody snapshot: AdminCatalogSnapshot): AdminCatalogSnapshot =
		adminCatalogService.replace(snapshot)
}

@Service
class AdminCatalogService(
	private val trackRepository: TrackRepository,
	private val lessonRepository: LessonRepository,
	private val stickerRepository: StickerRepository,
) {
	private val snapshotLock = Any()

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	fun get(): AdminCatalogSnapshot = synchronized(snapshotLock) {
		currentSnapshot()
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	fun replace(snapshot: AdminCatalogSnapshot): AdminCatalogSnapshot = synchronized(snapshotLock) {
		val normalized = snapshot.normalized()
		validate(normalized)

		val existingTracks = trackRepository.findAll()
		val existingLessons = lessonRepository.findAll()
		val existingStickers = stickerRepository.findAll()
		validateStableSlugs(normalized, existingTracks, existingLessons, existingStickers)

		val incomingTrackIds = normalized.tracks.mapTo(mutableSetOf()) { it.id }
		val incomingLessonIds = normalized.lessons.mapTo(mutableSetOf()) { it.id }
		val incomingStickerIds = normalized.stickers.mapTo(mutableSetOf()) { it.id }

		// Child rows must be removed before their parents. Sticker FKs use cascade/set-null.
		lessonRepository.deleteByIds(existingLessons.map { it.id }.filterNot(incomingLessonIds::contains))
		trackRepository.deleteByIds(existingTracks.map { it.id }.filterNot(incomingTrackIds::contains))
		stickerRepository.deleteByIds(existingStickers.map { it.id }.filterNot(incomingStickerIds::contains))

		val now = Instant.now()
		val tracksById = existingTracks.associateBy { it.id }
		val lessonsById = existingLessons.associateBy { it.id }
		val stickersById = existingStickers.associateBy { it.id }

		// Parent/reference targets are upserted before lessons that point to tracks.
		normalized.stickers.forEach { item ->
			val existing = stickersById[item.id]
			stickerRepository.save(item.toEntity(existing, now))
		}
		normalized.tracks.forEach { item ->
			val existing = tracksById[item.id]
			trackRepository.save(item.toEntity(existing, now))
		}
		normalized.lessons.forEach { item ->
			val existing = lessonsById[item.id]
			lessonRepository.save(item.toEntity(existing, now))
		}

		currentSnapshot()
	}

	private fun currentSnapshot(): AdminCatalogSnapshot {
		val tracks = trackRepository.findAll()
		val trackOrder = tracks.mapIndexed { index, track -> track.id to index }.toMap()
		return AdminCatalogSnapshot(
			tracks = tracks.map(Track::toSnapshot),
			lessons = lessonRepository.findAll()
				.sortedWith(compareBy({ trackOrder[it.trackId] ?: Int.MAX_VALUE }, { it.orderInTrack }, { it.slug }))
				.map(Lesson::toSnapshot),
			stickers = stickerRepository.findAll().map(Sticker::toSnapshot),
		)
	}

	private fun validate(snapshot: AdminCatalogSnapshot) {
		requireUnique(snapshot.tracks.map { it.id }, "track id")
		requireUnique(snapshot.tracks.map { it.slug }, "track slug")
		requireUnique(snapshot.tracks.map { it.order }, "track order")
		requireUnique(snapshot.lessons.map { it.id }, "lesson id")
		requireUnique(snapshot.lessons.map { it.slug }, "lesson slug")
		requireUnique(snapshot.stickers.map { it.id }, "sticker id")
		requireUnique(snapshot.stickers.map { it.slug }, "sticker slug")

		val tracksById = snapshot.tracks.associateBy { it.id }
		val stickersByRef = buildMap {
			snapshot.stickers.forEach {
				put(it.id, it)
				put(it.slug, it)
			}
		}
		requireUnique(snapshot.lessons.map { it.trackId to it.orderInTrack }, "lesson order within track")

		snapshot.tracks.forEachIndexed { index, track ->
			requireIdentifier(track.id, "tracks[$index].id", 36)
			requireIdentifier(track.slug, "tracks[$index].slug", 128)
			if (track.order < 1) invalid("tracks[$index].order must be >= 1")
			if (track.title.isBlank()) invalid("tracks[$index].title is required")
			if (!HEX_COLOR.matches(track.iconColor)) invalid("tracks[$index].iconColor must be a #RRGGBB value")
			track.stickerMilestones.forEach { (lessonOrder, stickerRef) ->
				if (lessonOrder < 1) invalid("tracks[$index].stickerMilestones keys must be >= 1")
				if (stickerRef !in stickersByRef) {
					invalid("tracks[$index].stickerMilestones references missing sticker: $stickerRef")
				}
			}
		}

		snapshot.lessons.forEachIndexed { index, lesson ->
			requireIdentifier(lesson.id, "lessons[$index].id", 36)
			requireIdentifier(lesson.slug, "lessons[$index].slug", 128)
			if (lesson.trackId !in tracksById) invalid("lessons[$index].trackId does not exist: ${lesson.trackId}")
			if (lesson.orderInTrack < 1) invalid("lessons[$index].orderInTrack must be >= 1")
			if (lesson.title.isBlank()) invalid("lessons[$index].title is required")
			if (lesson.contentVersion.isBlank()) invalid("lessons[$index].contentVersion is required")
			if (lesson.steps.size !in MIN_STEPS..MAX_STEPS) {
				invalid("lessons[$index].steps must contain $MIN_STEPS to $MAX_STEPS items")
			}
			requireUnique(lesson.steps.map { it.stepId }, "step id in lesson ${lesson.id}")
			lesson.steps.forEachIndexed { stepIndex, step ->
				if (step.stepId.isBlank()) invalid("lessons[$index].steps[$stepIndex].stepId is required")
				if (step.type !in ALLOWED_STEP_TYPES) {
					invalid("lessons[$index].steps[$stepIndex].type must be one of: ${ALLOWED_STEP_TYPES.joinToString()}")
				}
			}
		}

		snapshot.stickers.forEachIndexed { index, sticker ->
			requireIdentifier(sticker.id, "stickers[$index].id", 36)
			requireIdentifier(sticker.slug, "stickers[$index].slug", 128)
			if (sticker.title.isBlank()) invalid("stickers[$index].title is required")
		}
	}

	private fun validateStableSlugs(
		snapshot: AdminCatalogSnapshot,
		tracks: List<Track>,
		lessons: List<Lesson>,
		stickers: List<Sticker>,
	) {
		requireStableSlugs(snapshot.tracks.associate { it.id to it.slug }, tracks.associate { it.id to it.slug }, "track")
		requireStableSlugs(snapshot.lessons.associate { it.id to it.slug }, lessons.associate { it.id to it.slug }, "lesson")
		requireStableSlugs(snapshot.stickers.associate { it.id to it.slug }, stickers.associate { it.id to it.slug }, "sticker")
	}

	private fun requireStableSlugs(incoming: Map<String, String>, existing: Map<String, String>, entity: String) {
		incoming.forEach { (id, slug) ->
			val oldSlug = existing[id]
			if (oldSlug != null && oldSlug != slug) {
				invalid("$entity slug is immutable for id $id")
			}
		}
	}

	private fun requireIdentifier(value: String, field: String, maxLength: Int) {
		if (value.isBlank()) invalid("$field is required")
		if (value.length > maxLength) invalid("$field must not exceed $maxLength characters")
	}

	private fun requireUnique(values: List<Any>, label: String) {
		val duplicate = values.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.key
		if (duplicate != null) invalid("$label must be unique: $duplicate")
	}

	private fun invalid(message: String): Nothing = throw InvalidAdminCatalogException(message)

	private companion object {
		const val MIN_STEPS = 4
		const val MAX_STEPS = 7
		val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
		val ALLOWED_STEP_TYPES = setOf("listen", "show", "repeat", "order", "choose_good", "video")
	}
}

class InvalidAdminCatalogException(override val message: String) : RuntimeException(message)

private fun AdminCatalogSnapshot.normalized() = AdminCatalogSnapshot(
	tracks = tracks.map {
		it.copy(
			id = it.id.trim(),
			slug = it.slug.trim(),
			title = it.title.trim(),
			description = it.description.trim(),
			iconColor = it.iconColor.trim(),
			backgroundImg = it.backgroundImg.trim(),
			stickerMilestones = it.stickerMilestones.mapValues { (_, value) -> value.trim() },
		)
	},
	lessons = lessons.map {
		it.copy(
			id = it.id.trim(),
			slug = it.slug.trim(),
			trackId = it.trackId.trim(),
			title = it.title.trim(),
			description = it.description.trim(),
			backgroundImg = it.backgroundImg.trim(),
			parentNote = it.parentNote.trim(),
			contentVersion = it.contentVersion.trim(),
			steps = it.steps.map { step ->
				step.copy(
					stepId = step.stepId.trim(),
					type = step.type.trim(),
					assets = step.assets.map(String::trim).filter(String::isNotEmpty),
				)
			},
		)
	},
	stickers = stickers.map {
		it.copy(id = it.id.trim(), slug = it.slug.trim(), title = it.title.trim())
	},
)

private fun AdminCatalogTrack.toEntity(existing: Track?, now: Instant): Track =
	Track(
		id = id,
		slug = slug,
		order = order,
		title = title,
		description = description,
		iconColor = iconColor,
		backgroundImg = backgroundImg,
		stickerMilestones = stickerMilestones,
		createdAt = existing?.createdAt ?: now,
		updatedAt = now,
	)

private fun AdminCatalogLesson.toEntity(existing: Lesson?, now: Instant): Lesson =
	Lesson(
		id = id,
		slug = slug,
		trackId = trackId,
		orderInTrack = orderInTrack,
		title = title,
		description = description,
		backgroundImg = backgroundImg,
		parentNote = parentNote,
		contentVersion = contentVersion,
		steps = steps.map {
			LessonStep(stepId = it.stepId, type = it.type, payload = it.payload, assets = it.assets)
		},
		createdAt = existing?.createdAt ?: now,
		updatedAt = now,
	)

private fun AdminCatalogSticker.toEntity(existing: Sticker?, now: Instant): Sticker =
	Sticker(
		id = id,
		slug = slug,
		title = title,
		createdAt = existing?.createdAt ?: now,
		updatedAt = now,
	)

private fun Track.toSnapshot() =
	AdminCatalogTrack(
		id,
		slug,
		order,
		title,
		description,
		iconColor,
		backgroundImg,
		stickerMilestones,
		createdAt.toString(),
		updatedAt.toString(),
	)

private fun Lesson.toSnapshot() =
	AdminCatalogLesson(
		id = id,
		slug = slug,
		trackId = trackId,
		orderInTrack = orderInTrack,
		title = title,
		description = description,
		backgroundImg = backgroundImg,
		parentNote = parentNote,
		contentVersion = contentVersion,
		steps = steps.map { AdminCatalogLessonStep(it.stepId, it.type, it.payload, it.assets) },
		createdAt = createdAt.toString(),
		updatedAt = updatedAt.toString(),
	)

private fun Sticker.toSnapshot() = AdminCatalogSticker(id, slug, title, createdAt.toString(), updatedAt.toString())
