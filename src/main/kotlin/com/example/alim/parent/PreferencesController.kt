package com.example.alim.parent

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PreferencesRequest(
	@field:NotBlank(message = "uiLanguage is required")
	val uiLanguage: String,
	@field:NotBlank(message = "voiceLanguage is required")
	val voiceLanguage: String,
	val remindersEnabled: Boolean,
	@field:Min(value = 1, message = "dailyLessonGoal must be between 1 and 3")
	@field:Max(value = 3, message = "dailyLessonGoal must be between 1 and 3")
	val dailyLessonGoal: Int,
)

data class PreferencesResponse(
	val uiLanguage: String,
	val voiceLanguage: String,
	val remindersEnabled: Boolean,
	val dailyLessonGoal: Int,
)

@RestController
@RequestMapping("/api/preferences")
class PreferencesController(
	private val preferencesService: PreferencesService,
) {
	@GetMapping
	fun get(): PreferencesResponse = preferencesService.getPreferences().toResponse()

	@PutMapping
	fun update(
		@Valid @RequestBody request: PreferencesRequest,
	): PreferencesResponse =
		preferencesService.updatePreferences(
			FamilyPreferences(
				uiLanguage = request.uiLanguage.trim(),
				voiceLanguage = request.voiceLanguage.trim(),
				remindersEnabled = request.remindersEnabled,
				dailyLessonGoal = request.dailyLessonGoal,
			),
		).toResponse()

	private fun FamilyPreferences.toResponse() =
		PreferencesResponse(
			uiLanguage = uiLanguage,
			voiceLanguage = voiceLanguage,
			remindersEnabled = remindersEnabled,
			dailyLessonGoal = dailyLessonGoal,
		)
}
