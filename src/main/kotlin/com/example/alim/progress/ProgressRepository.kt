package com.example.alim.progress

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface ProgressRepository {
	fun <T> withChildLock(childId: String, action: () -> T): T

	fun findByChildId(childId: String): List<LessonProgress>

	fun find(childId: String, lessonId: String): LessonProgress?

	fun save(progress: LessonProgress): LessonProgress

	fun findWallet(childId: String): RewardWallet?

	fun saveWallet(wallet: RewardWallet): RewardWallet

	fun findSkillsByChildId(childId: String): List<SkillProgress>

	fun findSkill(childId: String, skillId: String): SkillProgress?

	fun saveSkill(progress: SkillProgress): SkillProgress

	fun deleteByChildId(childId: String)
}

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "memory", matchIfMissing = true)
class InMemoryProgressRepository : ProgressRepository {
	private val progress = ConcurrentHashMap<String, LessonProgress>()
	private val wallets = ConcurrentHashMap<String, RewardWallet>()
	private val skills = ConcurrentHashMap<String, SkillProgress>()
	private val childLocks = ConcurrentHashMap<String, Any>()

	private fun key(childId: String, lessonId: String) = "$childId::$lessonId"

	override fun <T> withChildLock(childId: String, action: () -> T): T =
		synchronized(childLocks.computeIfAbsent(childId) { Any() }) {
			action()
		}

	override fun findByChildId(childId: String): List<LessonProgress> =
		progress.values.filter { it.childId == childId }

	override fun find(childId: String, lessonId: String): LessonProgress? =
		progress[key(childId, lessonId)]

	override fun save(progress: LessonProgress): LessonProgress {
		this.progress[key(progress.childId, progress.lessonId)] = progress
		return progress
	}

	override fun findWallet(childId: String): RewardWallet? = wallets[childId]

	override fun saveWallet(wallet: RewardWallet): RewardWallet {
		wallets[wallet.childId] = wallet
		return wallet
	}

	override fun findSkillsByChildId(childId: String): List<SkillProgress> =
		skills.values.filter { it.childId == childId }

	override fun findSkill(childId: String, skillId: String): SkillProgress? =
		skills["$childId::$skillId"]

	override fun saveSkill(progress: SkillProgress): SkillProgress {
		skills["${progress.childId}::${progress.skillId}"] = progress
		return progress
	}

	override fun deleteByChildId(childId: String) {
		progress.entries.removeIf { (_, item) -> item.childId == childId }
		wallets.remove(childId)
		skills.entries.removeIf { (_, item) -> item.childId == childId }
		childLocks.remove(childId)
	}
}
