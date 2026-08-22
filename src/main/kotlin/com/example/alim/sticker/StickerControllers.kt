package com.example.alim.sticker

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Min
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

data class StickerWriteRequest(
	@field:NotBlank(message = "title is required")
	val title: String,
	@field:NotBlank(message = "description is required")
	val description: String,
	@field:NotBlank(message = "icon is required")
	val icon: String,
	@field:Valid
	val rule: AchievementRuleRequest,
	val active: Boolean,
	@field:Min(value = 0, message = "order must be >= 0")
	val order: Int,
)

data class AchievementRuleRequest(
	val metric: AchievementMetric,
	@field:Min(value = 1, message = "target must be >= 1")
	val target: Int,
	val scopeType: AchievementScopeType,
	val scopeId: String? = null,
)

data class StickerResponse(
	val id: String,
	val slug: String,
	val title: String,
	val description: String,
	val icon: String,
	val rule: AchievementRule,
	val active: Boolean,
	val order: Int,
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
	val description: String,
	val icon: String,
	val rule: AchievementRule,
	val active: Boolean,
	val order: Int,
)

data class CatalogStickerListResponse(
	val items: List<CatalogStickerResponse>,
)

@RestController
@RequestMapping("/api/admin/achievements")
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
		val sticker = stickerService.create(request.toInput())
		return ResponseEntity.status(HttpStatus.CREATED).body(sticker.toResponse())
	}

	@PutMapping("/{id}")
	fun update(
		@PathVariable id: String,
		@Valid @RequestBody request: StickerWriteRequest,
	): StickerResponse =
		stickerService.update(id, request.toInput()).toResponse()

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(@PathVariable id: String) {
		stickerService.delete(id)
	}
}

@RestController
@RequestMapping("/api/catalog/achievements")
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
		description = description,
		icon = icon,
		rule = rule,
		active = active,
		order = order,
		createdAt = createdAt.toString(),
		updatedAt = updatedAt.toString(),
	)

private fun Sticker.toCatalogResponse() =
	CatalogStickerResponse(
		id = id,
		slug = slug,
		title = title,
		description = description,
		icon = icon,
		rule = rule,
		active = active,
		order = order,
	)

private fun StickerWriteRequest.toInput() =
	StickerWriteInput(
		title = title,
		description = description,
		icon = icon,
		rule = AchievementRule(
			metric = rule.metric,
			target = rule.target,
			scopeType = rule.scopeType,
			scopeId = rule.scopeId,
		),
		active = active,
		order = order,
	)
