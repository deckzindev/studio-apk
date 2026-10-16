package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.BuildConfig

/**
 * Manages secure local persistence for GitHub credentials, Supabase authentication,
 * and user-dedicated repository configuration.
 */
class TokenStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gitbuilder_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "enc_github_token"
        private const val KEY_USERNAME = "github_username"
        private const val KEY_USER_NAME = "github_user_name"
        private const val KEY_AVATAR = "github_avatar_url"
        private const val KEY_REPO = "github_target_repo"
        const val DEFAULT_REPO = "android-apk-builder"

        // Supabase session & user keys
        private const val KEY_SUPABASE_USER_ID = "supabase_user_id"
        private const val KEY_SUPABASE_USER_EMAIL = "supabase_user_email"
        private const val KEY_SUPABASE_USER_NAME = "supabase_user_name"
        private const val KEY_SUPABASE_ACCESS_TOKEN = "supabase_access_token"
        private const val KEY_SUPABASE_REFRESH_TOKEN = "supabase_refresh_token"
        private const val KEY_DEDICATED_REPO = "supabase_dedicated_repo"
        private const val KEY_CUSTOM_SUPABASE_URL = "custom_supabase_url"
        private const val KEY_CUSTOM_SUPABASE_KEY = "custom_supabase_key"
    }

    // GitHub Token methods
    fun saveToken(token: String) {
        val encoded = Base64.encodeToString(token.trim().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        prefs.edit().putString(KEY_TOKEN, encoded).apply()
    }

    fun getToken(): String? {
        val encoded = prefs.getString(KEY_TOKEN, null)
        if (!encoded.isNullOrBlank()) {
            try {
                val decoded = String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8).trim()
                if (decoded.isNotBlank()) return decoded
            } catch (_: Exception) {}
        }
        // Fallback to BuildConfig if provided in secrets
        return try {
            val buildConfigToken = BuildConfig.GITHUB_TOKEN
            if (buildConfigToken.isNotBlank() && !buildConfigToken.contains("your-github-token")) buildConfigToken else null
        } catch (_: Exception) {
            null
        }
    }

    fun saveUserInfo(user: GitHubUser) {
        prefs.edit()
            .putString(KEY_USERNAME, user.login)
            .putString(KEY_USER_NAME, user.name ?: user.login)
            .putString(KEY_AVATAR, user.avatarUrl)
            .apply()
    }

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getUserDisplayName(): String? = prefs.getString(KEY_USER_NAME, null)
    fun getAvatarUrl(): String? = prefs.getString(KEY_AVATAR, null)

    fun saveTargetRepo(repoName: String) {
        val cleanName = repoName.trim().ifEmpty { DEFAULT_REPO }
        prefs.edit().putString(KEY_REPO, cleanName).apply()
    }

    fun getTargetRepo(): String = prefs.getString(KEY_REPO, DEFAULT_REPO) ?: DEFAULT_REPO

    // Supabase Session & User Data
    fun saveSupabaseSession(
        userId: String,
        email: String,
        name: String,
        accessToken: String,
        refreshToken: String?,
        dedicatedRepo: String? = null
    ) {
        prefs.edit()
            .putString(KEY_SUPABASE_USER_ID, userId)
            .putString(KEY_SUPABASE_USER_EMAIL, email)
            .putString(KEY_SUPABASE_USER_NAME, name)
            .putString(KEY_SUPABASE_ACCESS_TOKEN, accessToken)
            .putString(KEY_SUPABASE_REFRESH_TOKEN, refreshToken.orEmpty())
            .apply()

        if (!dedicatedRepo.isNullOrBlank()) {
            saveDedicatedRepo(dedicatedRepo)
        }
    }

    fun saveDedicatedRepo(repoName: String) {
        prefs.edit().putString(KEY_DEDICATED_REPO, repoName.trim()).apply()
        saveTargetRepo(repoName)
    }

    fun getDedicatedRepo(): String? = prefs.getString(KEY_DEDICATED_REPO, null)

    fun getSupabaseUserId(): String? = prefs.getString(KEY_SUPABASE_USER_ID, null)
    fun getSupabaseUserEmail(): String? = prefs.getString(KEY_SUPABASE_USER_EMAIL, null)
    fun getSupabaseUserName(): String? = prefs.getString(KEY_SUPABASE_USER_NAME, null)
    fun getSupabaseAccessToken(): String? = prefs.getString(KEY_SUPABASE_ACCESS_TOKEN, null)

    fun isLoggedIn(): Boolean {
        val uid = getSupabaseUserId()
        val token = getSupabaseAccessToken()
        return !uid.isNullOrBlank() && !token.isNullOrBlank()
    }

    fun logout() {
        prefs.edit()
            .remove(KEY_SUPABASE_USER_ID)
            .remove(KEY_SUPABASE_USER_EMAIL)
            .remove(KEY_SUPABASE_USER_NAME)
            .remove(KEY_SUPABASE_ACCESS_TOKEN)
            .remove(KEY_SUPABASE_REFRESH_TOKEN)
            .apply()
    }

    // Custom Supabase Connection settings
    fun saveCustomSupabaseConfig(url: String, key: String) {
        prefs.edit()
            .putString(KEY_CUSTOM_SUPABASE_URL, url.trim())
            .putString(KEY_CUSTOM_SUPABASE_KEY, key.trim())
            .apply()
    }

    fun getSupabaseUrl(): String {
        val custom = prefs.getString(KEY_CUSTOM_SUPABASE_URL, null)
        if (!custom.isNullOrBlank()) return custom
        return try {
            val buildConfigUrl = BuildConfig.SUPABASE_URL
            if (buildConfigUrl.isNotBlank() && !buildConfigUrl.contains("your-project")) buildConfigUrl else ""
        } catch (_: Exception) {
            ""
        }
    }

    fun getSupabaseAnonKey(): String {
        val custom = prefs.getString(KEY_CUSTOM_SUPABASE_KEY, null)
        if (!custom.isNullOrBlank()) return custom
        return try {
            val buildConfigKey = BuildConfig.SUPABASE_ANON_KEY
            if (buildConfigKey.isNotBlank() && !buildConfigKey.contains("your-supabase-anon-key")) buildConfigKey else ""
        } catch (_: Exception) {
            ""
        }
    }

    fun isSupabaseConfigured(): Boolean {
        return getSupabaseUrl().isNotBlank() && getSupabaseAnonKey().isNotBlank()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun hasToken(): Boolean = !getToken().isNullOrBlank()
}
