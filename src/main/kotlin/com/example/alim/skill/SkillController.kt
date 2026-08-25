package com.example.alim.skill

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

data class SkillWriteRequest(
	@field:NotBlank(message = "title is required")
	val title: String,
	val requiredSuccesses: Int = 2,
	val minAccuracyPercent: Int = 67,
	val requiredLessonCount: Int = 1,
)

data class SkillResponse(
	val id: String,
	val title: String,
	val requiredSuccesses: Int,
	val minAccuracyPercent: Int,
	val requiredLessonCount: Int,
	val createdAt: String,
	val updatedAt: String,
)

data class SkillListResponse(val items: List<SkillResponse>)

@RestController
@RequestMapping("/api/admin/skills")
class SkillController(
	private val skillService: SkillService,
) {
	@GetMapping
	fun list(): SkillListResponse = SkillListResponse(skillService.list().map(Skill::toResponse))

	@GetMapping("/{id}")
	fun get(@PathVariable id: String): SkillResponse = skillService.getById(id).toResponse()

	@PostMapping
	fun create(@Valid @RequestBody request: SkillWriteRequest): ResponseEntity<SkillResponse> =
		ResponseEntity.status(HttpStatus.CREATED).body(skillService.create(request.toInput()).toResponse())

	@PutMapping("/{id}")
	fun update(@PathVariable id: String, @Valid @RequestBody request: SkillWriteRequest): SkillResponse =
		skillService.update(id, request.toInput()).toResponse()

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(@PathVariable id: String) = skillService.delete(id)
}

private fun SkillWriteRequest.toInput() = SkillWriteInput(title, requiredSuccesses, minAccuracyPercent, requiredLessonCount)

private fun Skill.toResponse() = SkillResponse(
	id = id,
	title = title,
	requiredSuccesses = requiredSuccesses,
	minAccuracyPercent = minAccuracyPercent,
	requiredLessonCount = requiredLessonCount,
	createdAt = createdAt.toString(),
	updatedAt = updatedAt.toString(),
)
