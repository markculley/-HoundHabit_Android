package com.cometncloud.houndhabit.feature.trainer.guardians

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cometncloud.houndhabit.core.models.Comment
import com.cometncloud.houndhabit.core.models.LinkedGuardian
import com.cometncloud.houndhabit.core.models.Pet
import com.cometncloud.houndhabit.core.models.TrainingRecord
import com.cometncloud.houndhabit.core.services.CommentService
import com.cometncloud.houndhabit.core.services.PetService
import com.cometncloud.houndhabit.core.services.TrainingRecordService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GuardianDetailUiState(
    val pets: List<Pet> = emptyList(),
    val records: List<TrainingRecord> = emptyList(),
    val comments: Map<String, List<Comment>> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Trainer-side detail VM for one linked guardian. Loads the guardian's pets and
 * shared training records in parallel; keeps a per-record comments cache.
 *
 * Comments aren't preloaded — the detail screen loads them on demand when the
 * user opens a record.
 */
class GuardianDetailViewModel(
    private val petService: PetService = PetService(),
    private val recordService: TrainingRecordService = TrainingRecordService(),
    private val commentService: CommentService = CommentService(),
) : ViewModel() {

    private val _state = MutableStateFlow(GuardianDetailUiState())
    val state: StateFlow<GuardianDetailUiState> = _state.asStateFlow()

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    fun load(guardian: LinkedGuardian) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val (pets, records) = coroutineScope {
                    val p = async { petService.fetchPets(guardian.guardianId) }
                    val r = async { recordService.fetchRecords(guardian.guardianId) }
                    p.await() to r.await()
                }
                _state.update { it.copy(pets = pets, records = records) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not load guardian.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadComments(recordId: String) {
        viewModelScope.launch {
            try {
                val list = commentService.fetchComments(recordId)
                _state.update { it.copy(comments = it.comments + (recordId to list)) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not load comments.") }
            }
        }
    }

    suspend fun addComment(recordId: String, body: String) {
        try {
            val saved = commentService.addComment(recordId, body)
            _state.update {
                val current = it.comments[recordId].orEmpty()
                it.copy(comments = it.comments + (recordId to current + saved))
            }
        } catch (t: Throwable) {
            _state.update { it.copy(errorMessage = t.message ?: "Could not add comment.") }
        }
    }

    suspend fun deleteComment(comment: Comment) {
        try {
            commentService.deleteComment(comment.id)
            _state.update {
                val current = it.comments[comment.trainingRecordId].orEmpty()
                it.copy(
                    comments = it.comments + (comment.trainingRecordId to current.filterNot { c -> c.id == comment.id }),
                )
            }
        } catch (t: Throwable) {
            _state.update { it.copy(errorMessage = t.message ?: "Could not delete comment.") }
        }
    }

    fun petName(petId: String): String =
        _state.value.pets.firstOrNull { it.id == petId }?.name ?: "Unknown Pet"
}
