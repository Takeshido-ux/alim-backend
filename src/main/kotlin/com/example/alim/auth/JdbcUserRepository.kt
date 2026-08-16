package com.example.alim.auth

import com.example.alim.parent.FamilyPreferences
import com.example.alim.persistence.toTimestamp
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "jdbc")
class JdbcUserRepository(
	private val jdbc: JdbcTemplate,
) : UserRepository {
	private val mapper = RowMapper { rs, _ ->
		UserAccount(
			id = rs.getString("id"),
			phoneNumber = rs.getString("phone_number"),
			pinHash = rs.getString("pin_hash"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			activeChildId = rs.getString("active_child_id"),
			preferences = FamilyPreferences(
				uiLanguage = rs.getString("ui_language"),
				voiceLanguage = rs.getString("voice_language"),
				remindersEnabled = rs.getBoolean("reminders_enabled"),
				dailyLessonGoal = rs.getInt("daily_lesson_goal"),
			),
		)
	}

	override fun findByPhoneNumber(phoneNumber: String): UserAccount? =
		jdbc.query(
			"""
			SELECT id, phone_number, pin_hash, created_at, active_child_id,
			       ui_language, voice_language, reminders_enabled, daily_lesson_goal
			FROM parent_accounts
			WHERE phone_number = ?
			""".trimIndent(),
			mapper,
			phoneNumber,
		).firstOrNull()

	override fun findById(id: String): UserAccount? =
		jdbc.query(
			"""
			SELECT id, phone_number, pin_hash, created_at, active_child_id,
			       ui_language, voice_language, reminders_enabled, daily_lesson_goal
			FROM parent_accounts
			WHERE id = ?
			""".trimIndent(),
			mapper,
			id,
		).firstOrNull()

	override fun findAll(): List<UserAccount> =
		jdbc.query(
			"""
			SELECT id, phone_number, pin_hash, created_at, active_child_id,
			       ui_language, voice_language, reminders_enabled, daily_lesson_goal
			FROM parent_accounts
			ORDER BY created_at DESC
			""".trimIndent(),
			mapper,
		)

	override fun saveIfAbsent(user: UserAccount): Boolean {
		val updated = jdbc.update(
			"""
			INSERT INTO parent_accounts (
				id, phone_number, pin_hash, created_at, active_child_id,
				ui_language, voice_language, reminders_enabled, daily_lesson_goal
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (phone_number) DO NOTHING
			""".trimIndent(),
			user.id,
			user.phoneNumber,
			user.pinHash,
			user.createdAt.toTimestamp(),
			user.activeChildId,
			user.preferences.uiLanguage,
			user.preferences.voiceLanguage,
			user.preferences.remindersEnabled,
			user.preferences.dailyLessonGoal,
		)
		return updated > 0
	}

	override fun update(user: UserAccount) {
		val updated = jdbc.update(
			"""
			UPDATE parent_accounts
			SET phone_number = ?,
			    pin_hash = ?,
			    active_child_id = ?,
			    ui_language = ?,
			    voice_language = ?,
			    reminders_enabled = ?,
			    daily_lesson_goal = ?
			WHERE id = ?
			""".trimIndent(),
			user.phoneNumber,
			user.pinHash,
			user.activeChildId,
			user.preferences.uiLanguage,
			user.preferences.voiceLanguage,
			user.preferences.remindersEnabled,
			user.preferences.dailyLessonGoal,
			user.id,
		)
		check(updated > 0) { "Parent account not found: ${user.id}" }
	}
}
