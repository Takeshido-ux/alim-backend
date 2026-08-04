package com.example.alim.parent

import com.example.alim.auth.UserRepository
import org.springframework.stereotype.Service

@Service
class PreferencesService(
	private val currentParentResolver: CurrentParentResolver,
	private val userRepository: UserRepository,
) {
	fun getPreferences(): FamilyPreferences =
		currentParentResolver.requireParent().preferences

	fun updatePreferences(preferences: FamilyPreferences): FamilyPreferences {
		validate(preferences)
		val parent = currentParentResolver.requireParent()
		userRepository.update(parent.copy(preferences = preferences))
		return preferences
	}

	private fun validate(preferences: FamilyPreferences) {
		if (preferences.uiLanguage.isBlank() || preferences.voiceLanguage.isBlank()) {
			throw InvalidPreferencesException("Language values must not be blank")
		}
		if (preferences.dailyLessonGoal !in 1..3) {
			throw InvalidPreferencesException("dailyLessonGoal must be between 1 and 3")
		}
	}
}

class InvalidPreferencesException(override val message: String) : RuntimeException(message)
