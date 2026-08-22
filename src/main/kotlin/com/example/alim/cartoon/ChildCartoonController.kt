package com.example.alim.cartoon

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ChildCartoonTagResponse(
	val id: String,
	val title: String,
	val icon: String,
)

data class ChildCartoonSummaryResponse(
	val id: String,
	val title: String,
	val img: String,
	val tags: List<ChildCartoonTagResponse>,
	val isFavorite: Boolean,
)

data class ChildCartoonListResponse(
	val tags: List<ChildCartoonTagResponse>,
	val items: List<ChildCartoonSummaryResponse>,
)

data class ChildCartoonEpisodeResponse(
	val id: String,
	val title: String,
	val description: String,
	val img: String,
	val video: String,
)

data class ChildCartoonDetailResponse(
	val id: String,
	val title: String,
	val description: String,
	val img: String,
	val video: String,
	val tags: List<ChildCartoonTagResponse>,
	val isFavorite: Boolean,
	val episodes: List<ChildCartoonEpisodeResponse>,
)

data class CartoonFavoriteRequest(
	val favorite: Boolean,
)

data class CartoonFavoriteResponse(
	val isFavorite: Boolean,
)

@RestController
@RequestMapping("/api/children/{childId}/cartoons")
class ChildCartoonController(
	private val cartoonQueryService: CartoonQueryService,
) {
	@GetMapping
	fun list(@PathVariable childId: String): ChildCartoonListResponse {
		val catalog = cartoonQueryService.listForChild(childId)
		return ChildCartoonListResponse(
			tags = catalog.tags.map { it.toChildResponse() },
			items = catalog.items.map { it.toResponse() },
		)
	}

	@GetMapping("/{cartoonId}")
	fun get(
		@PathVariable childId: String,
		@PathVariable cartoonId: String,
	): ChildCartoonDetailResponse =
		cartoonQueryService.getForChild(childId, cartoonId).toResponse()

	@PutMapping("/{cartoonId}/favorite")
	fun setFavorite(
		@PathVariable childId: String,
		@PathVariable cartoonId: String,
		@RequestBody request: CartoonFavoriteRequest,
	): CartoonFavoriteResponse =
		CartoonFavoriteResponse(
			isFavorite = cartoonQueryService.setFavorite(childId, cartoonId, request.favorite),
		)
}

private fun CartoonTag.toChildResponse() =
	ChildCartoonTagResponse(
		id = id,
		title = title,
		icon = icon,
	)

private fun CartoonChildItem.toResponse() =
	ChildCartoonSummaryResponse(
		id = id,
		title = title,
		img = img,
		tags = tags.map { it.toChildResponse() },
		isFavorite = isFavorite,
	)

private fun CartoonChildDetail.toResponse() =
	ChildCartoonDetailResponse(
		id = id,
		title = title,
		description = description,
		img = img,
		video = video,
		tags = tags.map { it.toChildResponse() },
		isFavorite = isFavorite,
		episodes = episodes.map {
			ChildCartoonEpisodeResponse(
				id = it.id,
				title = it.title,
				description = it.description,
				img = it.img,
				video = it.video,
			)
		},
	)
