package com.cometncloud.houndhabit.feature.guardian.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Resource
import com.cometncloud.houndhabit.core.models.ResourceKind
import com.cometncloud.houndhabit.core.services.ResourceService
import com.cometncloud.houndhabit.core.services.StorageService
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ResourcesUiState(
    val resources: List<Resource> = emptyList(),
    val selectedKind: ResourceKind? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val filtered: List<Resource>
        get() = if (selectedKind == null) resources else resources.filter { it.kind == selectedKind }
}

class ResourceViewModel(
    private val service: ResourceService = ResourceService(),
    private val storageService: StorageService = StorageService(),
) : ViewModel() {

    private val _state = MutableStateFlow(ResourcesUiState())
    val state: StateFlow<ResourcesUiState> = _state.asStateFlow()

    private val supabase get() = SupabaseClient.client

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    fun setKindFilter(kind: ResourceKind?) = _state.update { it.copy(selectedKind = kind) }

    fun load() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val resources = service.fetchResources(userId)
                _state.update { it.copy(resources = resources) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not load resources.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Creates a resource. For [ResourceKind.Photo], [photoBytes] are uploaded to the
     * `resources` storage bucket first; the resulting public URL is stored in `url`.
     */
    fun create(
        kind: ResourceKind,
        title: String,
        urlText: String?,
        body: String?,
        photoBytes: ByteArray?,
    ) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val resourceId = UUID.randomUUID().toString()
                val resolvedUrl = when (kind) {
                    ResourceKind.Photo -> {
                        if (photoBytes == null) {
                            _state.update { it.copy(errorMessage = "Pick a photo before saving.") }
                            return@launch
                        }
                        storageService.uploadResourcePhoto(
                            bytes = photoBytes,
                            guardianId = userId,
                            resourceId = resourceId,
                        )
                    }
                    ResourceKind.Url -> urlText?.trim()?.ifEmpty { null }
                    ResourceKind.Note -> null
                }

                val saved = service.createResource(
                    guardianId = userId,
                    kind = kind,
                    title = title,
                    url = resolvedUrl,
                    body = body,
                    resourceId = resourceId,
                )
                _state.update { it.copy(resources = listOf(saved) + it.resources) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not save resource.") }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    fun delete(resource: Resource) {
        viewModelScope.launch {
            try {
                service.deleteResource(resource.id)
                _state.update { s -> s.copy(resources = s.resources.filterNot { it.id == resource.id }) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not delete resource.") }
            }
        }
    }
}
