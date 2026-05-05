package com.cometncloud.houndhabit.core.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ResourceKindTest {

    private val json = Json

    @Test
    fun resourceKindWireValues() {
        val cases = mapOf(
            ResourceKind.Photo to "\"photo\"",
            ResourceKind.Url to "\"url\"",
            ResourceKind.Note to "\"note\"",
        )
        for ((value, wire) in cases) {
            assertEquals(wire, json.encodeToString(ResourceKind.serializer(), value))
            assertEquals(value, json.decodeFromString(ResourceKind.serializer(), wire))
        }
    }
}
