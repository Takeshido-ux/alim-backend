package com.example.alim.skill

import java.time.Instant

data class Skill(
	val id: String,
	val title: String,
	val audioUrl: String = "",
	val illustrationUrl: String = "",
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
	return clean + mapOf(
		"skillId" to skill.id,
		"audioUrl" to skill.audioUrl,
		"illustrationUrl" to skill.illustrationUrl,
	)
}
