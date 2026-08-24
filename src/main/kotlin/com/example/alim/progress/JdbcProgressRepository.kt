package com.example.alim.progress

import com.example.alim.persistence.JsonColumns
import com.example.alim.persistence.toInstantOrNull
import com.example.alim.persistence.toTimestamp
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "jdbc")
class JdbcProgressRepository(
	private val jdbc: JdbcTemplate,
	private val json: JsonColumns,
) : ProgressRepository {
	private val progressMapper = RowMapper { rs, _ ->
		LessonProgress(
			childId = rs.getString("child_id"),
			lessonId = rs.getString("lesson_id"),
			status = LessonProgressStatus.valueOf(rs.getString("status")),
			currentStepIndex = rs.getInt("current_step_index"),
			completedStepIds = json.stringList(rs.getString("completed_step_ids")),
			attemptCount = rs.getInt("attempt_count"),
			firstTryPracticeCorrect = rs.getBoolean("first_try_practice_correct"),
			incorrectPracticeRetries = rs.getInt("incorrect_practice_retries"),
			starsEarned = rs.getInt("stars_earned"),
			startedAt = rs.getTimestamp("started_at").toInstantOrNull(),
			completedAt = rs.getTimestamp("completed_at").toInstantOrNull(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
			contentVersionAtStart = rs.getString("content_version_at_start"),
		)
	}
	private val skillMapper = RowMapper { rs, _ ->
		SkillProgress(
			childId = rs.getString("child_id"),
			objectiveId = rs.getString("objective_id"),
			objectiveTitle = rs.getString("objective_title"),
			state = SkillMasteryState.valueOf(rs.getString("state")),
			successfulAttempts = rs.getInt("successful_attempts"),
			totalAttempts = rs.getInt("total_attempts"),
			lastPracticedAt = rs.getTimestamp("last_practiced_at").toInstant(),
		)
	}

	override fun findByChildId(childId: String): List<LessonProgress> =
		jdbc.query(
			"""
			SELECT child_id, lesson_id, status, current_step_index, completed_step_ids::text,
			       attempt_count, first_try_practice_correct, incorrect_practice_retries,
			       stars_earned, started_at, completed_at, updated_at, content_version_at_start
			FROM lesson_progress
			WHERE child_id = ?
			""".trimIndent(),
			progressMapper,
			childId,
		)

	override fun find(childId: String, lessonId: String): LessonProgress? =
		jdbc.query(
			"""
			SELECT child_id, lesson_id, status, current_step_index, completed_step_ids::text,
			       attempt_count, first_try_practice_correct, incorrect_practice_retries,
			       stars_earned, started_at, completed_at, updated_at, content_version_at_start
			FROM lesson_progress
			WHERE child_id = ? AND lesson_id = ?
			""".trimIndent(),
			progressMapper,
			childId,
			lessonId,
		).firstOrNull()

	override fun save(progress: LessonProgress): LessonProgress {
		jdbc.update(
			"""
			INSERT INTO lesson_progress (
				child_id, lesson_id, status, current_step_index, completed_step_ids,
				attempt_count, first_try_practice_correct, incorrect_practice_retries,
				stars_earned, started_at, completed_at, updated_at, content_version_at_start
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (child_id, lesson_id) DO UPDATE SET
				status = EXCLUDED.status,
				current_step_index = EXCLUDED.current_step_index,
				completed_step_ids = EXCLUDED.completed_step_ids,
				attempt_count = EXCLUDED.attempt_count,
				first_try_practice_correct = EXCLUDED.first_try_practice_correct,
				incorrect_practice_retries = EXCLUDED.incorrect_practice_retries,
				stars_earned = EXCLUDED.stars_earned,
				started_at = EXCLUDED.started_at,
				completed_at = EXCLUDED.completed_at,
				updated_at = EXCLUDED.updated_at,
				content_version_at_start = EXCLUDED.content_version_at_start
			""".trimIndent(),
			progress.childId,
			progress.lessonId,
			progress.status.name,
			progress.currentStepIndex,
			json.toJsonb(progress.completedStepIds),
			progress.attemptCount,
			progress.firstTryPracticeCorrect,
			progress.incorrectPracticeRetries,
			progress.starsEarned,
			progress.startedAt?.toTimestamp(),
			progress.completedAt?.toTimestamp(),
			progress.updatedAt.toTimestamp(),
			progress.contentVersionAtStart,
		)
		return progress
	}

	override fun findWallet(childId: String): RewardWallet? {
		val wallet = jdbc.query(
			"""
			SELECT child_id, total_stars, last_granted_achievement_id
			FROM reward_wallets
			WHERE child_id = ?
			""".trimIndent(),
			{ rs, _ ->
				Triple(
					rs.getString("child_id"),
					rs.getInt("total_stars"),
					rs.getString("last_granted_achievement_id"),
				)
			},
			childId,
		).firstOrNull() ?: return null

		val unlockedAchievements = jdbc.query(
			"""
			SELECT achievement_id, unlocked_at
			FROM child_achievements
			WHERE child_id = ?
			""".trimIndent(),
			{ rs, _ -> rs.getString("achievement_id") to rs.getTimestamp("unlocked_at").toInstant() },
			childId,
		).toMap()

		return RewardWallet(
			childId = wallet.first,
			totalStars = wallet.second,
			achievementIds = unlockedAchievements.keys,
			achievementUnlockedAt = unlockedAchievements,
			lastGrantedAchievementId = wallet.third,
		)
	}

	@Transactional
	override fun saveWallet(wallet: RewardWallet): RewardWallet {
		jdbc.update(
			"""
			INSERT INTO reward_wallets (child_id, total_stars, last_granted_achievement_id)
			VALUES (?, ?, ?)
			ON CONFLICT (child_id) DO UPDATE SET
				total_stars = EXCLUDED.total_stars,
				last_granted_achievement_id = EXCLUDED.last_granted_achievement_id
			""".trimIndent(),
			wallet.childId,
			wallet.totalStars,
			wallet.lastGrantedAchievementId,
		)
		jdbc.update("DELETE FROM child_achievements WHERE child_id = ?", wallet.childId)
		wallet.achievementIds.forEach { achievementId ->
			jdbc.update(
				"""
				INSERT INTO child_achievements (child_id, achievement_id, unlocked_at)
				VALUES (?, ?, ?)
				ON CONFLICT DO NOTHING
				""".trimIndent(),
				wallet.childId,
				achievementId,
				(wallet.achievementUnlockedAt[achievementId] ?: java.time.Instant.now()).toTimestamp(),
			)
		}
		return wallet
	}

	override fun findSkillsByChildId(childId: String): List<SkillProgress> =
		jdbc.query(
			"""
			SELECT child_id, objective_id, objective_title, state, successful_attempts,
			       total_attempts, last_practiced_at
			FROM skill_progress
			WHERE child_id = ?
			ORDER BY last_practiced_at DESC, objective_title
			""".trimIndent(),
			skillMapper,
			childId,
		)

	override fun findSkill(childId: String, objectiveId: String): SkillProgress? =
		jdbc.query(
			"""
			SELECT child_id, objective_id, objective_title, state, successful_attempts,
			       total_attempts, last_practiced_at
			FROM skill_progress
			WHERE child_id = ? AND objective_id = ?
			""".trimIndent(),
			skillMapper,
			childId,
			objectiveId,
		).firstOrNull()

	override fun saveSkill(progress: SkillProgress): SkillProgress {
		jdbc.update(
			"""
			INSERT INTO skill_progress (
				child_id, objective_id, objective_title, state, successful_attempts,
				total_attempts, last_practiced_at
			) VALUES (?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (child_id, objective_id) DO UPDATE SET
				objective_title = EXCLUDED.objective_title,
				state = EXCLUDED.state,
				successful_attempts = EXCLUDED.successful_attempts,
				total_attempts = EXCLUDED.total_attempts,
				last_practiced_at = EXCLUDED.last_practiced_at
			""".trimIndent(),
			progress.childId,
			progress.objectiveId,
			progress.objectiveTitle,
			progress.state.name,
			progress.successfulAttempts,
			progress.totalAttempts,
			progress.lastPracticedAt.toTimestamp(),
		)
		return progress
	}

	@Transactional
	override fun deleteByChildId(childId: String) {
		jdbc.update("DELETE FROM lesson_progress WHERE child_id = ?", childId)
		jdbc.update("DELETE FROM skill_progress WHERE child_id = ?", childId)
		jdbc.update("DELETE FROM reward_wallets WHERE child_id = ?", childId)
	}
}
