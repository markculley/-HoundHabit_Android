package com.cometncloud.houndhabit.core.services

import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Profile
import com.cometncloud.houndhabit.core.models.Role
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthService {
    private val supabase get() = SupabaseClient.client

    suspend fun signUp(email: String, password: String, fullName: String, role: Role) {
        // Profile is created automatically via a Postgres trigger on auth.users.
        // Pass full_name and role as metadata so the trigger can read them.
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            this.data = buildJsonObject {
                put("full_name", fullName)
                put("role", role.wireValue)
            }
        }
    }

    suspend fun signIn(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        supabase.auth.signOut()
    }

    suspend fun deleteAccount() {
        supabase.postgrest.rpc("delete_my_account")
    }

    suspend fun currentProfile(): Profile? {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return null
        return supabase.postgrest
            .from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull()
    }
}

private val Role.wireValue: String
    get() = when (this) {
        Role.Guardian -> "guardian"
        Role.Trainer -> "trainer"
    }
