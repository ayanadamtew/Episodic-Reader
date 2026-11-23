package com.episodicreader.platform

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface AppClock {
    fun nowUtcMillis(): Long
    fun todayLocalDateIso(): String
    fun toLocalDateIso(utcMillis: Long): String
}

class SystemAppClock(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : AppClock {
    override fun nowUtcMillis(): Long = System.currentTimeMillis()

    override fun todayLocalDateIso(): String = LocalDate.now(zoneId).toString()

    override fun toLocalDateIso(utcMillis: Long): String =
        Instant.ofEpochMilli(utcMillis).atZone(zoneId).toLocalDate().toString()
}
