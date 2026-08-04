package com.example.alim.persistence

import java.sql.Timestamp
import java.time.Instant

fun Instant.toTimestamp(): Timestamp = Timestamp.from(this)

fun Timestamp?.toInstantOrNull(): Instant? = this?.toInstant()
