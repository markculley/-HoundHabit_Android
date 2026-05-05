package com.cometncloud.houndhabit.feature.guardian.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Pet
import com.cometncloud.houndhabit.core.services.PetService
import com.cometncloud.houndhabit.core.services.StorageService
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PetsUiState(
    val pets: List<Pet> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class PetViewModel(
    private val petService: PetService = PetService(),
    private val storageService: StorageService = StorageService(),
) : ViewModel() {

    private val _state = MutableStateFlow(PetsUiState())
    val state: StateFlow<PetsUiState> = _state.asStateFlow()

    private val supabase get() = SupabaseClient.client

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    fun loadPets() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val pets = petService.fetchPets(userId)
                _state.update { it.copy(pets = pets) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not load pets.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun createPet(name: String, breed: String?, photoBytes: ByteArray?) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                var pet = petService.createPet(
                    guardianId = userId,
                    name = name,
                    breed = breed,
                    photoUrl = null,
                )
                if (photoBytes != null) {
                    try {
                        val url = storageService.uploadPetPhoto(
                            bytes = photoBytes,
                            guardianId = userId,
                            petId = pet.id,
                        )
                        pet = petService.updatePet(pet.copy(photoUrl = url))
                    } catch (t: Throwable) {
                        // Pet was created — surface a non-fatal note. Caller can retry the photo via edit.
                        _state.update {
                            it.copy(errorMessage = "Pet saved, but photo upload failed: ${t.message ?: ""}")
                        }
                    }
                }
                _state.update { it.copy(pets = it.pets + pet) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not create pet.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updatePet(original: Pet, name: String, breed: String?, photoBytes: ByteArray?) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                var working = original.copy(name = name, breed = breed)
                if (photoBytes != null) {
                    val url = storageService.uploadPetPhoto(
                        bytes = photoBytes,
                        guardianId = userId,
                        petId = original.id,
                    )
                    working = working.copy(photoUrl = url)
                }
                val saved = petService.updatePet(working)
                _state.update { s ->
                    s.copy(pets = s.pets.map { if (it.id == saved.id) saved else it })
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not update pet.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun deletePet(pet: Pet) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                petService.deletePet(pet.id)
                _state.update { s -> s.copy(pets = s.pets.filterNot { it.id == pet.id }) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not delete pet.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}
