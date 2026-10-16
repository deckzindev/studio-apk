package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class SupabaseUser(
    val id: String,
    val email: String,
    val name: String,
    val dedicatedRepo: String? = null
)

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String?,
    val user: SupabaseUser
)

data class SupabaseBuildRecord(
    val id: String,
    val userId: String,
    val userEmail: String,
    val projectName: String,
    val repoName: String,
    val status: String,
    val elapsedSeconds: Long,
    val timestamp: Long,
    val apkUrl: String? = null,
    val apkSize: Long? = null,
    val apkFileName: String? = null
)

class SupabaseClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val apkMediaType = "application/vnd.android.package-archive".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun cleanUrl(rawUrl: String): String {
        return rawUrl.trim().trimEnd('/')
    }

    /**
     * Sign Up with Name, Email, Password, and dedicated GitHub repo tag.
     */
    suspend fun signUp(
        baseUrl: String,
        anonKey: String,
        name: String,
        email: String,
        password: String,
        dedicatedRepo: String
    ): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        runCatching {
            val rootUrl = cleanUrl(baseUrl)
            val url = "$rootUrl/auth/v1/signup"

            val metadata = JSONObject().apply {
                put("name", name.trim())
                put("display_name", name.trim())
                put("dedicated_repo", dedicatedRepo.trim())
            }

            val payload = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("password", password)
                put("data", metadata)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey.trim())
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = try {
                        val json = JSONObject(body)
                        json.optString("msg", json.optString("error_description", json.optString("message", body)))
                    } catch (_: Exception) {
                        body
                    }
                    throw IllegalStateException(errMsg.ifBlank { "Falha ao cadastrar usuário (HTTP ${response.code})" })
                }

                val json = JSONObject(body)
                val userJson = json.optJSONObject("user") ?: json
                val userId = userJson.optString("id")
                val userEmail = userJson.optString("email", email)
                val userMeta = userJson.optJSONObject("user_metadata")
                val userName = userMeta?.optString("name") ?: name
                val repo = userMeta?.optString("dedicated_repo") ?: dedicatedRepo

                val accessToken = json.optString("access_token", "")
                val refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }

                val user = SupabaseUser(
                    id = userId,
                    email = userEmail,
                    name = userName,
                    dedicatedRepo = repo
                )

                SupabaseSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    user = user
                )
            }
        }
    }

    /**
     * Sign In with Email & Password.
     */
    suspend fun signIn(
        baseUrl: String,
        anonKey: String,
        email: String,
        password: String
    ): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        runCatching {
            val rootUrl = cleanUrl(baseUrl)
            val url = "$rootUrl/auth/v1/token?grant_type=password"

            val payload = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("password", password)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey.trim())
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = try {
                        val json = JSONObject(body)
                        json.optString("error_description", json.optString("msg", json.optString("message", body)))
                    } catch (_: Exception) {
                        body
                    }
                    throw IllegalStateException(
                        if (errMsg.contains("invalid", ignoreCase = true) || errMsg.contains("credentials", ignoreCase = true))
                            "E-mail ou senha incorretos."
                        else errMsg.ifBlank { "Falha no login (HTTP ${response.code})" }
                    )
                }

                val json = JSONObject(body)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }

                val userJson = json.getJSONObject("user")
                val userId = userJson.getString("id")
                val userEmail = userJson.optString("email", email)
                val userMeta = userJson.optJSONObject("user_metadata")
                val userName = userMeta?.optString("name") ?: userEmail.substringBefore("@")
                val repo = userMeta?.optString("dedicated_repo")

                val user = SupabaseUser(
                    id = userId,
                    email = userEmail,
                    name = userName,
                    dedicatedRepo = repo
                )

                SupabaseSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    user = user
                )
            }
        }
    }

    /**
     * Update user metadata (e.g. to associate dedicated repository).
     */
    suspend fun updateDedicatedRepo(
        baseUrl: String,
        anonKey: String,
        accessToken: String,
        repoName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val rootUrl = cleanUrl(baseUrl)
            val url = "$rootUrl/auth/v1/user"

            val payload = JSONObject().apply {
                put("data", JSONObject().apply {
                    put("dedicated_repo", repoName.trim())
                })
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey.trim())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .put(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("SupabaseClient", "Não foi possível atualizar metadados do usuário: HTTP ${response.code}")
                }
            }
        }
    }

    /**
     * Upsert a build record to Supabase table `build_records`.
     */
    suspend fun saveBuildRecord(
        baseUrl: String,
        anonKey: String,
        accessToken: String,
        record: SupabaseBuildRecord
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val rootUrl = cleanUrl(baseUrl)
            val url = "$rootUrl/rest/v1/build_records"

            val payload = JSONObject().apply {
                put("id", record.id)
                put("user_id", record.userId)
                put("user_email", record.userEmail)
                put("project_name", record.projectName)
                put("repo_name", record.repoName)
                put("status", record.status)
                put("elapsed_seconds", record.elapsedSeconds)
                put("timestamp", record.timestamp)
                if (record.apkUrl != null) put("apk_url", record.apkUrl)
                if (record.apkSize != null) put("apk_size", record.apkSize)
                if (record.apkFileName != null) put("apk_file_name", record.apkFileName)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey.trim())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e("SupabaseClient", "Erro ao salvar histórico no Supabase (HTTP ${response.code}): $body")
                    throw IllegalStateException("Erro ao salvar histórico no Supabase (HTTP ${response.code})")
                }
            }
        }
    }

    /**
     * Fetch user's persistent build history from Supabase table `build_records`.
     */
    suspend fun fetchUserBuildHistory(
        baseUrl: String,
        anonKey: String,
        accessToken: String,
        userId: String
    ): Result<List<SupabaseBuildRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            val rootUrl = cleanUrl(baseUrl)
            val url = "$rootUrl/rest/v1/build_records?user_id=eq.$userId&order=timestamp.desc"

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey.trim())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Erro ao carregar histórico do Supabase (HTTP ${response.code}): $body")
                }

                val jsonArray = JSONArray(body)
                val list = ArrayList<SupabaseBuildRecord>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    list.add(
                        SupabaseBuildRecord(
                            id = item.getString("id"),
                            userId = item.optString("user_id", userId),
                            userEmail = item.optString("user_email", ""),
                            projectName = item.optString("project_name", "Projeto"),
                            repoName = item.optString("repo_name", ""),
                            status = item.optString("status", "SUCCESS"),
                            elapsedSeconds = item.optLong("elapsed_seconds", 0L),
                            timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                            apkUrl = item.optString("apk_url").takeIf { it.isNotBlank() },
                            apkSize = if (item.has("apk_size")) item.optLong("apk_size") else null,
                            apkFileName = item.optString("apk_file_name").takeIf { it.isNotBlank() }
                        )
                    )
                }
                list
            }
        }
    }

    /**
     * Delete a build record from Supabase table.
     */
    suspend fun deleteBuildRecord(
        baseUrl: String,
        anonKey: String,
        accessToken: String,
        buildId: String,
        userId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val rootUrl = cleanUrl(baseUrl)
            val url = "$rootUrl/rest/v1/build_records?id=eq.$buildId&user_id=eq.$userId"

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey.trim())
                .addHeader("Authorization", "Bearer $accessToken")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw IllegalStateException("Erro ao remover registro no Supabase: $body")
                }
            }
        }
    }

    /**
     * Upload an APK file to Supabase Storage bucket `apks`.
     */
    suspend fun uploadApkToStorage(
        baseUrl: String,
        anonKey: String,
        accessToken: String,
        userId: String,
        buildId: String,
        apkFile: File
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val rootUrl = cleanUrl(baseUrl)
            val sanitizedName = apkFile.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val remotePath = "$userId/${buildId}_$sanitizedName"
            val uploadUrl = "$rootUrl/storage/v1/object/apks/$remotePath"

            val fileRequestBody = apkFile.asRequestBody(apkMediaType)

            val request = Request.Builder()
                .url(uploadUrl)
                .addHeader("apikey", anonKey.trim())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/vnd.android.package-archive")
                .addHeader("x-upsert", "true")
                .post(fileRequestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w("SupabaseClient", "Erro ao fazer upload do APK no Supabase Storage (HTTP ${response.code}): $body")
                }
            }

            // Public object URL in bucket 'apks'
            "$rootUrl/storage/v1/object/public/apks/$remotePath"
        }
    }

    /**
     * Download an APK from Supabase Storage (or direct URL).
     */
    suspend fun downloadApkFile(
        downloadUrl: String,
        destinationFile: File,
        anonKey: String? = null,
        accessToken: String? = null,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBuilder = Request.Builder().url(downloadUrl)
            if (!anonKey.isNullOrBlank()) {
                requestBuilder.addHeader("apikey", anonKey)
            }
            if (!accessToken.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $accessToken")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Falha ao baixar APK (HTTP ${response.code})")
                }

                val body = response.body ?: throw IllegalStateException("Corpo vazio ao baixar APK")
                val totalLength = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            onProgress(totalRead, totalLength)
                        }
                        output.flush()
                    }
                }
                destinationFile
            }
        }
    }

    /**
     * Automatically fetch the admin's GitHub token placed in Supabase.
     * Supports multiple common structures:
     * 1. Table `app_config` with (key, value) where key = 'github_token'
     * 2. Table `system_settings` or `settings` with (key, value)
     * 3. Table `app_config` or `system_config` with column `github_token`
     */
    suspend fun fetchGlobalGitHubToken(baseUrl: String, anonKey: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val rootUrl = cleanUrl(baseUrl)
            if (rootUrl.isBlank() || anonKey.isBlank()) return@runCatching null

            // 1. Try table `app_config` with key/value
            var token = queryKeyValueConfig(rootUrl, anonKey, "app_config", "github_token")
                ?: queryKeyValueConfig(rootUrl, anonKey, "app_config", "GITHUB_TOKEN")

            // 2. Try table `system_settings` with key/value
            if (token == null) {
                token = queryKeyValueConfig(rootUrl, anonKey, "system_settings", "github_token")
                    ?: queryKeyValueConfig(rootUrl, anonKey, "system_settings", "GITHUB_TOKEN")
            }

            // 3. Try table `settings` with key/value
            if (token == null) {
                token = queryKeyValueConfig(rootUrl, anonKey, "settings", "github_token")
                    ?: queryKeyValueConfig(rootUrl, anonKey, "settings", "GITHUB_TOKEN")
            }

            // 4. Try table `app_config` with column `github_token`
            if (token == null) {
                token = queryColumnConfig(rootUrl, anonKey, "app_config", "github_token")
            }

            // 5. Try table `system_config` with column or key/value
            if (token == null) {
                token = queryKeyValueConfig(rootUrl, anonKey, "system_config", "github_token")
                    ?: queryColumnConfig(rootUrl, anonKey, "system_config", "github_token")
            }

            token
        }
    }

    private fun queryKeyValueConfig(rootUrl: String, anonKey: String, tableName: String, keyName: String): String? {
        return try {
            val url = "$rootUrl/rest/v1/$tableName?key=eq.$keyName&select=value"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey.trim())
                .addHeader("Authorization", "Bearer ${anonKey.trim()}")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                val array = JSONArray(body)
                if (array.length() > 0) {
                    val value = array.getJSONObject(0).optString("value", "")
                    if (value.isNotBlank()) value.trim() else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryColumnConfig(rootUrl: String, anonKey: String, tableName: String, columnName: String): String? {
        return try {
            val url = "$rootUrl/rest/v1/$tableName?select=$columnName&limit=1"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey.trim())
                .addHeader("Authorization", "Bearer ${anonKey.trim()}")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                val array = JSONArray(body)
                if (array.length() > 0) {
                    val value = array.getJSONObject(0).optString(columnName, "")
                    if (value.isNotBlank()) value.trim() else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}

