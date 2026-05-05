package com.cometncloud.houndhabit.core.services

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.cometncloud.houndhabit.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.util.UUID

private const val TAG = "HoundHabitAuth"

/**
 * Result of a successful Credential Manager Google Sign-In flow.
 *
 * - [idToken] is the Google-issued JWT we send to Supabase.
 * - [rawNonce] is the un-hashed nonce; Supabase hashes it server-side and verifies it matches the
 *   `nonce` claim in [idToken]. We pass the raw value, not the hash.
 */
data class GoogleSignInResult(val idToken: String, val rawNonce: String)

object GoogleSignInClient {

    /**
     * Returns a [GoogleSignInResult], or `null` if the user dismissed the sheet.
     * Throws on misconfiguration or on no-Google-account-on-device.
     */
    suspend fun requestIdToken(context: Context): GoogleSignInResult? {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        check(webClientId.isNotBlank()) {
            "GOOGLE_WEB_CLIENT_ID is not set. Add it to local.properties; see CLAUDE.md."
        }
        Log.i(TAG, "Google sign-in requested. webClientId prefix=${webClientId.take(20)}…(${webClientId.length} chars), pkg=${context.packageName}")

        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = sha256Hex(rawNonce)

        val option = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val response = try {
            CredentialManager.create(context).getCredential(context = context, request = request)
        } catch (e: GetCredentialCancellationException) {
            // androidx maps two very different things to this exception: a real user dismiss
            // (back / tap-outside / no account picked), and GMS GoogleSignInActivity finishing
            // with RESULT_CANCELED after a backend error like UNREGISTERED_ON_API_CONSOLE. The
            // backend-error variant carries a non-blank errorMessage; surface it so the user
            // (and we) don't get gaslit into thinking they cancelled.
            val detail = e.errorMessage?.toString()?.takeIf { it.isNotBlank() }
            if (detail != null) {
                Log.w(TAG, "Credential Manager finished with error masquerading as cancellation: $detail", e)
                throw IllegalStateException(
                    "Google Sign-In could not complete: $detail. " +
                        "Check Google Cloud Console (Android OAuth client for ${context.packageName}) " +
                        "and logcat tag 'Auth.Api.Credentials' for the underlying GMS error.",
                    e,
                )
            }
            Log.i(TAG, "Google sign-in cancelled by user (no errorMessage on exception).", e)
            return null
        } catch (e: NoCredentialException) {
            Log.w(TAG, "NoCredentialException: no Google account on device.", e)
            throw IllegalStateException(
                "No Google account is set up on this device. Add one in Settings, then try again.",
                e,
            )
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException type='${e.type}' message='${e.errorMessage}'", e)
            throw IllegalStateException(
                "Google Sign-In failed (${e.type}): ${e.errorMessage ?: e.message ?: "no detail"}",
                e,
            )
        }

        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = try {
                GoogleIdTokenCredential.createFrom(credential.data)
            } catch (e: GoogleIdTokenParsingException) {
                throw IllegalStateException("Could not parse Google ID token credential.", e)
            }
            return GoogleSignInResult(idToken = google.idToken, rawNonce = rawNonce)
        }

        throw IllegalStateException("Unexpected credential type: ${credential.type}")
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
