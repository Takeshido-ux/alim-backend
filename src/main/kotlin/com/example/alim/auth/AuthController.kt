package com.example.alim.auth

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class AuthRequest(
	@field:NotBlank(message = "Phone number is required")
	val phoneNumber: String,
	@field:Pattern(regexp = "^\\d{4,6}$", message = "PIN must contain 4 to 6 digits")
	val pin: String,
)

data class RegistrationResponse(
	val phoneNumber: String,
)

data class TokenResponse(
	val accessToken: String,
	val tokenType: String = "Bearer",
	val expiresIn: Long,
)

@RestController
@RequestMapping("/api/auth")
class AuthController(
	private val authService: AuthService,
) {
	@PostMapping("/register")
	fun register(
		@Valid @RequestBody request: AuthRequest,
	): ResponseEntity<RegistrationResponse> {
		val phoneNumber = authService.register(request.phoneNumber, request.pin)
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(RegistrationResponse(phoneNumber))
	}

	@PostMapping("/login")
	fun login(
		@Valid @RequestBody request: AuthRequest,
	): TokenResponse {
		val token = authService.login(request.phoneNumber, request.pin)
		return TokenResponse(
			accessToken = token.value,
			expiresIn = token.expiresInSeconds,
		)
	}
}
