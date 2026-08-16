package com.example.alim.auth

import com.example.alim.parent.FamilyPreferences
import java.time.Instant

data class UserAccount(
	val id: String,
	val phoneNumber: String,
	val pinHash: String,
	val createdAt: Instant,
	val activeChildId: String? = null,
	val preferences: FamilyPreferences = FamilyPreferences(),
)
