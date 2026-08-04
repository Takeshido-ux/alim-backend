package com.example.alim.auth

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration
class SecurityConfig {
	@Bean
	fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

	@Bean
	fun jwtSecretKey(
		@Value("\${security.jwt.secret}") secret: String,
	): SecretKey {
		require(secret.toByteArray(StandardCharsets.UTF_8).size >= 32) {
			"security.jwt.secret must contain at least 32 bytes"
		}
		return SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
	}

	@Bean
	fun jwtEncoder(secretKey: SecretKey): JwtEncoder =
		NimbusJwtEncoder(ImmutableSecret<SecurityContext>(secretKey))

	@Bean
	fun jwtDecoder(secretKey: SecretKey): JwtDecoder =
		NimbusJwtDecoder.withSecretKey(secretKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build()

	@Bean
	fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
		val authoritiesConverter = JwtGrantedAuthoritiesConverter().apply {
			setAuthoritiesClaimName("role")
			setAuthorityPrefix("ROLE_")
		}
		return JwtAuthenticationConverter().apply {
			setJwtGrantedAuthoritiesConverter(authoritiesConverter)
		}
	}

	@Bean
	fun corsConfigurationSource(
		@Value("\${app.cors.allowed-origins:http://localhost:5173}") allowedOrigins: String,
	): CorsConfigurationSource {
		val configuration = CorsConfiguration().apply {
			this.allowedOrigins = allowedOrigins.split(',').map { it.trim() }.filter { it.isNotEmpty() }
			allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			allowedHeaders = listOf("*")
			exposedHeaders = listOf("Authorization")
			allowCredentials = true
			maxAge = 3600
		}
		return UrlBasedCorsConfigurationSource().apply {
			registerCorsConfiguration("/**", configuration)
		}
	}

	@Bean
	fun securityFilterChain(
		http: HttpSecurity,
		authenticationEntryPoint: AuthenticationEntryPoint,
		accessDeniedHandler: AccessDeniedHandler,
		jwtAuthenticationConverter: JwtAuthenticationConverter,
		corsConfigurationSource: CorsConfigurationSource,
	): SecurityFilterChain {
		http
			.cors { it.configurationSource(corsConfigurationSource) }
			.csrf { it.disable() }
			.sessionManagement {
				it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
			}
			.authorizeHttpRequests {
				it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
					.requestMatchers(
						"/api/auth/register",
						"/api/auth/login",
						"/api/admin/auth/login",
					).permitAll()
					.requestMatchers(HttpMethod.GET, "/api/media/**").permitAll()
					.requestMatchers("/api/admin/**").hasRole(TokenService.ROLE_ADMIN)
					.requestMatchers("/api/**").authenticated()
					.anyRequest().permitAll()
			}
			.oauth2ResourceServer {
				it.jwt { jwt ->
					jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
				}
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler)
			}
			.exceptionHandling {
				it.authenticationEntryPoint(authenticationEntryPoint)
					.accessDeniedHandler(accessDeniedHandler)
			}

		return http.build()
	}

	@Bean
	fun authenticationEntryPoint(): AuthenticationEntryPoint =
		AuthenticationEntryPoint { _: HttpServletRequest, response: HttpServletResponse, _ ->
			writeSecurityError(
				response = response,
				status = HttpServletResponse.SC_UNAUTHORIZED,
				code = "unauthorized",
				message = "A valid Bearer token is required",
			)
		}

	@Bean
	fun accessDeniedHandler(): AccessDeniedHandler =
		AccessDeniedHandler { _: HttpServletRequest, response: HttpServletResponse, _ ->
			writeSecurityError(
				response = response,
				status = HttpServletResponse.SC_FORBIDDEN,
				code = "access_denied",
				message = "Access is denied",
			)
		}

	private fun writeSecurityError(
		response: HttpServletResponse,
		status: Int,
		code: String,
		message: String,
	) {
		response.status = status
		response.contentType = MediaType.APPLICATION_JSON_VALUE
		response.characterEncoding = StandardCharsets.UTF_8.name()
		response.writer.write("""{"code":"$code","message":"$message"}""")
	}
}
