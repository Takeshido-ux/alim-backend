package com.example.alim.admin

import com.example.alim.auth.IssuedToken
import com.example.alim.auth.TokenService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AdminAuthService(
	private val tokenService: TokenService,
	@Value("\${admin.auth.email}")
	private val configuredEmail: String,
	@Value("\${admin.auth.password}")
	private val configuredPassword: String,
) {
	fun login(rawEmail: String, password: String): IssuedToken {
		val email = rawEmail.trim().lowercase()
		val expectedEmail = configuredEmail.trim().lowercase()

		if (email != expectedEmail || password != configuredPassword) {
			throw InvalidAdminCredentialsException()
		}

		return tokenService.issueForAdmin(email)
	}
}

class InvalidAdminCredentialsException : RuntimeException()
