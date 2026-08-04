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
			SELECT child_id, total_stars, last_granted_sticker_id
			FROM reward_wallets
			WHERE child_id = ?
			""".trimIndent(),
			{ rs, _ ->
				Triple(
					rs.getString("child_id"),
					rs.getInt("total_stars"),
					rs.getString("last_granted_sticker_id"),
				)
			},
			childId,
		).firstOrNull() ?: return null

		val stickerIds = jdbc.query(
			"""
			SELECT sticker_id
			FROM reward_wallet_stickers
			WHERE child_id = ?
			""".trimIndent(),
			{ rs, _ -> rs.getString("sticker_id") },
			childId,
		).toSet()

		return RewardWallet(
			childId = wallet.first,
			totalStars = wallet.second,
			stickerIds = stickerIds,
			lastGrantedStickerId = wallet.third,
		)
	}

	@Transactional
	override fun saveWallet(wallet: RewardWallet): RewardWallet {
		jdbc.update(
			"""
			INSERT INTO reward_wallets (child_id, total_stars, last_granted_sticker_id)
			VALUES (?, ?, ?)
			ON CONFLICT (child_id) DO UPDATE SET
				total_stars = EXCLUDED.total_stars,
				last_granted_sticker_id = EXCLUDED.last_granted_sticker_id
			""".trimIndent(),
			wallet.childId,
			wallet.totalStars,
			wallet.lastGrantedStickerId,
		)
		jdbc.update("DELETE FROM reward_wallet_stickers WHERE child_id = ?", wallet.childId)
		wallet.stickerIds.forEach { stickerId ->
			jdbc.update(
				"""
				INSERT INTO reward_wallet_stickers (child_id, sticker_id)
				VALUES (?, ?)
				ON CONFLICT DO NOTHING
				""".trimIndent(),
				wallet.childId,
				stickerId,
			)
		}
		return wallet
	}
}
