package com.cometncloud.houndhabit.core.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trips each `@Serializable` enum value through JSON to lock the wire string.
 * Per CLAUDE.md "Testing rules": one test per enum, verifying the exact wire token.
 */
class TrainingRecordEnumsTest {

    private val json = Json

    @Test
    fun trainingStatusWireValues() {
        val cases = mapOf(
            TrainingStatus.Red to "\"red\"",
            TrainingStatus.Orange to "\"orange\"",
            TrainingStatus.Yellow to "\"yellow\"",
            TrainingStatus.Green to "\"green\"",
        )
        for ((value, wire) in cases) {
            assertEquals(wire, json.encodeToString(TrainingStatus.serializer(), value))
            assertEquals(value, json.decodeFromString(TrainingStatus.serializer(), wire))
        }
    }

    @Test
    fun distanceWireValues() {
        val cases = mapOf(
            Distance.ArmsLength to "\"arms_length\"",
            Distance.SixFeet to "\"6_feet\"",
            Distance.TwelveFeet to "\"12_feet\"",
            Distance.TwentyFeet to "\"20_feet\"",
            Distance.TwentyPlusFeet to "\"20_plus_feet\"",
            Distance.Custom to "\"custom\"",
        )
        for ((value, wire) in cases) {
            assertEquals(wire, json.encodeToString(Distance.serializer(), value))
            assertEquals(value, json.decodeFromString(Distance.serializer(), wire))
        }
    }

    @Test
    fun distractionWireValues() {
        val cases = mapOf(
            Distraction.None to "\"none\"",
            Distraction.Any to "\"any\"",
            Distraction.Custom to "\"custom\"",
        )
        for ((value, wire) in cases) {
            assertEquals(wire, json.encodeToString(Distraction.serializer(), value))
            assertEquals(value, json.decodeFromString(Distraction.serializer(), wire))
        }
    }

    @Test
    fun trainingDurationWireValues() {
        val cases = mapOf(
            TrainingDuration.Instant to "\"instant\"",
            TrainingDuration.FiveSeconds to "\"5_seconds\"",
            TrainingDuration.FivePlusSeconds to "\"5_plus_seconds\"",
            TrainingDuration.Custom to "\"custom\"",
        )
        for ((value, wire) in cases) {
            assertEquals(wire, json.encodeToString(TrainingDuration.serializer(), value))
            assertEquals(value, json.decodeFromString(TrainingDuration.serializer(), wire))
        }
    }

    @Test
    fun statusFromScore() {
        assertEquals(TrainingStatus.Green, TrainingStatus.fromScore(5))
        assertEquals(TrainingStatus.Yellow, TrainingStatus.fromScore(4))
        assertEquals(TrainingStatus.Yellow, TrainingStatus.fromScore(3))
        assertEquals(TrainingStatus.Orange, TrainingStatus.fromScore(2))
        assertEquals(TrainingStatus.Red, TrainingStatus.fromScore(1))
        assertEquals(TrainingStatus.Red, TrainingStatus.fromScore(0))
    }
}
