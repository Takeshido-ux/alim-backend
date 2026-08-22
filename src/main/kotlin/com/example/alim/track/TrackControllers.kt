package com.example.alim.track

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class TrackWriteRequest(
	@field:Min(value = 1, message = "order must be >= 1")
	val order: Int,
	@field:NotBlank(message = "title is required")
	val title: String,
	@field:NotBlank(message = "description is required")
	val description: String,
	@field:NotBlank(message = "iconColor is required")
	val iconColor: String,
	@field:NotBlank(message = "backgroundImg is required")
	val backgroundImg: String,
)

data class TrackResponse(
	val id: String,
	val slug: String,
	val order: Int,
	val title: String,
	val description: String,
	val iconColor: String,
	val backgroundImg: String,
	val createdAt: String,
	val updatedAt: String,
)

data class TrackListResponse(
	val items: List<TrackResponse>,
)

data class CatalogTrackResponse(
	val id: String,
	val slug: String,
	val order: Int,
	val title: String,
	val description: String,
	val iconColor: String,
	val backgroundImg: String,
)

data class CatalogTrackListResponse(
	val items: List<CatalogTrackResponse>,
)

@RestController
@RequestMapping("/api/admin/tracks")
class AdminTrackController(
	private val trackService: TrackService,
) {
	@GetMapping
	fun list(): TrackListResponse =
		TrackListResponse(items = trackService.list().map { it.toResponse() })

	@GetMapping("/{id}")
	fun get(@PathVariable id: String): TrackResponse =
		trackService.getById(id).toResponse()

	@PostMapping
	fun create(@Valid @RequestBody request: TrackWriteRequest): ResponseEntity<TrackResponse> {
		val track = trackService.create(request.toInput())
		return ResponseEntity.status(HttpStatus.CREATED).body(track.toResponse())
	}

	@PutMapping("/{id}")
	fun update(
		@PathVariable id: String,
		@Valid @RequestBody request: TrackWriteRequest,
	): TrackResponse = trackService.update(id, request.toInput()).toResponse()

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(@PathVariable id: String) {
		trackService.delete(id)
	}
}

@RestController
@RequestMapping("/api/catalog/tracks")
class CatalogTrackController(
	private val trackService: TrackService,
) {
	@GetMapping
	fun list(): CatalogTrackListResponse =
		CatalogTrackListResponse(items = trackService.list().map { it.toCatalogResponse() })

	@GetMapping("/{id}")
	fun get(@PathVariable id: String): CatalogTrackResponse =
		trackService.getById(id).toCatalogResponse()
}

private fun TrackWriteRequest.toInput() =
	TrackWriteInput(
		order = order,
		title = title,
		description = description,
		iconColor = iconColor,
		backgroundImg = backgroundImg,
	)

private fun Track.toResponse() =
	TrackResponse(
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

private fun Track.toCatalogResponse() =
	CatalogTrackResponse(
		id = id,
		slug = slug,
		order = order,
		title = title,
		description = description,
		iconColor = iconColor,
		backgroundImg = backgroundImg,
	)
