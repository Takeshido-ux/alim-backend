package com.example.alim.cartoon

import com.example.alim.child.ChildService
import org.springframework.stereotype.Service

@Service
class CartoonQueryService(
	private val childService: ChildService,
	private val cartoonService: CartoonService,
	private val tagService: CartoonTagService,
	private val favoriteRepository: CartoonFavoriteRepository,
) {
	fun listForChild(childId: String): CartoonCatalogResult {
		childService.requireOwnedChildForCurrentParent(childId)
		val tags = tagService.list()
		val tagsById = tags.associateBy { it.id }
		val favoriteIds = favoriteRepository.findCartoonIds(childId)
		return CartoonCatalogResult(
			tags = tags,
			items = cartoonService.list().map { cartoon ->
				cartoon.toChildItem(tagsById, favoriteIds)
			},
		)
	}

	fun getForChild(childId: String, cartoonId: String): CartoonChildDetail {
		childService.requireOwnedChildForCurrentParent(childId)
		val cartoon = cartoonService.getById(cartoonId)
		val tagsById = tagService.list().associateBy { it.id }
		return cartoon.toChildDetail(
			tagsById = tagsById,
			isFavorite = favoriteRepository.exists(childId, cartoonId),
		)
	}

	fun setFavorite(childId: String, cartoonId: String, favorite: Boolean): Boolean {
		childService.requireOwnedChildForCurrentParent(childId)
		cartoonService.getById(cartoonId)
		if (favorite) {
			favoriteRepository.add(childId, cartoonId)
		} else {
			favoriteRepository.remove(childId, cartoonId)
		}
		return favorite
	}
}

data class CartoonCatalogResult(
	val tags: List<CartoonTag>,
	val items: List<CartoonChildItem>,
)

data class CartoonChildItem(
	val id: String,
	val title: String,
	val img: String,
	val tags: List<CartoonTag>,
	val isFavorite: Boolean,
)

data class CartoonChildDetail(
	val id: String,
	val title: String,
	val description: String,
	val img: String,
	val video: String,
	val tags: List<CartoonTag>,
	val isFavorite: Boolean,
	val episodes: List<CartoonEpisode>,
)

private fun Cartoon.toChildItem(
	tagsById: Map<String, CartoonTag>,
	favoriteIds: Set<String>,
) = CartoonChildItem(
	id = id,
	title = title,
	img = img,
	tags = tagIds.mapNotNull(tagsById::get),
	isFavorite = id in favoriteIds,
)

private fun Cartoon.toChildDetail(
	tagsById: Map<String, CartoonTag>,
	isFavorite: Boolean,
) = CartoonChildDetail(
	id = id,
	title = title,
	description = description,
	img = img,
	video = video,
	tags = tagIds.mapNotNull(tagsById::get),
	isFavorite = isFavorite,
	episodes = episodes,
)
