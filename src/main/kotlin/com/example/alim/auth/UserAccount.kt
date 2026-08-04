package com.example.alim.auth

import com.example.alim.parent.FamilyPreferences

data class UserAccount(
	val id: String,
	val phoneNumber: String,
	val pinHash: String,
	val activeChildId: String? = null,
	val preferences: FamilyPreferences = FamilyPreferences(),
)
