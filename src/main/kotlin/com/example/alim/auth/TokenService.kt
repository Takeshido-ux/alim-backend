package com.example.alim.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

data class IssuedToken(
	val value: String,
	val expiresInSeconds: Long,
)

@Service
class TokenService(
	private val jwtEncoder: JwtEncoder,
	@Value("\${security.jwt.expiration:PT1H}")
	private val expiration: Duration,
) {
	fun issue(phoneNumber: String): IssuedToken =
		issueToken(subject = phoneNumber, role = ROLE_USER)

	fun issueForAdmin(email: String): IssuedToken =
		issueToken(subject = email, role = ROLE_ADMIN)

	private fun issueToken(subject: String, role: String): IssuedToken {
		val issuedAt = Instant.now()
		val expiresAt = issuedAt.plus(expiration)
		val claims = JwtClaimsSet.builder()
			.issuer("phone-auth-api")
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.subject(subject)
			.claim("role", role)
			.build()
		val header = JwsHeader.with(MacAlgorithm.HS256).build()
		val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue

		return IssuedToken(
			value = token,
			expiresInSeconds = expiration.seconds,
		)
	}

	companion object {
		const val ROLE_USER = "USER"
		const val ROLE_ADMIN = "ADMIN"
	}
}
