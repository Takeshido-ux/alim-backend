package com.example.alim.parent

import com.example.alim.child.ChildRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class MeResponse(
	val id: String,
	val phone: String,
	val activeChildId: String?,
	val childrenCount: Int,
	val preferences: PreferencesResponse,
)

@RestController
@RequestMapping("/api/me")
class MeController(
	private val currentParentResolver: CurrentParentResolver,
	private val childRepository: ChildRepository,
) {
	@GetMapping
	fun me(): MeResponse {
		val parent = currentParentResolver.requireParent()
		val childrenCount = childRepository.findActiveByParentId(parent.id).size
		return MeResponse(
			id = parent.id,
			phone = parent.phoneNumber,
			activeChildId = parent.activeChildId,
			childrenCount = childrenCount,
			preferences = PreferencesResponse(
				uiLanguage = parent.preferences.uiLanguage,
				voiceLanguage = parent.preferences.voiceLanguage,
				remindersEnabled = parent.preferences.remindersEnabled,
				dailyLessonGoal = parent.preferences.dailyLessonGoal,
			),
		)
	}
}
