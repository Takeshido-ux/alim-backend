package com.example.alim.persistence

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * Converts Railway/Heroku DATABASE_URL (postgres:// or postgresql://)
 * into spring.datasource.url (jdbc:postgresql://...).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class DatasourceUrlEnvironmentPostProcessor : EnvironmentPostProcessor {
	override fun postProcessEnvironment(
		environment: ConfigurableEnvironment,
		application: SpringApplication,
	) {
		val candidates = listOfNotNull(
			environment.getProperty("SPRING_DATASOURCE_URL"),
			environment.getProperty("JDBC_DATABASE_URL"),
			environment.getProperty("spring.datasource.url"),
			environment.getProperty("DATABASE_URL"),
		)
		val raw = candidates.firstOrNull { it.isNotBlank() }?.trim() ?: return
		val jdbcUrl = toJdbcUrl(raw) ?: return
		if (jdbcUrl == environment.getProperty("spring.datasource.url")) {
			return
		}
		environment.propertySources.addFirst(
			MapPropertySource(
				"databaseUrlBridge",
				mapOf("spring.datasource.url" to jdbcUrl),
			),
		)
	}

	private fun toJdbcUrl(raw: String): String? =
		when {
			raw.startsWith("jdbc:postgresql://") -> raw
			raw.startsWith("jdbc:postgres://") ->
				raw.replaceFirst("jdbc:postgres://", "jdbc:postgresql://")
			raw.startsWith("postgresql://") -> "jdbc:$raw"
			raw.startsWith("postgres://") ->
				"jdbc:postgresql://${raw.removePrefix("postgres://")}"
			else -> null
		}
}
