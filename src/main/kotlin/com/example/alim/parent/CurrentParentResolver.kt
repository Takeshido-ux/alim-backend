package com.example.alim.parent

import com.example.alim.auth.UserAccount
import com.example.alim.auth.UserRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class CurrentParentResolver(
	private val userRepository: UserRepository,
) {
	fun requireParent(): UserAccount {
		val authentication = SecurityContextHolder.getContext().authentication
			?: throw ParentSessionRequiredException()
		val principal = authentication.principal
		if (principal !is Jwt) {
			throw ParentSessionRequiredException()
		}
		val phoneNumber = principal.subject ?: throw ParentSessionRequiredException()
		return userRepository.findByPhoneNumber(phoneNumber)
			?: throw ParentSessionRequiredException()
	}
}

class ParentSessionRequiredException : RuntimeException()
