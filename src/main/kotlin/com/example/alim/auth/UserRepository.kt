package com.example.alim.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface UserRepository {
	fun findByPhoneNumber(phoneNumber: String): UserAccount?

	fun findById(id: String): UserAccount?

	fun findAll(): List<UserAccount>

	fun saveIfAbsent(user: UserAccount): Boolean

	fun update(user: UserAccount)
}

@Repository
@ConditionalOnProperty(name = ["app.persistence"], havingValue = "memory", matchIfMissing = true)
class InMemoryUserRepository : UserRepository {
	private val usersByPhone = ConcurrentHashMap<String, UserAccount>()
	private val usersById = ConcurrentHashMap<String, UserAccount>()

	override fun findByPhoneNumber(phoneNumber: String): UserAccount? = usersByPhone[phoneNumber]

	override fun findById(id: String): UserAccount? = usersById[id]

	override fun findAll(): List<UserAccount> =
		usersById.values.sortedByDescending { it.createdAt }

	override fun saveIfAbsent(user: UserAccount): Boolean {
		val previous = usersByPhone.putIfAbsent(user.phoneNumber, user)
		if (previous != null) {
			return false
		}
		usersById[user.id] = user
		return true
	}

	override fun update(user: UserAccount) {
		val existing = usersByPhone[user.phoneNumber]
			?: error("Parent account not found: ${user.phoneNumber}")
		usersByPhone[user.phoneNumber] = user
		usersById.remove(existing.id)
		usersById[user.id] = user
	}
}
