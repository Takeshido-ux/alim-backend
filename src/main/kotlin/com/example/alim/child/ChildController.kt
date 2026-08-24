package com.example.alim.child

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class CreateChildRequest(
	@field:NotBlank(message = "Name is required")
	@field:Size(max = 24, message = "Name must be at most 24 characters")
	val name: String,
	@field:Min(value = 4, message = "Age band must be 4–5 or 6–8")
	@field:Max(value = 8, message = "Age band must be 4–5 or 6–8")
	val age: Int,
	@field:NotBlank(message = "Avatar is required")
	val avatarId: String,
)

data class UpdateChildRequest(
	@field:Size(max = 24, message = "Name must be at most 24 characters")
	val name: String? = null,
	@field:Min(value = 4, message = "Age band must be 4–5 or 6–8")
	@field:Max(value = 8, message = "Age band must be 4–5 or 6–8")
	val age: Int? = null,
	val avatarId: String? = null,
)

data class ChildResponse(
	val id: String,
	val name: String,
	val age: Int,
	val avatarId: String,
)

data class ChildrenListResponse(
	val items: List<ChildResponse>,
	val activeChildId: String?,
)

data class ActivateChildResponse(
	val activeChildId: String,
)

@RestController
@RequestMapping("/api/children")
class ChildController(
	private val childService: ChildService,
) {
	@GetMapping
	fun list(): ChildrenListResponse {
		val result = childService.listChildren()
		return ChildrenListResponse(
			items = result.items.map { it.toResponse() },
			activeChildId = result.activeChildId,
		)
	}

	@PostMapping
	fun create(
		@Valid @RequestBody request: CreateChildRequest,
	): ResponseEntity<ChildResponse> {
		val child = childService.createChild(request.name, request.age, request.avatarId)
		return ResponseEntity.status(HttpStatus.CREATED).body(child.toResponse())
	}

	@PatchMapping("/{id}")
	fun update(
		@PathVariable id: String,
		@Valid @RequestBody request: UpdateChildRequest,
	): ChildResponse =
		childService.updateChild(id, request.name, request.age, request.avatarId).toResponse()

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(@PathVariable id: String) {
		childService.archiveChild(id)
	}

	@PutMapping("/{id}/activate")
	fun activate(@PathVariable id: String): ActivateChildResponse =
		ActivateChildResponse(activeChildId = childService.activateChild(id))

	private fun ChildProfile.toResponse() =
		ChildResponse(
			id = id,
			name = name,
			age = age,
			avatarId = avatarId,
		)
}
