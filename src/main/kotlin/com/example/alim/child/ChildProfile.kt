package com.example.alim.child

import java.time.Instant

data class ChildProfile(
	val id: String,
	val parentId: String,
	val name: String,
	val age: Int,
	val avatarId: String,
	val createdAt: Instant,
	val archivedAt: Instant? = null,
)
