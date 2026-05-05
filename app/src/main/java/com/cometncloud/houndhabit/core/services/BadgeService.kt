package com.cometncloud.houndhabit.core.services

import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Badge
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

class BadgeService {
    private val supabase get() = SupabaseClient.client

    suspend fun fetchBadges(userId: String): List<Badge> =
        supabase.postgrest
            .from("badges")
            .select {
                filter { eq("user_id", userId.lowercase()) }
                order("earned_at", Order.ASCENDING)
            }
            .decodeList()
}
