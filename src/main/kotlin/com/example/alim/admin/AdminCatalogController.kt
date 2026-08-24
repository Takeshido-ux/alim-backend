package com.example.alim.admin

import com.example.alim.cartoon.Cartoon
import com.example.alim.cartoon.CartoonEpisode
import com.example.alim.cartoon.CartoonFavoriteRepository
import com.example.alim.cartoon.CartoonRepository
import com.example.alim.cartoon.CartoonTag
import com.example.alim.cartoon.CartoonTagRepository
import com.example.alim.common.withGeneratedObjectiveId
import com.example.alim.lesson.Lesson
import com.example.alim.lesson.LessonRepository
import com.example.alim.lesson.LessonStep
import com.example.alim.sticker.AchievementMetric
import com.example.alim.sticker.AchievementRule
import com.example.alim.sticker.AchievementScopeType
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
import java.util.UUID

data class AdminCatalogSnapshot(
	val tracks: List<AdminCatalogTrack>,
	val lessons: List<AdminCatalogLesson>,
	val achievements: List<AdminCatalogSticker>,
	val cartoonTags: List<AdminCatalogCartoonTag> = emptyList(),
	val cartoons: List<AdminCatalogCartoon> = emptyList(),
)

data class AdminCatalogTrack(
	val id: String,
	val slug: String,
	val order: Int,
	val title: String,
	val description: String,
	val iconColor: String,
	val backgroundImg: String,
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
	val ageBand: String = "all",
	val contentVersion: String,
	val steps: List<AdminCatalogLessonStep>,
	val createdAt: String = "",
	val updatedAt: String = "",
)

data class AdminCatalogLessonStep(
	val stepId: String = "",
	val type: String,
	val payload: Map<String, Any?> = emptyMap(),
	val assets: List<String> = emptyList(),
)

data class AdminCatalogSticker(
	val id: String,
	val slug: String,
	val title: String,
	val description: String,
	val icon: String,
	val rule: AchievementRule,
	val active: Boolean,
	val order: Int,
	val createdAt: String = "",
	val updatedAt: String = "",
)

data class AdminCatalogCartoonTag(
	val id: String,
	val title: String,
	val icon: String,
	val createdAt: String = "",
	val updatedAt: String = "",
)

data class AdminCatalogCartoonEpisode(
	val id: String = "",
	val title: String,
	val description: String = "",
	val img: String = "",
	val video: String = "",
)

