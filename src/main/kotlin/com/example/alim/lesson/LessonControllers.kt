package com.example.alim.lesson

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
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

data class LessonStepRequest(
	val stepId: String = "",
	@field:NotBlank(message = "type is required")
	val type: String,
	val payload: Map<String, Any?> = emptyMap(),
	val assets: List<String> = emptyList(),
)

data class LessonWriteRequest(
	@field:NotBlank(message = "trackId is required")
	val trackId: String,
	@field:Min(value = 1, message = "orderInTrack must be >= 1")
	val orderInTrack: Int,
	@field:NotBlank(message = "title is required")
	val title: String,
	@field:NotBlank(message = "description is required")
	val description: String,
	val backgroundImg: String = "",
	@field:NotBlank(message = "parentNote is required")
	val parentNote: String,
	val contentVersion: String = "1",
	@field:NotEmpty(message = "steps are required")
	@field:Size(min = 4, max = 7, message = "steps must contain 4 to 7 items")
	@field:Valid
	val steps: List<LessonStepRequest>,
)

data class LessonStepResponse(
	val stepId: String,
	val type: String,
	val payload: Map<String, Any?>,
	val assets: List<String>,
)

data class LessonResponse(
	val id: String,
	val slug: String,
	val trackId: String,
	val orderInTrack: Int,
	val title: String,
	val description: String,
	val backgroundImg: String,
	val parentNote: String,
	val contentVersion: String,
	val steps: List<LessonStepResponse>,
	val createdAt: String,
	val updatedAt: String,
)

data class LessonSummaryResponse(
	val id: String,
	val slug: String,
	val trackId: String,
	val orderInTrack: Int,
	val title: String,
	val description: String,
	val backgroundImg: String,
	val contentVersion: String,
)

data class LessonListResponse(
	val items: List<LessonResponse>,
)

data class LessonSummaryListResponse(
	val items: List<LessonSummaryResponse>,
)

@RestController
@RequestMapping("/api/admin/lessons")
class AdminLessonController(
	private val lessonService: LessonService,
) {
	@GetMapping
	fun list(): LessonListResponse =
		LessonListResponse(items = lessonService.list().map { it.toResponse() })

	@GetMapping("/{id}")
	fun get(@PathVariable id: String): LessonResponse =
		lessonService.getById(id).toResponse()

	@PostMapping
	fun create(
		@Valid @RequestBody request: LessonWriteRequest,
	): ResponseEntity<LessonResponse> {
		val lesson = lessonService.create(request.toInput())
		return ResponseEntity.status(HttpStatus.CREATED).body(lesson.toResponse())
	}

	@PutMapping("/{id}")
	fun update(
		@PathVariable id: String,
		@Valid @RequestBody request: LessonWriteRequest,
	): LessonResponse =
		lessonService.update(id, request.toInput()).toResponse()

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(@PathVariable id: String) {
		lessonService.delete(id)
	}
}

@RestController
@RequestMapping("/api/catalog/lessons")
class CatalogLessonController(
	private val lessonService: LessonService,
) {
	@GetMapping
	fun list(): LessonSummaryListResponse =
		LessonSummaryListResponse(
			items = lessonService.list().map { it.toSummaryResponse() },
		)

	@GetMapping("/{id}")
	fun get(@PathVariable id: String): LessonResponse =
		lessonService.getById(id).toResponse()
}

private fun LessonWriteRequest.toInput() =
	LessonWriteInput(
		trackId = trackId.trim(),
		orderInTrack = orderInTrack,
		title = title,
		description = description,
		backgroundImg = backgroundImg,
		parentNote = parentNote,
		contentVersion = contentVersion,
		steps = steps.map {
			LessonStepInput(
				stepId = it.stepId,
				type = it.type,
				payload = it.payload,
				assets = it.assets,
			)
		},
	)

private fun Lesson.toResponse() =
	LessonResponse(
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
			LessonStepResponse(
				stepId = it.stepId,
				type = it.type,
				payload = it.payload,
				assets = it.assets,
			)
		},
		createdAt = createdAt.toString(),
		updatedAt = updatedAt.toString(),
	)

private fun Lesson.toSummaryResponse() =
	LessonSummaryResponse(
		id = id,
		slug = slug,
		trackId = trackId,
		orderInTrack = orderInTrack,
		title = title,
		description = description,
		backgroundImg = backgroundImg,
		contentVersion = contentVersion,
	)
