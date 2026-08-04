package com.example.alim.sticker

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
import jakarta.validation.Valid

data class StickerWriteRequest(
	@field:NotBlank(message = "title is required")
	val title: String,
)

data class StickerResponse(
	val id: String,
	val slug: String,
	val title: String,
	val createdAt: String,
	val updatedAt: String,
)

data class StickerListResponse(
	val items: List<StickerResponse>,
)

data class CatalogStickerResponse(
	val id: String,
	val slug: String,
	val title: String,
)

data class CatalogStickerListResponse(
	val items: List<CatalogStickerResponse>,
)

@RestController
@RequestMapping("/api/admin/stickers")
class AdminStickerController(
	private val stickerService: StickerService,
) {
	@GetMapping
	fun list(): StickerListResponse =
		StickerListResponse(items = stickerService.list().map { it.toResponse() })

	@GetMapping("/{id}")
	fun get(@PathVariable id: String): StickerResponse =
		stickerService.getById(id).toResponse()

	@PostMapping
	fun create(@Valid @RequestBody request: StickerWriteRequest): ResponseEntity<StickerResponse> {
		val sticker = stickerService.create(StickerWriteInput(request.title))
		return ResponseEntity.status(HttpStatus.CREATED).body(sticker.toResponse())
	}

	@PutMapping("/{id}")
	fun update(
		@PathVariable id: String,
		@Valid @RequestBody request: StickerWriteRequest,
	): StickerResponse =
		stickerService.update(id, StickerWriteInput(request.title)).toResponse()

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(@PathVariable id: String) {
		stickerService.delete(id)
	}
}

@RestController
@RequestMapping("/api/catalog/stickers")
class CatalogStickerController(
	private val stickerService: StickerService,
) {
	@GetMapping
	fun list(): CatalogStickerListResponse =
		CatalogStickerListResponse(items = stickerService.list().map { it.toCatalogResponse() })

	@GetMapping("/{id}")
	fun get(@PathVariable id: String): CatalogStickerResponse =
		stickerService.getById(id).toCatalogResponse()
}

private fun Sticker.toResponse() =
	StickerResponse(
		id = id,
		slug = slug,
		title = title,
		createdAt = createdAt.toString(),
		updatedAt = updatedAt.toString(),
	)

private fun Sticker.toCatalogResponse() =
	CatalogStickerResponse(
		id = id,
		slug = slug,
		title = title,
	)
