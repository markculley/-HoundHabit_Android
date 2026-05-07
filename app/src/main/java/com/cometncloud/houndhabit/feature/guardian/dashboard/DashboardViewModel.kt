package com.cometncloud.houndhabit.feature.guardian.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Badge
import com.cometncloud.houndhabit.core.models.LinkedTrainer
import com.cometncloud.houndhabit.core.models.Pet
import com.cometncloud.houndhabit.core.models.TrainingRecord
import com.cometncloud.houndhabit.core.services.BadgeService
import com.cometncloud.houndhabit.core.services.InviteService
import com.cometncloud.houndhabit.core.services.PetService
import com.cometncloud.houndhabit.core.services.TrainingPlanService
import com.cometncloud.houndhabit.core.services.TrainingRecordService
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

data class DashboardUiState(
    val pets: List<Pet> = emptyList(),
    val records: List<TrainingRecord> = emptyList(),
    val badges: List<Badge> = emptyList(),
    val linkedTrainer: LinkedTrainer? = null,
    val currentStreak: Int = 0,
    val assignedPlanCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    /** The 3 most recently earned badges, shown on the dashboard. */
    val recentBadges: List<Badge> get() = badges.sortedByDescending { it.earnedAt }.take(3)
}

class DashboardViewModel(
    private val petService: PetService = PetService(),
    private val recordService: TrainingRecordService = TrainingRecordService(),
    private val badgeService: BadgeService = BadgeService(),
    private val inviteService: InviteService = InviteService(),
    private val planService: TrainingPlanService = TrainingPlanService(),
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private val supabase get() = SupabaseClient.client

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    fun load() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val result = coroutineScope {
                    val p = async { petService.fetchPets(userId) }
                    val r = async { recordService.fetchRecords(userId) }
                    val b = async { runCatching { badgeService.fetchBadges(userId) }.getOrDefault(emptyList()) }
                    val t = async { runCatching { inviteService.fetchLinkedTrainer() }.getOrNull() }
                    val plans = async { runCatching { planService.fetchAssignedPlans() }.getOrDefault(emptyList()) }
                    DashboardLoadResult(p.await(), r.await(), b.await(), t.await(), plans.await().size)
                }
                _state.update {
                    it.copy(
                        pets = result.pets,
                        records = result.records,
                        badges = result.badges,
                        linkedTrainer = result.linkedTrainer,
                        assignedPlanCount = result.assignedPlanCount,
                        currentStreak = computeStreak(result.records.map { rec -> rec.recordedAt }),
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not load dashboard.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}

private data class DashboardLoadResult(
    val pets: List<Pet>,
    val records: List<TrainingRecord>,
    val badges: List<Badge>,
    val linkedTrainer: LinkedTrainer?,
    val assignedPlanCount: Int,
)

/**
 * Pure streak computation — extracted as a top-level function for testability.
 *
 * A streak is the number of consecutive calendar days (in the user's local timezone) ending
 * today (or yesterday, if no session today yet) on which at least one session was logged.
 *
 * @param sessionDates timestamps of all the user's sessions; duplicates are ignored.
 * @param tz timezone whose calendar days define a "day"; defaults to system default.
 * @param today reference "today" for the calculation; defaults to current calendar date in [tz].
 */
fun computeStreak(
    sessionDates: List<Instant>,
    tz: TimeZone = TimeZone.currentSystemDefault(),
    today: LocalDate = Clock.System.now().toLocalDateTime(tz).date,
): Int {
    val days = sessionDates.map { it.toLocalDateTime(tz).date }.toSet()
    if (days.isEmpty()) return 0

    var checkDay = today
    if (checkDay !in days) {
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        if (yesterday !in days) return 0
        checkDay = yesterday
    }

    var streak = 0
    while (checkDay in days) {
        streak++
        checkDay = checkDay.minus(1, DateTimeUnit.DAY)
    }
    return streak
}
