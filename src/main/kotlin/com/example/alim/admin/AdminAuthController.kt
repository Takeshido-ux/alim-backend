package com.example.alim.admin

import com.example.alim.auth.TokenResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class AdminAuthRequest(
	@field:NotBlank(message = "Email is required")
	@field:Email(message = "Email must be valid")
	val email: String,
	@field:NotBlank(message = "Password is required")
	val password: String,
)

@RestController
@RequestMapping("/api/admin/auth")
class AdminAuthController(
	private val adminAuthService: AdminAuthService,
) {
	@PostMapping("/login")
	fun login(
		@Valid @RequestBody request: AdminAuthRequest,
	): TokenResponse {
		val token = adminAuthService.login(request.email, request.password)
		return TokenResponse(
			accessToken = token.value,
			expiresIn = token.expiresInSeconds,
		)
	}
}
