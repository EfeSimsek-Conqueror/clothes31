package com.fitrater.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.fitrater.app.data.Supa
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import java.security.MessageDigest
import java.security.SecureRandom

object GoogleAuth {
    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun randomNonce(): String {
        val sr = SecureRandom()
        val bytes = ByteArray(32)
        sr.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Presents the Google Credential Manager UI, extracts the ID token,
     * exchanges it with Supabase, then upserts the profile.
     * Returns the display name on success.
     */
    suspend fun signIn(context: Context): Result<String?> = runCatching {
        val rawNonce = randomNonce()
        val hashedNonce = sha256(rawNonce)

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(Supa.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val cm = CredentialManager.create(context)
        val response = cm.getCredential(context, request)
        val cred = response.credential
        require(cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type: ${cred.type}"
        }
        val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
        val idToken = googleCred.idToken

        Supa.client.auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
            this.nonce = rawNonce
        }

        googleCred.displayName ?: googleCred.givenName
    }
}
