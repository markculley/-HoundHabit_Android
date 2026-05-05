package com.cometncloud.houndhabit.core.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class InviteAndLinkStatusTest {

    private val json = Json

    @Test
    fun inviteStatusWireValues() {
        val cases = mapOf(
            InviteStatus.Pending to "\"pending\"",
            InviteStatus.Accepted to "\"accepted\"",
            InviteStatus.Expired to "\"expired\"",
        )
        for ((value, wire) in cases) {
            assertEquals(wire, json.encodeToString(InviteStatus.serializer(), value))
            assertEquals(value, json.decodeFromString(InviteStatus.serializer(), wire))
        }
    }

    @Test
    fun linkStatusWireValues() {
        val cases = mapOf(
            LinkStatus.Active to "\"active\"",
            LinkStatus.Inactive to "\"inactive\"",
        )
        for ((value, wire) in cases) {
            assertEquals(wire, json.encodeToString(LinkStatus.serializer(), value))
            assertEquals(value, json.decodeFromString(LinkStatus.serializer(), wire))
        }
    }
}
