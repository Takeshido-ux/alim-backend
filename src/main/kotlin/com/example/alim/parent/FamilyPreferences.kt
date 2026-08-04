package com.example.alim.parent

data class FamilyPreferences(
	val uiLanguage: String = "ru",
	val voiceLanguage: String = "ru",
	val remindersEnabled: Boolean = false,
	val dailyLessonGoal: Int = 1,
)
