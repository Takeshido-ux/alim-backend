package com.example.alim.skill

import java.time.Instant

data class Skill(
	val id: String,
	val title: String,
	val requiredSuccesses: Int = 2,
	val minAccuracyPercent: Int = 67,
	val requiredLessonCount: Int = 1,
	val createdAt: Instant,
	val updatedAt: Instant,
)

internal fun Map<String, Any?>.skillId(): String =
	(this["skillId"] as? String)?.trim().orEmpty()

internal fun Map<String, Any?>.withoutLegacySkillFields(): Map<String, Any?> =
	this - setOf("objectiveId", "objectiveTitle", "audioUrl", "illustrationUrl", "skillTitle")

internal fun Map<String, Any?>.withResolvedSkill(skill: Skill?): Map<String, Any?> {
	val clean = withoutLegacySkillFields()
	if (skill == null) return clean
	return clean + ("skillId" to skill.id)
}
