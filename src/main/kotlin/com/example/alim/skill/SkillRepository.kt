package com.example.alim.skill

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface SkillRepository {
	fun findAll(): List<Skill>
	fun findById(id: String): Skill?
	fun save(skill: Skill): Skill
	fun deleteById(id: String): Boolean
	fun deleteByIds(ids: Collection<String>): Int
}

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "memory", matchIfMissing = true)
class InMemorySkillRepository : SkillRepository {
	private val skills = ConcurrentHashMap<String, Skill>()

	override fun findAll(): List<Skill> = skills.values.sortedBy(Skill::title)

	override fun findById(id: String): Skill? = skills[id]

	override fun save(skill: Skill): Skill {
		skills[skill.id] = skill
		return skill
	}

	override fun deleteById(id: String): Boolean = skills.remove(id) != null

	override fun deleteByIds(ids: Collection<String>): Int =
		ids.count(::deleteById)
}
