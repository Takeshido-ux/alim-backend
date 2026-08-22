package com.example.alim.auth

import com.example.alim.admin.InvalidAdminCredentialsException
import com.example.alim.admin.AdminUserNotFoundException
import com.example.alim.admin.InvalidAdminCatalogException
import com.example.alim.cartoon.CartoonNotFoundException
import com.example.alim.cartoon.CartoonTagNotFoundException
import com.example.alim.cartoon.InvalidCartoonDataException
import com.example.alim.child.ChildNotFoundException
import com.example.alim.child.InvalidChildDataException
import com.example.alim.lesson.InvalidLessonDataException
import com.example.alim.lesson.LessonNotFoundException
import com.example.alim.lesson.LessonSlugAlreadyExistsException
import com.example.alim.parent.InvalidPreferencesException
import com.example.alim.parent.ParentSessionRequiredException
import com.example.alim.media.InvalidMediaException
import com.example.alim.media.MediaNotFoundException
import com.example.alim.progress.InvalidProgressDataException
import com.example.alim.progress.LessonLockedException
import com.example.alim.sticker.InvalidStickerDataException
import com.example.alim.sticker.StickerNotFoundException
import com.example.alim.sticker.StickerSlugAlreadyExistsException
import com.example.alim.track.InvalidTrackDataException
import com.example.alim.track.TrackNotFoundException
import com.example.alim.track.TrackSlugAlreadyExistsException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ApiError(
	val code: String,
	val message: String,
)

@RestControllerAdvice
class RestExceptionHandler {
	@ExceptionHandler(PhoneNumberAlreadyRegisteredException::class)
	fun handlePhoneNumberAlreadyRegistered(): ResponseEntity<ApiError> =
		error(HttpStatus.CONFLICT, "phone_number_already_registered", "Phone number is already registered")

	@ExceptionHandler(InvalidCredentialsException::class)
	fun handleInvalidCredentials(): ResponseEntity<ApiError> =
		error(HttpStatus.UNAUTHORIZED, "invalid_code", "Неверный код")

