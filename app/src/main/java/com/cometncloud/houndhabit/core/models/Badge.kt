package com.cometncloud.houndhabit.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Badge(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("badge_type") val badgeType: BadgeType,
    @SerialName("earned_at") val earnedAt: Instant,
)

@Serializable
enum class BadgeType {
    @SerialName("first_session") FirstSession,
    @SerialName("first_green") FirstGreen,
    @SerialName("7_day_streak") SevenDayStreak,
    @SerialName("30_day_streak") ThirtyDayStreak,
}

val BadgeType.title: String
    get() = when (this) {
        BadgeType.FirstSession -> "First Step"
        BadgeType.FirstGreen -> "Green Light"
        BadgeType.SevenDayStreak -> "Week Warrior"
        BadgeType.ThirtyDayStreak -> "Monthly Master"
    }

val BadgeType.description: String
    get() = when (this) {
        BadgeType.FirstSession -> "Logged your first training session"
        BadgeType.FirstGreen -> "Earned your first green status"
        BadgeType.SevenDayStreak -> "Trained 7 days in a row"
        BadgeType.ThirtyDayStreak -> "Trained 30 days in a row"
    }
