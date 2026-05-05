package com.cometncloud.houndhabit.feature.guardian.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Distance
import com.cometncloud.houndhabit.core.models.Distraction
import com.cometncloud.houndhabit.core.models.Pet
import com.cometncloud.houndhabit.core.models.TrainingDuration
import com.cometncloud.houndhabit.core.models.TrainingRecord
import com.cometncloud.houndhabit.core.services.PetService
import com.cometncloud.houndhabit.core.services.TrainingRecordService
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

data class TrainingRecordsUiState(
    val records: List<TrainingRecord> = emptyList(),
    /** All of the guardian's pets — drives the form's pet picker. */
    val pets: List<Pet> = emptyList(),
    /** Map of pet id → name, used to label rows when the list spans multiple pets. */
    val petNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class TrainingRecordViewModel(
    private val recordService: TrainingRecordService = TrainingRecordService(),
    private val petService: PetService = PetService(),
) : ViewModel() {

    private val _state = MutableStateFlow(TrainingRecordsUiState())
    val state: StateFlow<TrainingRecordsUiState> = _state.asStateFlow()

    private val supabase get() = SupabaseClient.client

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    fun loadRecords(petId: String? = null) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val (records, pets) = coroutineScope {
                    val r = async { recordService.fetchRecords(userId, petId) }
                    val p = async { petService.fetchPets(userId) }
                    r.await() to p.await()
                }
                _state.update {
                    it.copy(
                        records = records,
                        pets = pets,
                        petNames = pets.associate { p -> p.id to p.name },
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not load sessions.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun createRecord(
        petId: String,
        recordedAt: Instant,
        score: Int,
        distance: Distance,
        distraction: Distraction,
        duration: TrainingDuration,
        notes: String?,
        isShared: Boolean,
    ) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val record = recordService.createRecord(
                    petId = petId,
                    guardianId = userId,
                    recordedAt = recordedAt,
                    score = score,
                    distance = distance,
                    distraction = distraction,
                    duration = duration,
                    notes = notes?.ifBlank { null },
                    isShared = isShared,
                )
                _state.update { it.copy(records = listOf(record) + it.records) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not log session.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateRecord(record: TrainingRecord) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val saved = recordService.updateRecord(record)
                _state.update { s ->
                    s.copy(records = s.records.map { if (it.id == saved.id) saved else it })
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not update session.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Optimistic share-toggle. Reverts on failure to avoid a stuck UI.
     */
    fun toggleSharing(record: TrainingRecord) {
        val toggled = record.copy(isShared = !record.isShared)
        _state.update { s ->
            s.copy(records = s.records.map { if (it.id == record.id) toggled else it })
        }
        viewModelScope.launch {
            try {
                val saved = recordService.updateRecord(toggled)
                _state.update { s ->
                    s.copy(records = s.records.map { if (it.id == saved.id) saved else it })
                }
            } catch (t: Throwable) {
                _state.update { s ->
                    s.copy(
                        records = s.records.map { if (it.id == record.id) record else it },
                        errorMessage = t.message ?: "Could not update sharing.",
                    )
                }
            }
        }
    }

    fun deleteRecord(record: TrainingRecord) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                recordService.deleteRecord(record.id)
                _state.update { s -> s.copy(records = s.records.filterNot { it.id == record.id }) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not delete session.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}