	@ExceptionHandler(InvalidAdminCredentialsException::class)
	fun handleInvalidAdminCredentials(): ResponseEntity<ApiError> =
		error(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid email or password")

	@ExceptionHandler(AdminUserNotFoundException::class)
	fun handleAdminUserNotFound(): ResponseEntity<ApiError> =
		error(HttpStatus.NOT_FOUND, "user_not_found", "User was not found")

	@ExceptionHandler(InvalidAdminCatalogException::class)
	fun handleInvalidAdminCatalog(exception: InvalidAdminCatalogException): ResponseEntity<ApiError> =
		error(HttpStatus.BAD_REQUEST, "invalid_admin_catalog", exception.message)

	@ExceptionHandler(InvalidPhoneNumberException::class)
	fun handleInvalidPhoneNumber(): ResponseEntity<ApiError> =
		error(
			HttpStatus.BAD_REQUEST,
			"invalid_phone_number",
			"Phone number must use international format, for example +15551234567",
		)

	@ExceptionHandler(SmsVerificationRequiredException::class)
	fun handleSmsVerificationRequired(): ResponseEntity<ApiError> =
		error(
			HttpStatus.FORBIDDEN,
			"sms_verification_required",
			"Authentication requires an SMS verification code",
		)

	@ExceptionHandler(ChildNotFoundException::class)
	fun handleChildNotFound(): ResponseEntity<ApiError> =
		error(HttpStatus.NOT_FOUND, "child_not_found", "Child profile was not found")

	@ExceptionHandler(InvalidChildDataException::class)
	fun handleInvalidChildData(exception: InvalidChildDataException): ResponseEntity<ApiError> =
		error(HttpStatus.BAD_REQUEST, "invalid_child_data", exception.message)

	@ExceptionHandler(InvalidPreferencesException::class)
	fun handleInvalidPreferences(exception: InvalidPreferencesException): ResponseEntity<ApiError> =
		error(HttpStatus.BAD_REQUEST, "invalid_preferences", exception.message)

	@ExceptionHandler(ParentSessionRequiredException::class)
	fun handleParentSessionRequired(): ResponseEntity<ApiError> =
		error(HttpStatus.UNAUTHORIZED, "unauthorized", "A valid parent Bearer token is required")

	@ExceptionHandler(LessonNotFoundException::class)
	fun handleLessonNotFound(): ResponseEntity<ApiError> =
		error(HttpStatus.NOT_FOUND, "lesson_not_found", "Lesson was not found")

	@ExceptionHandler(LessonSlugAlreadyExistsException::class)
	fun handleLessonSlugAlreadyExists(): ResponseEntity<ApiError> =
		error(HttpStatus.CONFLICT, "lesson_slug_already_exists", "Lesson slug is already used")

	@ExceptionHandler(InvalidLessonDataException::class)
	fun handleInvalidLessonData(exception: InvalidLessonDataException): ResponseEntity<ApiError> =
		error(HttpStatus.BAD_REQUEST, "invalid_lesson_data", exception.message)

	@ExceptionHandler(CartoonNotFoundException::class)
	fun handleCartoonNotFound(): ResponseEntity<ApiError> =
		error(HttpStatus.NOT_FOUND, "cartoon_not_found", "Cartoon was not found")

	@ExceptionHandler(CartoonTagNotFoundException::class)
	fun handleCartoonTagNotFound(): ResponseEntity<ApiError> =
		error(HttpStatus.NOT_FOUND, "cartoon_tag_not_found", "Cartoon tag was not found")

	@ExceptionHandler(InvalidCartoonDataException::class)
	fun handleInvalidCartoonData(exception: InvalidCartoonDataException): ResponseEntity<ApiError> =
		error(HttpStatus.BAD_REQUEST, "invalid_cartoon_data", exception.message)

	@ExceptionHandler(TrackNotFoundException::class)
	fun handleTrackNotFound(): ResponseEntity<ApiError> =
		error(HttpStatus.NOT_FOUND, "track_not_found", "Track was not found")

	@ExceptionHandler(TrackSlugAlreadyExistsException::class)
	fun handleTrackSlugAlreadyExists(): ResponseEntity<ApiError> =
		error(HttpStatus.CONFLICT, "track_slug_already_exists", "Track slug is already used")

	@ExceptionHandler(InvalidTrackDataException::class)
	fun handleInvalidTrackData(exception: InvalidTrackDataException): ResponseEntity<ApiError> =
		error(HttpStatus.BAD_REQUEST, "invalid_track_data", exception.message)

	@ExceptionHandler(StickerNotFoundException::class)
	fun handleStickerNotFound(): ResponseEntity<ApiError> =
		error(HttpStatus.NOT_FOUND, "sticker_not_found", "Sticker was not found")

	@ExceptionHandler(StickerSlugAlreadyExistsException::class)
	fun handleStickerSlugAlreadyExists(): ResponseEntity<ApiError> =
		error(HttpStatus.CONFLICT, "sticker_slug_already_exists", "Sticker slug is already used")

	@ExceptionHandler(InvalidStickerDataException::class)
	fun handleInvalidStickerData(exception: InvalidStickerDataException): ResponseEntity<ApiError> =
		error(HttpStatus.BAD_REQUEST, "invalid_sticker_data", exception.message)

	@ExceptionHandler(LessonLockedException::class)
	fun handleLessonLocked(): ResponseEntity<ApiError> =
		error(HttpStatus.CONFLICT, "lesson_locked", "Lesson is locked for this child")

	@ExceptionHandler(InvalidProgressDataException::class)
	fun handleInvalidProgress(exception: InvalidProgressDataException): ResponseEntity<ApiError> =
		error(HttpStatus.BAD_REQUEST, "invalid_progress_data", exception.message)

	@ExceptionHandler(MediaNotFoundException::class)
	fun handleMediaNotFound(): ResponseEntity<ApiError> =
		error(HttpStatus.NOT_FOUND, "media_not_found", "Media file was not found")

	@ExceptionHandler(InvalidMediaException::class)
	fun handleInvalidMedia(exception: InvalidMediaException): ResponseEntity<ApiError> =
		error(HttpStatus.BAD_REQUEST, "invalid_media", exception.message)

	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<ApiError> {
		val message = exception.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
			?: "Request validation failed"
		return error(HttpStatus.BAD_REQUEST, "validation_error", message)
	}

	@ExceptionHandler(HttpMessageNotReadableException::class)
	fun handleUnreadableMessage(): ResponseEntity<ApiError> =
		error(HttpStatus.BAD_REQUEST, "invalid_request", "Request body is invalid")

	private fun error(
		status: HttpStatus,
		code: String,
		message: String,
	): ResponseEntity<ApiError> =
		ResponseEntity.status(status).body(ApiError(code, message))
}
