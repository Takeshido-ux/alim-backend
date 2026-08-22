package com.example.alim.cartoon

import jakarta.validation.Valid
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

data class CartoonTagWriteRequest(
	@field:NotBlank(message = "title is required")
	val title: String,
	@field:NotBlank(message = "icon is required")
	val icon: String,
)

data class CartoonTagResponse(
	val id: String,
	val title: String,
	val icon: String,
	val createdAt: String,
	val updatedAt: String,
)

data class CartoonTagListResponse(
	val items: List<CartoonTagResponse>,
)

data class CatalogCartoonTagResponse(
	val id: String,
	val title: String,
	val icon: String,
)

data class CatalogCartoonTagListResponse(
	val items: List<CatalogCartoonTagResponse>,
)

data class CartoonEpisodeWriteRequest(
	val id: String = "",
	@field:NotBlank(message = "title is required")
	val title: String,
	val description: String = "",
	val img: String = "",
	val video: String = "",
)

data class CartoonWriteRequest(
	@field:NotBlank(message = "title is required")
	val title: String,
	val description: String = "",
	val img: String = "",
	val video: String = "",
	val tagIds: List<String> = emptyList(),
	@field:Valid
	val episodes: List<CartoonEpisodeWriteRequest> = emptyList(),
)

data class CartoonEpisodeResponse(
	val id: String,
	val title: String,
	val description: String,
	val img: String,
	val video: String,
)

data class CartoonResponse(
	val id: String,
	val title: String,
	val description: String,
	val img: String,
	val video: String,
	val tagIds: List<String>,
	val episodes: List<CartoonEpisodeResponse>,
	val createdAt: String,
	val updatedAt: String,
)

data class CartoonListResponse(
	val items: List<CartoonResponse>,
)

@RestController
@RequestMapping("/api/admin/cartoon-tags")
class AdminCartoonTagController(
	private val tagService: CartoonTagService,
) {
	@GetMapping
	fun list(): CartoonTagListResponse =
		CartoonTagListResponse(items = tagService.list().map { it.toAdminResponse() })

	@GetMapping("/{id}")
	fun get(@PathVariable id: String): CartoonTagResponse =
		tagService.getById(id).toAdminResponse()

	@PostMapping
	fun create(@Valid @RequestBody request: CartoonTagWriteRequest): ResponseEntity<CartoonTagResponse> {
		val tag = tagService.create(CartoonTagWriteInput(request.title, request.icon))
		return ResponseEntity.status(HttpStatus.CREATED).body(tag.toAdminResponse())
	}

	@PutMapping("/{id}")
	fun update(
		@PathVariable id: String,
		@Valid @RequestBody request: CartoonTagWriteRequest,
	): CartoonTagResponse =
		tagService.update(id, CartoonTagWriteInput(request.title, request.icon)).toAdminResponse()

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(@PathVariable id: String) {
		tagService.delete(id)
	}
}

@RestController
@RequestMapping("/api/admin/cartoons")
class AdminCartoonController(
	private val cartoonService: CartoonService,
) {
	@GetMapping
	fun list(): CartoonListResponse =
		CartoonListResponse(items = cartoonService.list().map { it.toAdminResponse() })

	@GetMapping("/{id}")
	fun get(@PathVariable id: String): CartoonResponse =
		cartoonService.getById(id).toAdminResponse()

	@PostMapping
	fun create(@Valid @RequestBody request: CartoonWriteRequest): ResponseEntity<CartoonResponse> {
		val cartoon = cartoonService.create(request.toInput())
		return ResponseEntity.status(HttpStatus.CREATED).body(cartoon.toAdminResponse())
	}

	@PutMapping("/{id}")
	fun update(
		@PathVariable id: String,
		@Valid @RequestBody request: CartoonWriteRequest,
	): CartoonResponse = cartoonService.update(id, request.toInput()).toAdminResponse()

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(@PathVariable id: String) {
		cartoonService.delete(id)
	}
}

@RestController
@RequestMapping("/api/catalog/cartoon-tags")
class CatalogCartoonTagController(
	private val tagService: CartoonTagService,
) {
	@GetMapping
	fun list(): CatalogCartoonTagListResponse =
		CatalogCartoonTagListResponse(items = tagService.list().map { it.toCatalogResponse() })
}

private fun CartoonWriteRequest.toInput() =
	CartoonWriteInput(
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
	)

private fun CartoonTag.toAdminResponse() =
	CartoonTagResponse(
		id = id,
		title = title,
		icon = icon,
		createdAt = createdAt.toString(),
		updatedAt = updatedAt.toString(),
	)

private fun CartoonTag.toCatalogResponse() =
	CatalogCartoonTagResponse(
		id = id,
		title = title,
		icon = icon,
	)

private fun Cartoon.toAdminResponse() =
	CartoonResponse(
		id = id,
		title = title,
		description = description,
		img = img,
		video = video,
		tagIds = tagIds,
		episodes = episodes.map {
			CartoonEpisodeResponse(
				id = it.id,
				title = it.title,
				description = it.description,
				img = it.img,
				video = it.video,
			)
		},
		createdAt = createdAt.toString(),
		updatedAt = updatedAt.toString(),
	)
