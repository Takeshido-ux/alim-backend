package com.example.alim.common

private val NON_OBJECTIVE_ID_CHARACTERS = Regex("[^\\p{L}\\p{N}]+")

internal fun Map<String, Any?>.withGeneratedObjectiveId(): Map<String, Any?> {
	val currentId = (this["objectiveId"] as? String)?.trim().orEmpty()
	if (currentId.isNotEmpty()) return this

	val generatedId = (this["objectiveTitle"] as? String).toObjectiveId()
	return if (generatedId.isEmpty()) this else this + ("objectiveId" to generatedId)
}

internal fun Map<String, Any?>.objectiveIdOr(fallback: String): String {
	val currentId = (this["objectiveId"] as? String)?.trim().orEmpty()
	if (currentId.isNotEmpty()) return currentId
	return (this["objectiveTitle"] as? String).toObjectiveId().ifEmpty { fallback }
}

private fun String?.toObjectiveId(): String {
	val suffix = this
		?.trim()
		?.lowercase()
		?.replace(NON_OBJECTIVE_ID_CHARACTERS, "-")
		?.trim('-')
		.orEmpty()
	return if (suffix.isEmpty()) "" else "skill:$suffix"
}
