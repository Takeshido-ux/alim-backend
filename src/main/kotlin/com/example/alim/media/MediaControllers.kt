package com.example.alim.media

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

data class MediaResponse(
	val id: String,
	val originalFilename: String,
	val contentType: String,
	val sizeBytes: Long,
	val url: String,
	val createdAt: String,
)

data class MediaListResponse(
	val items: List<MediaResponse>,
)

@RestController
@RequestMapping("/api/admin/media")
class AdminMediaController(
	private val mediaService: MediaService,
) {
	@GetMapping
	fun list(): MediaListResponse =
		MediaListResponse(items = mediaService.list().map { it.toResponse(mediaService) })

	@PostMapping
	fun upload(
		@RequestParam("file") file: MultipartFile,
	): ResponseEntity<MediaResponse> {
		val asset = mediaService.upload(file)
		return ResponseEntity.status(HttpStatus.CREATED).body(asset.toResponse(mediaService))
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(@PathVariable id: String) {
		mediaService.delete(id)
	}
}

@RestController
@RequestMapping("/api/media")
class MediaController(
	private val mediaService: MediaService,
) {
	@GetMapping("/{id}")
	fun download(
		@PathVariable id: String,
		@RequestHeader headers: HttpHeaders,
	): ResponseEntity<ResourceRegion> {
		val media = mediaService.resolve(id)
		val resource = FileSystemResource(media.path)
		val contentLength = media.sizeBytes
		val ranges = headers.range

		if (ranges.isNullOrEmpty()) {
			return mediaResponse(
				status = HttpStatus.OK,
				contentType = media.contentType,
				originalFilename = media.originalFilename,
				body = ResourceRegion(resource, 0, contentLength),
			)
		}

		val range = ranges.first()
		val start = range.getRangeStart(contentLength)
		if (start >= contentLength) {
			return ResponseEntity
				.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
				.header(HttpHeaders.ACCEPT_RANGES, "bytes")
				.header(HttpHeaders.CONTENT_RANGE, "bytes */$contentLength")
				.build()
		}

		// Honor open-ended ranges through EOF. ExoPlayer/AVPlayer send bytes=N-
		// and keep reading until buffered; capping chunks breaks progressive playback.
		val end = range.getRangeEnd(contentLength)
		val rangeLength = end - start + 1
		return mediaResponse(
			status = HttpStatus.PARTIAL_CONTENT,
			contentType = media.contentType,
			originalFilename = media.originalFilename,
			body = ResourceRegion(resource, start, rangeLength),
		)
	}

	private fun mediaResponse(
		status: HttpStatus,
		contentType: String,
		originalFilename: String,
		body: ResourceRegion,
	): ResponseEntity<ResourceRegion> =
		ResponseEntity.status(status)
			.contentType(MediaType.parseMediaType(contentType))
			.header(HttpHeaders.ACCEPT_RANGES, "bytes")
			.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$originalFilename\"")
			.cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePublic())
			.body(body)
}

private fun MediaAsset.toResponse(mediaService: MediaService) =
	MediaResponse(
		id = id,
		originalFilename = originalFilename,
		contentType = contentType,
		sizeBytes = sizeBytes,
		url = mediaService.publicUrl(this),
		createdAt = createdAt.toString(),
	)
