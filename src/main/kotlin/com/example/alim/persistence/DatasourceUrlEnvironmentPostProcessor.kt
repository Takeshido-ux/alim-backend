package com.example.alim.persistence

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import java.net.URI

/**
 * Railway provides either:
 * - PGHOST / PGPORT / PGDATABASE / PGUSER / PGPASSWORD
 * - or DATABASE_URL = postgres(ql)://user:pass@host:port/db
 *
 * Spring needs jdbc:postgresql://host:port/db with separate username/password.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class DatasourceUrlEnvironmentPostProcessor : EnvironmentPostProcessor {
	override fun postProcessEnvironment(
		environment: ConfigurableEnvironment,
		application: SpringApplication,
	) {
		val overrides = linkedMapOf<String, Any>()

		val pgHost = environment.getProperty("PGHOST")?.trim().orEmpty()
		val pgPort = environment.getProperty("PGPORT")?.trim().orEmpty().ifBlank { "5432" }
		val pgDatabase = environment.getProperty("PGDATABASE")?.trim().orEmpty()
		val pgUser = environment.getProperty("PGUSER")?.trim().orEmpty()
		val pgPassword = environment.getProperty("PGPASSWORD")?.trim().orEmpty()

		if (pgHost.isNotBlank() && pgDatabase.isNotBlank()) {
			overrides["spring.datasource.url"] = "jdbc:postgresql://$pgHost:$pgPort/$pgDatabase"
			if (pgUser.isNotBlank()) {
				overrides["spring.datasource.username"] = pgUser
			}
			if (pgPassword.isNotBlank()) {
				overrides["spring.datasource.password"] = pgPassword
			}
		} else {
			val raw = listOf(
				environment.getProperty("DATABASE_URL"),
				environment.getProperty("SPRING_DATASOURCE_URL"),
				environment.getProperty("JDBC_DATABASE_URL"),
			).firstOrNull { !it.isNullOrBlank() }?.trim()

			val parsed = raw?.let(::parseDatabaseUrl)
			if (parsed != null) {
				overrides["spring.datasource.url"] = parsed.jdbcUrl
				if (!parsed.username.isNullOrBlank()) {
					overrides["spring.datasource.username"] = parsed.username
				}
				if (!parsed.password.isNullOrBlank()) {
					overrides["spring.datasource.password"] = parsed.password
				}
			}
		}

		if (overrides.isEmpty()) {
			return
		}
		environment.propertySources.addFirst(MapPropertySource("databaseUrlBridge", overrides))
	}

	private fun parseDatabaseUrl(raw: String): ParsedDatabaseUrl? {
		val normalized = when {
			raw.startsWith("jdbc:postgresql://") -> raw.removePrefix("jdbc:")
			raw.startsWith("jdbc:postgres://") -> "postgresql://" + raw.removePrefix("jdbc:postgres://")
			raw.startsWith("postgres://") -> "postgresql://" + raw.removePrefix("postgres://")
			raw.startsWith("postgresql://") -> raw
			else -> return null
		}

		val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
		val host = uri.host ?: return null
		val port = if (uri.port > 0) uri.port else 5432
		val database = uri.path?.trimStart('/')?.substringBefore('?')?.ifBlank { null } ?: return null
		val userInfo = uri.userInfo
		val username = userInfo?.substringBefore(':', missingDelimiterValue = userInfo)?.ifBlank { null }
		val password = userInfo
			?.takeIf { it.contains(':') }
			?.substringAfter(':')
			?.ifBlank { null }

		return ParsedDatabaseUrl(
			jdbcUrl = "jdbc:postgresql://$host:$port/$database",
			username = username,
			password = password,
		)
	}

	private data class ParsedDatabaseUrl(
		val jdbcUrl: String,
		val username: String?,
		val password: String?,
	)
}
