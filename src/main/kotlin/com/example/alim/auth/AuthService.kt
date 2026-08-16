package com.example.alim.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
	private val userRepository: UserRepository,
	private val passwordEncoder: PasswordEncoder,
	private val tokenService: TokenService,
	@Value("\${auth.stage-access.enabled:false}")
	private val stageAccessEnabled: Boolean,
	@Value("\${auth.stage-access.pin:}")
	private val stageAccessPin: String,
) {
	fun register(rawPhoneNumber: String, pin: String): String {
		requireDevAccessOrSms()
		requireStageAccessPin(pin)

		val phoneNumber = normalizePhoneNumber(rawPhoneNumber)
		val user = newParentAccount(phoneNumber, pin)

		if (!userRepository.saveIfAbsent(user)) {
			throw PhoneNumberAlreadyRegisteredException()
		}

		return phoneNumber
	}

	fun login(rawPhoneNumber: String, pin: String): IssuedToken {
		requireDevAccessOrSms()
		requireStageAccessPin(pin)

		val phoneNumber = normalizePhoneNumber(rawPhoneNumber)
		val user = userRepository.findByPhoneNumber(phoneNumber)

		if (user == null) {
			userRepository.saveIfAbsent(newParentAccount(phoneNumber, pin))
		}

		return tokenService.issue(phoneNumber)
	}

	private fun newParentAccount(phoneNumber: String, pin: String): UserAccount =
		UserAccount(
			id = UUID.randomUUID().toString(),
			phoneNumber = phoneNumber,
			pinHash = checkNotNull(passwordEncoder.encode(pin)),
			createdAt = Instant.now(),
		)

	private fun requireDevAccessOrSms() {
		if (!stageAccessEnabled) {
			throw SmsVerificationRequiredException()
		}
	}

	private fun requireStageAccessPin(pin: String) {
		if (pin != stageAccessPin) {
			throw InvalidCredentialsException()
		}
	}

	private fun normalizePhoneNumber(rawPhoneNumber: String): String {
		val normalized = rawPhoneNumber.replace(IGNORED_PHONE_CHARACTERS, "")
		if (!E164_PHONE_NUMBER.matches(normalized)) {
			throw InvalidPhoneNumberException()
		}
		return normalized
	}

	private companion object {
		val IGNORED_PHONE_CHARACTERS = Regex("[\\s()-]")
		val E164_PHONE_NUMBER = Regex("^\\+[1-9]\\d{7,14}$")
	}
}

class PhoneNumberAlreadyRegisteredException : RuntimeException()

class InvalidCredentialsException : RuntimeException()

class InvalidPhoneNumberException : RuntimeException()

class SmsVerificationRequiredException : RuntimeException()
