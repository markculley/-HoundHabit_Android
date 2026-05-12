package com.cometncloud.houndhabit.feature.trainer.guardians

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Comment
import com.cometncloud.houndhabit.core.models.LinkedGuardian
import com.cometncloud.houndhabit.core.models.Pet
import com.cometncloud.houndhabit.core.models.ResourceKind
import com.cometncloud.houndhabit.core.models.TrainingRecord
import com.cometncloud.houndhabit.core.services.CommentService
import com.cometncloud.houndhabit.core.services.PetService
import com.cometncloud.houndhabit.core.services.ResourceService
import com.cometncloud.houndhabit.core.services.StorageService
import com.cometncloud.houndhabit.core.services.TrainingRecordService
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class GuardianDetailUiState(
    val pets: List<Pet> = emptyList(),
    val records: List<TrainingRecord> = emptyList(),
    val comments: Map<String, List<Comment>> = emptyMap(),
    val isLoading: Boolean = false,
    val isSavingResource: Boolean = false,
    val lastResourceSavedAt: Long? = null,
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
    private val resourceService: ResourceService = ResourceService(),
    private val storageService: StorageService = StorageService(),
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

    /**
     * Trainer creates a resource for [guardian]. Photos upload to the
     * `resources` bucket first (path `{guardianId}/{resourceId}.jpg`); the
     * resulting public URL is stored in the resource row.
     *
     * Sets `added_by_id = trainer.id`, `guardian_id = guardian.guardianId` —
     * RLS allows the insert because the trainer is linked to the guardian.
     */
    fun addResourceForGuardian(
        guardian: LinkedGuardian,
        kind: ResourceKind,
        title: String,
        urlText: String?,
        body: String?,
        photoBytes: ByteArray?,
    ) {
        val trainerId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSavingResource = true, errorMessage = null) }
            try {
                val resourceId = UUID.randomUUID().toString()
                val resolvedUrl = when (kind) {
                    ResourceKind.Photo -> {
                        if (photoBytes == null) {
                            _state.update {
                                it.copy(errorMessage = "Pick a photo before saving.")
                            }
                            return@launch
                        }
                        storageService.uploadResourcePhoto(
                            bytes = photoBytes,
                            guardianId = guardian.guardianId,
                            resourceId = resourceId,
                        )
                    }
                    ResourceKind.Url -> urlText?.trim()?.ifEmpty { null }
                    ResourceKind.Note -> null
                }

                resourceService.createResourceForGuardian(
                    guardianId = guardian.guardianId,
                    addedById = trainerId,
                    kind = kind,
                    title = title,
                    url = resolvedUrl,
                    body = body,
                    resourceId = resourceId,
                )
                _state.update {
                    it.copy(lastResourceSavedAt = System.currentTimeMillis())
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(errorMessage = t.message ?: "Could not save resource.")
                }
            } finally {
                _state.update { it.copy(isSavingResource = false) }
            }
        }
    }
}
