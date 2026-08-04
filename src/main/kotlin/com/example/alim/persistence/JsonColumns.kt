package com.example.alim.persistence

import org.postgresql.util.PGobject
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

@Component
class JsonColumns(
	private val objectMapper: ObjectMapper,
) {
	fun toJsonb(value: Any): PGobject =
		PGobject().apply {
			type = "jsonb"
			this.value = objectMapper.writeValueAsString(value)
		}

	fun <T> fromJson(json: String?, type: TypeReference<T>, default: T): T {
		if (json.isNullOrBlank()) {
			return default
		}
		return objectMapper.readValue(json, type)
	}

	fun stringList(json: String?): List<String> =
		fromJson(json, object : TypeReference<List<String>>() {}, emptyList())

	fun stringMap(json: String?): Map<String, String> =
		fromJson(json, object : TypeReference<Map<String, String>>() {}, emptyMap())

	fun lessonSteps(json: String?): List<com.example.alim.lesson.LessonStep> =
		fromJson(json, object : TypeReference<List<com.example.alim.lesson.LessonStep>>() {}, emptyList())
}
