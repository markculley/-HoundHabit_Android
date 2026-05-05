package com.cometncloud.houndhabit.core.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BadgeTypeTest {

    private val json = Json

    @Test
    fun badgeTypeWireValues() {
        val cases = mapOf(
            BadgeType.FirstSession to "\"first_session\"",
            BadgeType.FirstGreen to "\"first_green\"",
            BadgeType.SevenDayStreak to "\"7_day_streak\"",
            BadgeType.ThirtyDayStreak to "\"30_day_streak\"",
        )
        for ((value, wire) in cases) {
            assertEquals(wire, json.encodeToString(BadgeType.serializer(), value))
            assertEquals(value, json.decodeFromString(BadgeType.serializer(), wire))
        }
    }
}