data class AdminCatalogCartoon(
	val id: String,
	val title: String,
	val description: String = "",
	val img: String = "",
	val video: String = "",
	val tagIds: List<String> = emptyList(),
	val episodes: List<AdminCatalogCartoonEpisode> = emptyList(),
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
	private val cartoonTagRepository: CartoonTagRepository,
	private val cartoonRepository: CartoonRepository,
	private val cartoonFavoriteRepository: CartoonFavoriteRepository,
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
		val existingTags = cartoonTagRepository.findAll()
		val existingCartoons = cartoonRepository.findAll()
		validateStableSlugs(normalized, existingTracks, existingLessons, existingStickers)

		val incomingTrackIds = normalized.tracks.mapTo(mutableSetOf()) { it.id }
		val incomingLessonIds = normalized.lessons.mapTo(mutableSetOf()) { it.id }
		val incomingStickerIds = normalized.achievements.mapTo(mutableSetOf()) { it.id }
		val incomingTagIds = normalized.cartoonTags.mapTo(mutableSetOf()) { it.id }
		val incomingCartoonIds = normalized.cartoons.mapTo(mutableSetOf()) { it.id }

		// Child rows must be removed before their parents. Sticker FKs use cascade/set-null.
		lessonRepository.deleteByIds(existingLessons.map { it.id }.filterNot(incomingLessonIds::contains))
		trackRepository.deleteByIds(existingTracks.map { it.id }.filterNot(incomingTrackIds::contains))
		stickerRepository.deleteByIds(existingStickers.map { it.id }.filterNot(incomingStickerIds::contains))
		existingCartoons.map { it.id }.filterNot(incomingCartoonIds::contains).forEach { cartoonId ->
			cartoonFavoriteRepository.deleteByCartoonId(cartoonId)
			cartoonRepository.deleteById(cartoonId)
		}
		cartoonTagRepository.deleteByIds(existingTags.map { it.id }.filterNot(incomingTagIds::contains))

		val now = Instant.now()
		val tracksById = existingTracks.associateBy { it.id }
		val lessonsById = existingLessons.associateBy { it.id }
		val stickersById = existingStickers.associateBy { it.id }
		val tagsById = existingTags.associateBy { it.id }
		val cartoonsById = existingCartoons.associateBy { it.id }

		// Parent/reference targets are upserted before lessons that point to tracks.
		normalized.achievements.forEach { item ->
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
		normalized.cartoonTags.forEach { item ->
			val existing = tagsById[item.id]
			cartoonTagRepository.save(item.toEntity(existing, now))
		}
		normalized.cartoons.forEach { item ->
			val existing = cartoonsById[item.id]
			cartoonRepository.save(item.toEntity(existing, now))
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
			achievements = stickerRepository.findAll().map(Sticker::toSnapshot),
			cartoonTags = cartoonTagRepository.findAll().map(CartoonTag::toSnapshot),
			cartoons = cartoonRepository.findAll().map(Cartoon::toSnapshot),
		)
	}

	private fun validate(snapshot: AdminCatalogSnapshot) {
		requireUnique(snapshot.tracks.map { it.id }, "track id")
		requireUnique(snapshot.tracks.map { it.slug }, "track slug")
		requireUnique(snapshot.tracks.map { it.order }, "track order")
		requireUnique(snapshot.lessons.map { it.id }, "lesson id")
		requireUnique(snapshot.lessons.map { it.slug }, "lesson slug")
		requireUnique(snapshot.achievements.map { it.id }, "achievement id")
		requireUnique(snapshot.achievements.map { it.slug }, "achievement slug")

		val tracksById = snapshot.tracks.associateBy { it.id }
		val lessonIds = snapshot.lessons.mapTo(mutableSetOf()) { it.id }
		requireUnique(snapshot.lessons.map { it.trackId to it.orderInTrack }, "lesson order within track")

		snapshot.tracks.forEachIndexed { index, track ->
			requireIdentifier(track.id, "tracks[$index].id", 36)
			requireIdentifier(track.slug, "tracks[$index].slug", 128)
			if (track.order < 1) invalid("tracks[$index].order must be >= 1")
			if (track.title.isBlank()) invalid("tracks[$index].title is required")
			if (!HEX_COLOR.matches(track.iconColor)) invalid("tracks[$index].iconColor must be a #RRGGBB value")
		}

		snapshot.lessons.forEachIndexed { index, lesson ->
			requireIdentifier(lesson.id, "lessons[$index].id", 36)
			requireIdentifier(lesson.slug, "lessons[$index].slug", 128)
			if (lesson.trackId !in tracksById) invalid("lessons[$index].trackId does not exist: ${lesson.trackId}")
			if (lesson.orderInTrack < 1) invalid("lessons[$index].orderInTrack must be >= 1")
			if (lesson.title.isBlank()) invalid("lessons[$index].title is required")
			if (lesson.contentVersion.isBlank()) invalid("lessons[$index].contentVersion is required")
			if (lesson.ageBand !in ALLOWED_AGE_BANDS) {
				invalid("lessons[$index].ageBand must be one of: ${ALLOWED_AGE_BANDS.joinToString()}")
			}
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

		snapshot.achievements.forEachIndexed { index, sticker ->
			requireIdentifier(sticker.id, "achievements[$index].id", 36)
			requireIdentifier(sticker.slug, "achievements[$index].slug", 128)
			if (sticker.title.isBlank()) invalid("achievements[$index].title is required")
			if (sticker.description.isBlank()) invalid("achievements[$index].description is required")
			if (sticker.icon.isBlank()) invalid("achievements[$index].icon is required")
			if (sticker.order < 0) invalid("achievements[$index].order must be >= 0")
			if (sticker.rule.target < 1) invalid("achievements[$index].rule.target must be >= 1")
			if (sticker.rule.scopeType == AchievementScopeType.GLOBAL && sticker.rule.scopeId != null) {
				invalid("achievements[$index].rule.scopeId must be null for GLOBAL scope")
			}
			if (sticker.rule.scopeType != AchievementScopeType.GLOBAL && sticker.rule.scopeId.isNullOrBlank()) {
				invalid("achievements[$index].rule.scopeId is required")
			}
			when (sticker.rule.metric) {
				AchievementMetric.LESSONS_COMPLETED -> when (sticker.rule.scopeType) {
					AchievementScopeType.GLOBAL -> Unit
					AchievementScopeType.TRACK -> if (sticker.rule.scopeId !in tracksById) {
						invalid("achievements[$index].rule.scopeId references missing track")
					}
					AchievementScopeType.LESSON -> invalid("LESSONS_COMPLETED does not support LESSON scope")
				}
				AchievementMetric.TOTAL_STARS -> if (
					sticker.rule.scopeType != AchievementScopeType.GLOBAL || sticker.rule.scopeId != null
				) invalid("TOTAL_STARS requires GLOBAL scope")
				AchievementMetric.TRACKS_COMPLETED -> when (sticker.rule.scopeType) {
					AchievementScopeType.GLOBAL -> Unit
					AchievementScopeType.TRACK -> {
						if (sticker.rule.scopeId !in tracksById) invalid("rule references missing track")
						if (sticker.rule.target != 1) invalid("specific track rule target must be 1")
					}
					AchievementScopeType.LESSON -> invalid("TRACKS_COMPLETED does not support LESSON scope")
				}
				AchievementMetric.SPECIFIC_LESSON_COMPLETED -> {
					if (sticker.rule.scopeType != AchievementScopeType.LESSON) {
						invalid("SPECIFIC_LESSON_COMPLETED requires LESSON scope")
					}
					if (sticker.rule.scopeId !in lessonIds) invalid("rule references missing lesson")
					if (sticker.rule.target != 1) invalid("specific lesson rule target must be 1")
				}
			}
		}

		requireUnique(snapshot.cartoonTags.map { it.id }, "cartoon tag id")
		requireUnique(snapshot.cartoons.map { it.id }, "cartoon id")
		val tagIds = snapshot.cartoonTags.mapTo(mutableSetOf()) { it.id }
		snapshot.cartoonTags.forEachIndexed { index, tag ->
			requireIdentifier(tag.id, "cartoonTags[$index].id", 36)
			if (tag.title.isBlank()) invalid("cartoonTags[$index].title is required")
			if (tag.icon.isBlank()) invalid("cartoonTags[$index].icon is required")
		}
		snapshot.cartoons.forEachIndexed { index, cartoon ->
			requireIdentifier(cartoon.id, "cartoons[$index].id", 36)
			if (cartoon.title.isBlank()) invalid("cartoons[$index].title is required")
			cartoon.tagIds.forEach { tagId ->
				if (tagId !in tagIds) invalid("cartoons[$index].tagIds references missing tag: $tagId")
			}
			requireUnique(cartoon.episodes.map { it.id }, "episode id in cartoon ${cartoon.id}")
			cartoon.episodes.forEachIndexed { episodeIndex, episode ->
				if (episode.id.isBlank()) invalid("cartoons[$index].episodes[$episodeIndex].id is required")
				if (episode.title.isBlank()) invalid("cartoons[$index].episodes[$episodeIndex].title is required")
			}
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
		requireStableSlugs(
			snapshot.achievements.associate { it.id to it.slug },
			stickers.associate { it.id to it.slug },
			"achievement",
		)
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
		val ALLOWED_STEP_TYPES = setOf("listen", "show", "repeat", "order", "story", "video")
		val ALLOWED_AGE_BANDS = setOf("all", "4-5", "6-8")
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
			ageBand = it.ageBand.trim().ifEmpty { "all" },
			contentVersion = it.contentVersion.trim(),
			steps = it.steps.withGeneratedIds().map { step ->
				step.copy(
					stepId = step.stepId.trim(),
					type = step.type.trim().replaceLegacyStepType(),
					payload = step.payload.withGeneratedObjectiveId(),
					assets = step.assets.map(String::trim).filter(String::isNotEmpty),
				)
			},
		)
	},
	achievements = achievements.map {
		it.copy(
			id = it.id.trim(),
			slug = it.slug.trim(),
			title = it.title.trim(),
			description = it.description.trim(),
			icon = it.icon.trim(),
			rule = it.rule.copy(scopeId = it.rule.scopeId?.trim()?.takeIf(String::isNotEmpty)),
		)
	},
	cartoonTags = cartoonTags.map {
		it.copy(id = it.id.trim(), title = it.title.trim(), icon = it.icon.trim())
	},
	cartoons = cartoons.map {
		it.copy(
			id = it.id.trim(),
			title = it.title.trim(),
			description = it.description.trim(),
			img = it.img.trim(),
			video = it.video.trim(),
			tagIds = it.tagIds.map(String::trim).filter(String::isNotEmpty).distinct(),
			episodes = it.episodes.withGeneratedEpisodeIds().map { episode ->
				episode.copy(
					id = episode.id.trim(),
					title = episode.title.trim(),
					description = episode.description.trim(),
					img = episode.img.trim(),
					video = episode.video.trim(),
				)
			},
		)
	},
)

private fun List<AdminCatalogLessonStep>.withGeneratedIds(): List<AdminCatalogLessonStep> {
	val usedIds = mapTo(mutableSetOf()) { it.stepId.trim() }.apply { remove("") }
	return map { step ->
		if (step.stepId.isNotBlank()) {
			step
		} else {
			var generated: String
			do {
				generated = UUID.randomUUID().toString()
			} while (!usedIds.add(generated))
			step.copy(stepId = generated)
		}
	}
}

private fun List<AdminCatalogCartoonEpisode>.withGeneratedEpisodeIds(): List<AdminCatalogCartoonEpisode> {
	val usedIds = mapTo(mutableSetOf()) { it.id.trim() }.apply { remove("") }
	return map { episode ->
		if (episode.id.isNotBlank()) {
			episode
		} else {
			var generated: String
			do {
				generated = UUID.randomUUID().toString()
			} while (!usedIds.add(generated))
			episode.copy(id = generated)
		}
	}
}

private fun String.replaceLegacyStepType(): String = if (this == "choose_good") "show" else this

private fun AdminCatalogTrack.toEntity(existing: Track?, now: Instant): Track =
	Track(
		id = id,
		slug = slug,
		order = order,
		title = title,
		description = description,
		iconColor = iconColor,
		backgroundImg = backgroundImg,
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
		ageBand = ageBand,
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
		description = description,
		icon = icon,
		rule = rule,
		active = active,
		order = order,
		createdAt = existing?.createdAt ?: now,
		updatedAt = now,
	)

private fun AdminCatalogCartoonTag.toEntity(existing: CartoonTag?, now: Instant): CartoonTag =
	CartoonTag(
		id = id,
		title = title,
		icon = icon,
		createdAt = existing?.createdAt ?: now,
		updatedAt = now,
	)

private fun AdminCatalogCartoon.toEntity(existing: Cartoon?, now: Instant): Cartoon =
	Cartoon(
		id = id,
		title = title,
		description = description,
		img = img,
		video = video,
		tagIds = tagIds,
		episodes = episodes.map {
			CartoonEpisode(
				id = it.id,
				title = it.title,
				description = it.description,
				img = it.img,
				video = it.video,
			)
		},
		createdAt = existing?.createdAt ?: now,
		updatedAt = now,
	)

private fun Track.toSnapshot() =
	AdminCatalogTrack(
		id = id,
		slug = slug,
		order = order,
		title = title,
		description = description,
		iconColor = iconColor,
		backgroundImg = backgroundImg,
		createdAt = createdAt.toString(),
		updatedAt = updatedAt.toString(),
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
		ageBand = ageBand,
		contentVersion = contentVersion,
		steps = steps.map { AdminCatalogLessonStep(it.stepId, it.type, it.payload, it.assets) },
		createdAt = createdAt.toString(),
		updatedAt = updatedAt.toString(),
	)

private fun Sticker.toSnapshot() =
	AdminCatalogSticker(
		id = id,
		slug = slug,
		title = title,
		description = description,
		icon = icon,
		rule = rule,
		active = active,
		order = order,
		createdAt = createdAt.toString(),
		updatedAt = updatedAt.toString(),
	)

private fun CartoonTag.toSnapshot() =
	AdminCatalogCartoonTag(id, title, icon, createdAt.toString(), updatedAt.toString())

private fun Cartoon.toSnapshot() =
	AdminCatalogCartoon(
		id = id,
		title = title,
		description = description,
		img = img,
		video = video,
		tagIds = tagIds,
		episodes = episodes.map {
			AdminCatalogCartoonEpisode(it.id, it.title, it.description, it.img, it.video)
		},
		createdAt = createdAt.toString(),
		updatedAt = updatedAt.toString(),
	)
