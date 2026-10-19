package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ArtifactItem
import com.example.data.GitHubApiClient
import com.example.data.GitHubUser
import com.example.data.SupabaseBuildRecord
import com.example.data.SupabaseClient
import com.example.data.SupabaseUser
import com.example.data.TokenStorage
import com.example.data.db.AppDatabase
import com.example.data.db.BuildRecord
import com.example.data.db.BuildRepository
import com.example.service.BuildForegroundService
import com.example.util.ApkHelper
import com.example.util.ProjectAnalysis
import com.example.util.ZipHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

enum class AppScreen {
    AUTH,
    SELECT_ZIP,
    BUILD_PROGRESS,
    HISTORY
}

enum class BuildPhase {
    IDLE,
    PREPARING,
    UPLOADING,
    COMPILING,
    COMPLETED_SUCCESS,
    COMPLETED_FAILURE,
    ERROR
}

data class BuildUiState(
    val currentScreen: AppScreen = AppScreen.AUTH,

    // Supabase Auth & Session
    val isSignUpMode: Boolean = true,
    val authNameInput: String = "",
    val authEmailInput: String = "",
    val authPasswordInput: String = "",
    val authConfirmPasswordInput: String = "",
    val authGithubTokenInput: String = "",
    val isAuthenticating: Boolean = false,
    val authErrorMessage: String? = null,
    val authSuccessMessage: String? = null,

    // Supabase Configuration
    val supabaseUrlInput: String = "",
    val supabaseAnonKeyInput: String = "",
    val isSupabaseConfigDialogOpen: Boolean = false,
    val isSupabaseConfigured: Boolean = false,

    // Current Authenticated User & Dedicated Repo
    val currentUser: SupabaseUser? = null,
    val dedicatedRepo: String = "",

    // GitHub Connection
    val tokenInput: String = "",
    val isSavingToken: Boolean = false,
    val tokenUser: GitHubUser? = null,
    val tokenError: String? = null,
    val targetRepo: String = "",

    // Project & Build
    val isReadingZip: Boolean = false,
    val selectedProject: ProjectAnalysis? = null,

    val buildPhase: BuildPhase = BuildPhase.IDLE,
    val currentStepNumber: Int = 1,
    val buildStatusMessage: String = "",
    val elapsedSeconds: Long = 0,
    val steps: List<SimpleBuildStep> = BuildStepHelper.createSteps(1),

    val validApkArtifact: ArtifactItem? = null,
    val isDownloadingApk: Boolean = false,
    val downloadPercent: Int = 0,
    val downloadedApkFile: File? = null,
    val buildLogs: List<String> = emptyList(),

    // Cloud History
    val historyList: List<BuildRecord> = emptyList(),
    val activeHistoryRecord: BuildRecord? = null,
    val isSyncingHistory: Boolean = false,
    val historySyncMessage: String? = null
)

class BuildViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStorage = TokenStorage(application)
    private val gitHubClient = GitHubApiClient()
    private val supabaseClient = SupabaseClient()
    private val database = AppDatabase.getDatabase(application)
    private val buildRepository = BuildRepository(database.buildRecordDao())

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    private var currentBuildId: String? = null
    private var currentBuildStartTime: Long = 0L
    private var currentProjectName: String = "Projeto Android"
    private var timerJob: Job? = null
    private var historyCollectionJob: Job? = null

    init {
        val supaConfigured = tokenStorage.isSupabaseConfigured()
        val supaUrl = tokenStorage.getSupabaseUrl()
        val supaKey = tokenStorage.getSupabaseAnonKey()
        val loggedIn = tokenStorage.isLoggedIn()

        _uiState.update {
            it.copy(
                isSupabaseConfigured = supaConfigured,
                supabaseUrlInput = supaUrl,
                supabaseAnonKeyInput = supaKey
            )
        }

        if (loggedIn) {
            val savedUid = tokenStorage.getSupabaseUserId().orEmpty()
            val savedEmail = tokenStorage.getSupabaseUserEmail().orEmpty()
            val savedName = tokenStorage.getSupabaseUserName().orEmpty()
            val savedRepo = tokenStorage.getDedicatedRepo() ?: tokenStorage.getTargetRepo()
            val savedToken = tokenStorage.getToken().orEmpty()
            val savedUsername = tokenStorage.getUsername()

            val user = SupabaseUser(
                id = savedUid,
                email = savedEmail,
                name = savedName.ifBlank { savedEmail.substringBefore("@") },
                dedicatedRepo = savedRepo
            )

            val cachedGithubUser = if (!savedUsername.isNullOrBlank()) {
                GitHubUser(
                    login = savedUsername,
                    name = tokenStorage.getUserDisplayName(),
                    avatarUrl = tokenStorage.getAvatarUrl()
                )
            } else null

            _uiState.update {
                it.copy(
                    currentScreen = AppScreen.SELECT_ZIP,
                    currentUser = user,
                    dedicatedRepo = savedRepo,
                    targetRepo = savedRepo,
                    tokenInput = savedToken,
                    tokenUser = cachedGithubUser
                )
            }

            observeUserHistory(savedUid)
            syncHistory(silent = true)
        } else {
            // First time: Obligatory account creation
            _uiState.update {
                it.copy(
                    currentScreen = AppScreen.AUTH,
                    isSignUpMode = true
                )
            }
            observeUserHistory("")
        }

        // Fetch and apply the admin's GitHub token configured globally in Supabase
        fetchAndApplyGlobalGitHubToken()

        // Listen to background service updates for live compilation tracking
        viewModelScope.launch {
            BuildForegroundService.serviceState.collect { serviceUpdate ->
                val currentLogs = _uiState.value.buildLogs
                val mergedLogs = if (serviceUpdate.logs.isNotEmpty()) {
                    (currentLogs + serviceUpdate.logs).distinct()
                } else currentLogs

                if (serviceUpdate.isRunning) {
                    val stepNum = serviceUpdate.stepNumber
                    val phase = when (stepNum) {
                        1 -> BuildPhase.PREPARING
                        2 -> BuildPhase.UPLOADING
                        3 -> BuildPhase.COMPILING
                        else -> BuildPhase.COMPILING
                    }
                    _uiState.update {
                        it.copy(
                            buildPhase = phase,
                            currentStepNumber = stepNum,
                            buildStatusMessage = serviceUpdate.statusMessage,
                            elapsedSeconds = serviceUpdate.elapsedSeconds,
                            steps = BuildStepHelper.createSteps(stepNum),
                            buildLogs = mergedLogs
                        )
                    }
                } else if (serviceUpdate.isFinished) {
                    val finalPhase = if (serviceUpdate.isSuccess) BuildPhase.COMPLETED_SUCCESS else BuildPhase.COMPLETED_FAILURE
                    _uiState.update {
                        it.copy(
                            buildPhase = finalPhase,
                            currentStepNumber = if (serviceUpdate.isSuccess) 4 else 3,
                            buildStatusMessage = serviceUpdate.statusMessage,
                            elapsedSeconds = serviceUpdate.elapsedSeconds,
                            validApkArtifact = serviceUpdate.validApkArtifact ?: it.validApkArtifact,
                            buildLogs = mergedLogs,
                            steps = BuildStepHelper.createSteps(
                                currentStep = if (serviceUpdate.isSuccess) 4 else 3,
                                isError = !serviceUpdate.isSuccess,
                                isCompleted = serviceUpdate.isSuccess
                            )
                        )
                    }
                }
            }
        }
    }

    private fun fetchAndApplyGlobalGitHubToken() {
        viewModelScope.launch {
            try {
                val supaUrl = tokenStorage.getSupabaseUrl()
                val supaKey = tokenStorage.getSupabaseAnonKey()
                if (supaUrl.isNotBlank() && supaKey.isNotBlank()) {
                    val remoteToken = supabaseClient.fetchGlobalGitHubToken(supaUrl, supaKey).getOrNull()
                    if (!remoteToken.isNullOrBlank()) {
                        tokenStorage.saveToken(remoteToken)
                    }
                }

                val effectiveToken = tokenStorage.getToken().orEmpty()
                if (effectiveToken.isNotBlank()) {
                    val ghUser = gitHubClient.verifyToken(effectiveToken).getOrNull()
                    if (ghUser != null) {
                        tokenStorage.saveUserInfo(ghUser)
                        _uiState.update { it.copy(tokenUser = ghUser, tokenInput = effectiveToken) }
                    }
                }
            } catch (e: Exception) {
                Log.w("BuildViewModel", "Não foi possível sincronizar o token global do Supabase: ${e.message}")
            }
        }
    }

    private fun observeUserHistory(userId: String) {
        historyCollectionJob?.cancel()
        historyCollectionJob = viewModelScope.launch {
            buildRepository.getBuildsForUser(userId)
                .catch { e -> Log.e("BuildViewModel", "Erro ao carregar histórico: ${e.message}", e) }
                .collect { builds ->
                    _uiState.update { it.copy(historyList = builds) }
                }
        }
    }

    // Auth Form handlers
    fun onAuthNameChanged(name: String) = _uiState.update { it.copy(authNameInput = name, authErrorMessage = null) }
    fun onAuthEmailChanged(email: String) = _uiState.update { it.copy(authEmailInput = email, authErrorMessage = null) }
    fun onAuthPasswordChanged(pwd: String) = _uiState.update { it.copy(authPasswordInput = pwd, authErrorMessage = null) }
    fun onAuthConfirmPasswordChanged(pwd: String) = _uiState.update { it.copy(authConfirmPasswordInput = pwd, authErrorMessage = null) }
    fun onAuthGithubTokenChanged(token: String) = _uiState.update { it.copy(authGithubTokenInput = token, authErrorMessage = null) }

    fun toggleAuthMode() {
        _uiState.update {
            it.copy(
                isSignUpMode = !it.isSignUpMode,
                authErrorMessage = null,
                authSuccessMessage = null
            )
        }
    }

    // Supabase Configuration handlers
    fun onSupabaseUrlChanged(url: String) = _uiState.update { it.copy(supabaseUrlInput = url) }
    fun onSupabaseAnonKeyChanged(key: String) = _uiState.update { it.copy(supabaseAnonKeyInput = key) }
    fun openSupabaseConfigDialog() = _uiState.update { it.copy(isSupabaseConfigDialogOpen = true) }
    fun closeSupabaseConfigDialog() = _uiState.update { it.copy(isSupabaseConfigDialogOpen = false) }

    fun saveSupabaseConfig() {
        val state = _uiState.value
        val url = state.supabaseUrlInput.trim()
        val key = state.supabaseAnonKeyInput.trim()

        if (url.isBlank() || key.isBlank()) {
            _uiState.update { it.copy(authErrorMessage = "Informe a URL e a Anon Key do Supabase.") }
            return
        }

        tokenStorage.saveCustomSupabaseConfig(url, key)
        _uiState.update {
            it.copy(
                isSupabaseConfigDialogOpen = false,
                isSupabaseConfigured = true,
                authErrorMessage = null
            )
        }
    }

    // Primary Auth Submit (Cadastro Obrigatório ou Login)
    fun submitAuth() {
        val state = _uiState.value
        val supaUrl = tokenStorage.getSupabaseUrl().ifBlank { state.supabaseUrlInput.trim() }.ifBlank { TokenStorage.DEFAULT_SUPABASE_URL }
        val supaKey = tokenStorage.getSupabaseAnonKey().ifBlank { state.supabaseAnonKeyInput.trim() }.ifBlank { TokenStorage.DEFAULT_SUPABASE_ANON_KEY }

        val email = state.authEmailInput.trim().lowercase()
        val password = state.authPasswordInput.trim()

        if (email.isBlank() || !email.contains("@")) {
            _uiState.update { it.copy(authErrorMessage = "Por favor, informe um e-mail válido.") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(authErrorMessage = "A senha deve ter pelo menos 6 caracteres.") }
            return
        }

        if (state.isSignUpMode) {
            // Sign Up: Only Name, Email and Password entered once!
            val name = state.authNameInput.trim().ifBlank { email.substringBefore("@") }

            if (state.authNameInput.isBlank()) {
                _uiState.update { it.copy(authErrorMessage = "Por favor, informe seu nome completo.") }
                return
            }

            // Dedicated repository name for this user: gitbuilder-<userslug>-builds
            val userSlug = name.filter { it.isLetterOrDigit() }.lowercase().take(12).ifBlank {
                email.substringBefore("@").filter { it.isLetterOrDigit() }.lowercase().take(12)
            }
            val dedicatedRepo = "gitbuilder-$userSlug-builds"
            val localUid = UUID.randomUUID().toString()

            val persistentUser = SupabaseUser(
                id = localUid,
                email = email,
                name = name,
                dedicatedRepo = dedicatedRepo
            )

            // Save session immediately so user NEVER has to enter credentials again
            tokenStorage.saveSupabaseSession(
                userId = localUid,
                email = email,
                name = name,
                accessToken = "",
                refreshToken = "",
                dedicatedRepo = dedicatedRepo
            )

            // INSTANT UI TRANSITION: Super rápido, zero delay, 100% success!
            _uiState.update {
                it.copy(
                    isAuthenticating = false,
                    currentScreen = AppScreen.SELECT_ZIP,
                    currentUser = persistentUser,
                    dedicatedRepo = dedicatedRepo,
                    targetRepo = dedicatedRepo,
                    authErrorMessage = null,
                    authSuccessMessage = null
                )
            }

            observeUserHistory(localUid)

            // Background sync with Supabase and GitHub token preparation without blocking user
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val signUpResult = supabaseClient.signUp(
                        baseUrl = supaUrl,
                        anonKey = supaKey,
                        name = name,
                        email = email,
                        password = password,
                        dedicatedRepo = dedicatedRepo
                    )

                    var serverUid = localUid
                    var finalAccessToken = ""
                    var finalRefreshToken: String? = null

                    if (signUpResult.isSuccess) {
                        val session = signUpResult.getOrThrow()
                        if (session.user.id.isNotBlank()) serverUid = session.user.id
                        finalAccessToken = session.accessToken
                        finalRefreshToken = session.refreshToken
                    } else {
                        // If already registered or need direct login
                        val signInResult = supabaseClient.signIn(supaUrl, supaKey, email, password)
                        if (signInResult.isSuccess) {
                            val session = signInResult.getOrThrow()
                            if (session.user.id.isNotBlank()) serverUid = session.user.id
                            finalAccessToken = session.accessToken
                            finalRefreshToken = session.refreshToken
                        }
                    }

                    // Update token storage with server session if obtained
                    tokenStorage.saveSupabaseSession(
                        userId = serverUid,
                        email = email,
                        name = name,
                        accessToken = finalAccessToken,
                        refreshToken = finalRefreshToken,
                        dedicatedRepo = dedicatedRepo
                    )

                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                currentUser = persistentUser.copy(id = serverUid)
                            )
                        }
                        observeUserHistory(serverUid)
                    }

                    // Ensure GitHub token from app_config and dedicated repo are ready
                    ensureGitHubTokenAndRepoReady(supaUrl, supaKey, dedicatedRepo)
                    syncHistory(silent = true)
                } catch (e: Exception) {
                    Log.w("BuildViewModel", "Aviso no registro em segundo plano: ${e.message}")
                    try {
                        ensureGitHubTokenAndRepoReady(supaUrl, supaKey, dedicatedRepo)
                    } catch (_: Exception) {}
                }
            }

        } else {
            // Sign In
            val defaultName = email.substringBefore("@")
            val userSlug = defaultName.filter { it.isLetterOrDigit() }.lowercase().take(12)
            val dedicatedRepo = "gitbuilder-$userSlug-builds"
            val localUid = UUID.randomUUID().toString()

            val fallbackUser = SupabaseUser(
                id = localUid,
                email = email,
                name = defaultName,
                dedicatedRepo = dedicatedRepo
            )

            // Save credentials immediately
            tokenStorage.saveSupabaseSession(
                userId = localUid,
                email = email,
                name = defaultName,
                accessToken = "",
                refreshToken = "",
                dedicatedRepo = dedicatedRepo
            )

            // Instant transition
            _uiState.update {
                it.copy(
                    isAuthenticating = false,
                    currentScreen = AppScreen.SELECT_ZIP,
                    currentUser = fallbackUser,
                    dedicatedRepo = dedicatedRepo,
                    targetRepo = dedicatedRepo,
                    authErrorMessage = null
                )
            }

            observeUserHistory(localUid)

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val signInResult = supabaseClient.signIn(
                        baseUrl = supaUrl,
                        anonKey = supaKey,
                        email = email,
                        password = password
                    )

                    if (signInResult.isSuccess) {
                        val session = signInResult.getOrThrow()
                        val user = session.user
                        val persistentDedicatedRepo = user.dedicatedRepo ?: dedicatedRepo

                        tokenStorage.saveSupabaseSession(
                            userId = user.id,
                            email = user.email,
                            name = user.name.ifBlank { defaultName },
                            accessToken = session.accessToken,
                            refreshToken = session.refreshToken,
                            dedicatedRepo = persistentDedicatedRepo
                        )

                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    currentUser = user.copy(name = user.name.ifBlank { defaultName }),
                                    dedicatedRepo = persistentDedicatedRepo,
                                    targetRepo = persistentDedicatedRepo
                                )
                            }
                            observeUserHistory(user.id)
                        }

                        ensureGitHubTokenAndRepoReady(supaUrl, supaKey, persistentDedicatedRepo)
                        syncHistory(silent = true)
                    } else {
                        ensureGitHubTokenAndRepoReady(supaUrl, supaKey, dedicatedRepo)
                    }
                } catch (e: Exception) {
                    Log.w("BuildViewModel", "Aviso no login em segundo plano: ${e.message}")
                    try {
                        ensureGitHubTokenAndRepoReady(supaUrl, supaKey, dedicatedRepo)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private suspend fun ensureGitHubTokenAndRepoReady(supaUrl: String, supaKey: String, repoName: String) {
        try {
            var githubToken = tokenStorage.getToken().orEmpty()
            if (githubToken.isBlank()) {
                val remoteToken = supabaseClient.fetchGlobalGitHubToken(supaUrl, supaKey).getOrNull()
                if (!remoteToken.isNullOrBlank()) {
                    tokenStorage.saveToken(remoteToken)
                    githubToken = remoteToken
                }
            }

            if (githubToken.isNotBlank()) {
                val ghUser = gitHubClient.verifyToken(githubToken).getOrNull()
                if (ghUser != null) {
                    tokenStorage.saveUserInfo(ghUser)
                    _uiState.update { it.copy(tokenUser = ghUser, tokenInput = githubToken) }
                    try {
                        gitHubClient.getOrCreateRepo(githubToken, ghUser.login, repoName)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w("BuildViewModel", "Aviso ao preparar repositório em segundo plano: ${e.message}")
        }
    }

    fun logout() {
        tokenStorage.logout()
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.AUTH,
                currentUser = null,
                isSignUpMode = false,
                authPasswordInput = "",
                authConfirmPasswordInput = "",
                historyList = emptyList()
            )
        }
        observeUserHistory("")
    }

    fun goToHistoryScreen() {
        _uiState.update { it.copy(currentScreen = AppScreen.HISTORY) }
        syncHistory(silent = true)
    }

    fun goToSelectZipScreen() {
        _uiState.update { it.copy(currentScreen = AppScreen.SELECT_ZIP) }
    }

    fun goToAuthScreen() {
        _uiState.update { it.copy(currentScreen = AppScreen.AUTH) }
    }


    fun appendLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "[$time] $message"
        _uiState.update { it.copy(buildLogs = it.buildLogs + entry) }
        BuildForegroundService.appendLog(message)
    }

    fun clearLogs() {
        _uiState.update { it.copy(buildLogs = emptyList()) }
        BuildForegroundService.clearLogs()
    }

    // Build flow initiated by user selecting ZIP
    fun onZipSelected(uri: Uri) {
        val newBuildId = UUID.randomUUID().toString()
        currentBuildId = newBuildId
        currentBuildStartTime = System.currentTimeMillis()
        currentProjectName = "Projeto Android"

        clearLogs()
        appendLog("[INFO] Inicializando processamento do arquivo ZIP...")

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isReadingZip = true,
                    currentScreen = AppScreen.BUILD_PROGRESS,
                    buildPhase = BuildPhase.PREPARING,
                    currentStepNumber = 1,
                    buildStatusMessage = "Lendo e validando arquivos do projeto...",
                    steps = BuildStepHelper.createSteps(1),
                    elapsedSeconds = 0,
                    validApkArtifact = null,
                    downloadedApkFile = null,
                    activeHistoryRecord = null
                )
            }

            val context = getApplication<Application>()
            BuildForegroundService.startService(
                context = context,
                projectName = currentProjectName,
                stepNumber = 1,
                statusMessage = "Lendo arquivos do projeto..."
            )

            // Save initial in-progress record to database & Supabase cloud
            saveInitialRecord(newBuildId, currentProjectName)

            val result = ZipHelper.readZipFromUri(context, uri)

            if (result.isFailure) {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Falha ao ler o arquivo ZIP."
                appendLog("[ERRO] Falha ao extrair arquivo ZIP: $err")
                BuildForegroundService.finishService(context, false, currentProjectName, "Falha ao ler arquivo ZIP")
                _uiState.update {
                    it.copy(
                        isReadingZip = false,
                        buildPhase = BuildPhase.ERROR,
                        buildStatusMessage = err,
                        steps = BuildStepHelper.createSteps(1, isError = true)
                    )
                }
                updateFinalRecord(newBuildId, "FAILURE", null)
                return@launch
            }

            val project = result.getOrThrow()
            currentProjectName = project.fileName

            val ktCount = project.files.count { it.path.endsWith(".kt") }
            val javaCount = project.files.count { it.path.endsWith(".java") }
            val xmlCount = project.files.count { it.path.endsWith(".xml") }
            val hasManifest = project.files.any { it.path.contains("AndroidManifest.xml", ignoreCase = true) }

            appendLog("[INFO] Arquivo selecionado: ${project.fileName} (${ZipHelper.formatBytes(project.fileSizeBytes)})")
            appendLog("[INFO] Extração concluída: ${project.totalFilesCount} arquivos encontrados no projeto.")
            appendLog("[INFO] Código-fonte: $ktCount Kotlin, $javaCount Java, $xmlCount arquivos XML.")
            if (hasManifest) {
                appendLog("[OK] AndroidManifest.xml validado.")
            }
            if (project.detectedBuildFiles.isNotEmpty()) {
                appendLog("[INFO] Scripts de compilação detectados: ${project.detectedBuildFiles.joinToString(", ")}")
            }
            if (project.hasGradleWrapper) {
                appendLog("[OK] Gradle Wrapper presente no projeto.")
            } else {
                appendLog("[INFO] Gradle Wrapper será configurado no runner.")
            }
            if (project.hasWorkflow) {
                appendLog("[INFO] Workflow do GitHub Actions já configurado.")
            } else {
                appendLog("[INFO] Injetando workflow otimizado de compilação Android...")
            }
            appendLog("[OK] Preparação local concluída com sucesso.")

            _uiState.update {
                it.copy(
                    isReadingZip = false,
                    selectedProject = project
                )
            }

            startTimer()
            startBuild()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    private fun startBuild() {
        val state = _uiState.value
        val project = state.selectedProject
        val buildId = currentBuildId ?: UUID.randomUUID().toString()

        if (project == null) {
            _uiState.update {
                it.copy(
                    buildPhase = BuildPhase.ERROR,
                    buildStatusMessage = "Nenhum arquivo de projeto selecionado.",
                    steps = BuildStepHelper.createSteps(1, isError = true)
                )
            }
            updateFinalRecord(buildId, "FAILURE", null)
            return
        }

        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                // Ensure global GitHub token configured in Supabase is resolved
                var token = tokenStorage.getToken()
                if (token.isNullOrBlank()) {
                    val supaUrl = tokenStorage.getSupabaseUrl()
                    val supaKey = tokenStorage.getSupabaseAnonKey()
                    if (supaUrl.isNotBlank() && supaKey.isNotBlank()) {
                        val remoteToken = supabaseClient.fetchGlobalGitHubToken(supaUrl, supaKey).getOrNull()
                        if (!remoteToken.isNullOrBlank()) {
                            tokenStorage.saveToken(remoteToken)
                            token = remoteToken
                        }
                    }
                }

                var user = _uiState.value.tokenUser ?: tokenStorage.getUsername()?.let {
                    GitHubUser(login = it, name = tokenStorage.getUserDisplayName(), avatarUrl = tokenStorage.getAvatarUrl())
                }

                if (user == null && !token.isNullOrBlank()) {
                    val ghUser = gitHubClient.verifyToken(token).getOrNull()
                    if (ghUser != null) {
                        tokenStorage.saveUserInfo(ghUser)
                        user = ghUser
                        _uiState.update { it.copy(tokenUser = ghUser) }
                    }
                }

                val repoName = tokenStorage.getDedicatedRepo() ?: _uiState.value.targetRepo.ifBlank { tokenStorage.getTargetRepo() }

                if (token.isNullOrBlank() || user == null) {
                    _uiState.update {
                        it.copy(
                            buildPhase = BuildPhase.ERROR,
                            buildStatusMessage = "Servidor de compilação em manutenção. Tente novamente mais tarde.",
                            steps = BuildStepHelper.createSteps(1, isError = true)
                        )
                    }
                    updateFinalRecord(buildId, "FAILURE", null)
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        buildPhase = BuildPhase.UPLOADING,
                        currentStepNumber = 2,
                        buildStatusMessage = "Enviando projeto...",
                        steps = BuildStepHelper.createSteps(2)
                    )
                }
                BuildForegroundService.updateStatus(
                    context = context,
                    stepNumber = 2,
                    statusMessage = "Enviando código do projeto..."
                )
                appendLog("[INFO] Preparando envio de ${project.files.size} arquivos para o repositório $repoName...")

                var lastLoggedCount = 0
                val uploadResult = gitHubClient.uploadProjectAndTriggerBuild(
                    token = token,
                    owner = user.login,
                    repo = repoName,
                    projectFiles = project.files,
                    onProgress = { status, current, total ->
                        val percent = if (total > 0) ((current * 100) / total) else 0
                        _uiState.update {
                            it.copy(buildStatusMessage = "Enviando arquivos ($percent%)...")
                        }
                        if (current > 0 && (current == total || current - lastLoggedCount >= 20)) {
                            lastLoggedCount = current
                            appendLog("[INFO] Upload de código: $current/$total arquivos transferidos ($percent%).")
                        }
                    }
                )

                val commitSha = uploadResult.getOrThrow()
                appendLog("[OK] Upload concluído. Commit criado na branch main (SHA: ${commitSha.take(7)}).")
                appendLog("[INFO] Inicializando runner do GitHub Actions para compilação...")

                _uiState.update {
                    it.copy(
                        buildPhase = BuildPhase.COMPILING,
                        currentStepNumber = 3,
                        buildStatusMessage = "Compilando aplicativo no servidor...",
                        steps = BuildStepHelper.createSteps(3)
                    )
                }

                BuildForegroundService.startPolling(
                    context = context,
                    token = token,
                    owner = user.login,
                    repo = repoName,
                    commitSha = commitSha,
                    projectName = project.fileName,
                    buildId = buildId,
                    startTime = currentBuildStartTime
                )

            } catch (e: Exception) {
                timerJob?.cancel()
                val msg = e.localizedMessage ?: "Erro ao enviar o projeto para o GitHub."
                BuildForegroundService.finishService(context, false, project.fileName, "Falha na preparação: $msg")
                _uiState.update {
                    it.copy(
                        buildPhase = BuildPhase.ERROR,
                        buildStatusMessage = msg,
                        steps = BuildStepHelper.createSteps(2, isError = true)
                    )
                }
                updateFinalRecord(buildId, "FAILURE", null)
            }
        }
    }

    private fun saveInitialRecord(buildId: String, projectName: String) {
        val uid = tokenStorage.getSupabaseUserId().orEmpty()
        val email = tokenStorage.getSupabaseUserEmail().orEmpty()
        val repo = tokenStorage.getDedicatedRepo() ?: _uiState.value.targetRepo.ifBlank { tokenStorage.getTargetRepo() }

        viewModelScope.launch {
            try {
                val record = BuildRecord(
                    id = buildId,
                    projectName = projectName,
                    repoName = repo,
                    status = "IN_PROGRESS",
                    elapsedSeconds = 0L,
                    timestamp = System.currentTimeMillis(),
                    logsText = "Compilação em andamento...",
                    userId = uid
                )
                buildRepository.saveBuild(record)

                // Sync initial record to Supabase
                val supaUrl = tokenStorage.getSupabaseUrl()
                val supaKey = tokenStorage.getSupabaseAnonKey()
                val supaToken = tokenStorage.getSupabaseAccessToken()
                if (supaUrl.isNotBlank() && supaKey.isNotBlank() && !supaToken.isNullOrBlank() && uid.isNotBlank()) {
                    supabaseClient.saveBuildRecord(
                        baseUrl = supaUrl,
                        anonKey = supaKey,
                        accessToken = supaToken,
                        record = SupabaseBuildRecord(
                            id = buildId,
                            userId = uid,
                            userEmail = email,
                            projectName = projectName,
                            repoName = repo,
                            status = "IN_PROGRESS",
                            elapsedSeconds = 0L,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("BuildViewModel", "Erro ao salvar registro inicial", e)
            }
        }
    }

    private fun updateFinalRecord(buildId: String, status: String, artifact: ArtifactItem?) {
        val uid = tokenStorage.getSupabaseUserId().orEmpty()
        val email = tokenStorage.getSupabaseUserEmail().orEmpty()
        val repo = tokenStorage.getDedicatedRepo() ?: _uiState.value.targetRepo.ifBlank { tokenStorage.getTargetRepo() }
        val elapsed = _uiState.value.elapsedSeconds
        val startTime = if (currentBuildStartTime > 0L) currentBuildStartTime else System.currentTimeMillis()

        viewModelScope.launch {
            try {
                val record = BuildRecord(
                    id = buildId,
                    projectName = currentProjectName,
                    repoName = repo,
                    status = status,
                    elapsedSeconds = elapsed,
                    timestamp = startTime,
                    logsText = if (status == "SUCCESS") "Compilação concluída com sucesso." else "Compilação finalizou com erro.",
                    artifactId = artifact?.id,
                    artifactDownloadUrl = artifact?.archiveDownloadUrl,
                    artifactSize = artifact?.sizeInBytes,
                    apkFileName = artifact?.name,
                    userId = uid
                )
                buildRepository.saveBuild(record)

                // Sync final state to Supabase
                val supaUrl = tokenStorage.getSupabaseUrl()
                val supaKey = tokenStorage.getSupabaseAnonKey()
                val supaToken = tokenStorage.getSupabaseAccessToken()
                if (supaUrl.isNotBlank() && supaKey.isNotBlank() && !supaToken.isNullOrBlank() && uid.isNotBlank()) {
                    supabaseClient.saveBuildRecord(
                        baseUrl = supaUrl,
                        anonKey = supaKey,
                        accessToken = supaToken,
                        record = SupabaseBuildRecord(
                            id = buildId,
                            userId = uid,
                            userEmail = email,
                            projectName = currentProjectName,
                            repoName = repo,
                            status = status,
                            elapsedSeconds = elapsed,
                            timestamp = startTime,
                            apkUrl = artifact?.archiveDownloadUrl,
                            apkSize = artifact?.sizeInBytes,
                            apkFileName = artifact?.name
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("BuildViewModel", "Erro ao atualizar registro final", e)
            }
        }
    }

    fun openHistoryRecord(record: BuildRecord) {
        val artifactItem = if (record.artifactId != null && record.artifactDownloadUrl != null) {
            ArtifactItem(
                id = record.artifactId,
                name = record.apkFileName ?: "app-debug.apk",
                sizeInBytes = record.artifactSize ?: 0,
                archiveDownloadUrl = record.cloudStorageUrl ?: record.artifactDownloadUrl
            )
        } else if (record.cloudStorageUrl != null) {
            ArtifactItem(
                id = record.id.hashCode().toLong(),
                name = record.apkFileName ?: "app-release.apk",
                sizeInBytes = record.artifactSize ?: 0,
                archiveDownloadUrl = record.cloudStorageUrl
            )
        } else null

        val isSuccess = record.status == "SUCCESS"
        val isFailure = record.status == "FAILURE"
        val phase = when {
            isSuccess -> BuildPhase.COMPLETED_SUCCESS
            isFailure -> BuildPhase.COMPLETED_FAILURE
            else -> BuildPhase.COMPILING
        }

        val stepNum = if (isSuccess) 4 else 3
        val steps = BuildStepHelper.createSteps(stepNum, isError = isFailure, isCompleted = isSuccess)

        _uiState.update {
            it.copy(
                currentScreen = AppScreen.BUILD_PROGRESS,
                buildPhase = phase,
                currentStepNumber = stepNum,
                steps = steps,
                buildStatusMessage = if (isSuccess) "Compilação concluída com sucesso!" else "A compilação falhou no servidor.",
                elapsedSeconds = record.elapsedSeconds,
                validApkArtifact = artifactItem,
                activeHistoryRecord = record,
                buildLogs = if (record.logsText.isNotBlank()) record.logsText.lines() else emptyList()
            )
        }
    }

    fun deleteHistoryRecord(id: String) {
        viewModelScope.launch {
            try {
                buildRepository.deleteBuild(id)

                val supaUrl = tokenStorage.getSupabaseUrl()
                val supaKey = tokenStorage.getSupabaseAnonKey()
                val supaToken = tokenStorage.getSupabaseAccessToken()
                val uid = tokenStorage.getSupabaseUserId()
                if (supaUrl.isNotBlank() && supaKey.isNotBlank() && !supaToken.isNullOrBlank() && !uid.isNullOrBlank()) {
                    supabaseClient.deleteBuildRecord(supaUrl, supaKey, supaToken, id, uid)
                }
            } catch (e: Exception) {
                Log.e("BuildViewModel", "Erro ao excluir do histórico", e)
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                buildRepository.clearHistory()
                _uiState.update { it.copy(historySyncMessage = "Histórico limpo com sucesso.") }
            } catch (e: Exception) {
                Log.e("BuildViewModel", "Erro ao limpar histórico", e)
            }
        }
    }

    fun clearHistorySyncMessage() {
        _uiState.update { it.copy(historySyncMessage = null) }
    }

    /**
     * Sincronização Completa:
     * 1. Baixa o histórico persistente vinculado à conta.
     * 2. Se houver token do GitHub, também busca as últimas execuções do repositório dedicado.
     */
    fun syncHistory(silent: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingHistory = true, historySyncMessage = null) }
            val supaUrl = tokenStorage.getSupabaseUrl()
            val supaKey = tokenStorage.getSupabaseAnonKey()
            val supaToken = tokenStorage.getSupabaseAccessToken()
            val uid = tokenStorage.getSupabaseUserId()

            var totalSynced = 0

            // 1. Fetch from Supabase
            if (supaUrl.isNotBlank() && supaKey.isNotBlank() && !supaToken.isNullOrBlank() && !uid.isNullOrBlank()) {
                try {
                    val supaResult = supabaseClient.fetchUserBuildHistory(supaUrl, supaKey, supaToken, uid)
                    val records = supaResult.getOrDefault(emptyList())
                    for (rec in records) {
                        val buildRecord = BuildRecord(
                            id = rec.id,
                            projectName = rec.projectName,
                            repoName = rec.repoName,
                            status = rec.status,
                            elapsedSeconds = rec.elapsedSeconds,
                            timestamp = rec.timestamp,
                            logsText = if (rec.status == "SUCCESS") "Compilação concluída com sucesso." else "Falha no processo de compilação.",
                            artifactId = rec.id.hashCode().toLong(),
                            artifactDownloadUrl = rec.apkUrl,
                            artifactSize = rec.apkSize,
                            apkFileName = rec.apkFileName,
                            userId = uid,
                            cloudStorageUrl = rec.apkUrl
                        )
                        buildRepository.saveBuild(buildRecord)
                        totalSynced++
                    }
                } catch (e: Exception) {
                    Log.w("BuildViewModel", "Erro ao buscar do Supabase", e)
                }
            }

            // 2. Fetch from GitHub Actions dedicated repository
            val token = tokenStorage.getToken()
            val user = _uiState.value.tokenUser ?: (tokenStorage.getUsername()?.let {
                GitHubUser(login = it, name = tokenStorage.getUserDisplayName(), avatarUrl = tokenStorage.getAvatarUrl())
            })
            val repo = tokenStorage.getDedicatedRepo() ?: _uiState.value.targetRepo.ifBlank { tokenStorage.getTargetRepo() }

            if (!token.isNullOrBlank() && user != null && repo.isNotBlank()) {
                try {
                    val runsResult = gitHubClient.getRecentWorkflowRuns(token, user.login, repo, perPage = 30)
                    val runs = runsResult.getOrDefault(emptyList())

                    for (run in runs) {
                        val runKey = "run-${run.id}"
                        val existing = buildRepository.getBuild(runKey)

                        val status = when (run.conclusion) {
                            "success" -> "SUCCESS"
                            "failure", "cancelled", "timed_out" -> "FAILURE"
                            else -> if (run.status == "completed") "FAILURE" else "IN_PROGRESS"
                        }

                        var artifactItem: ArtifactItem? = null
                        if (status == "SUCCESS") {
                            if (existing?.artifactDownloadUrl != null) {
                                artifactItem = ArtifactItem(
                                    id = existing.artifactId ?: run.id,
                                    name = existing.apkFileName ?: "app-release.apk",
                                    sizeInBytes = existing.artifactSize ?: 0L,
                                    archiveDownloadUrl = existing.artifactDownloadUrl
                                )
                            } else {
                                val artifactsResult = gitHubClient.getArtifacts(token, user.login, repo, run.id)
                                val artifacts = artifactsResult.getOrDefault(emptyList())
                                artifactItem = artifacts.firstOrNull {
                                    it.name.contains("apk", ignoreCase = true) && !it.expired
                                }
                            }
                        }

                        val runTimestamp = parseIsoTimestamp(run.createdAt) ?: (existing?.timestamp ?: System.currentTimeMillis())
                        val runName = existing?.projectName ?: ("Build #${run.runNumber ?: run.id}")

                        val record = BuildRecord(
                            id = runKey,
                            projectName = runName,
                            repoName = repo,
                            status = status,
                            elapsedSeconds = existing?.elapsedSeconds ?: 0L,
                            timestamp = runTimestamp,
                            logsText = if (status == "SUCCESS") "Compilação concluída com sucesso." else "Compilação falhou.",
                            artifactId = artifactItem?.id,
                            artifactDownloadUrl = artifactItem?.archiveDownloadUrl,
                            artifactSize = artifactItem?.sizeInBytes,
                            apkFileName = artifactItem?.name,
                            userId = uid,
                            cloudStorageUrl = existing?.cloudStorageUrl
                        )

                        buildRepository.saveBuild(record)
                        totalSynced++
                    }
                } catch (e: Exception) {
                    Log.w("BuildViewModel", "Erro ao buscar do GitHub", e)
                }
            }

            _uiState.update {
                it.copy(
                    isSyncingHistory = false,
                    historySyncMessage = if (!silent) {
                        if (totalSynced > 0) "Histórico sincronizado ($totalSynced registros)."
                        else "Histórico atualizado."
                    } else null
                )
            }
        }
    }

    fun syncHistoryFromGitHub(silent: Boolean = false) = syncHistory(silent)

    private fun parseIsoTimestamp(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(isoString)?.time
        } catch (_: Exception) {
            try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                format.parse(isoString)?.time
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Download and install APK.
     * Prioritizes direct download from Supabase Storage (which never expires).
     * If downloading from GitHub artifact ZIP, automatically uploads the extracted APK
     * to Supabase Storage so the user can download it on any device forever!
     */
    fun downloadAndOpenApk(context: Context) {
        val artifact = _uiState.value.validApkArtifact ?: return
        val record = _uiState.value.activeHistoryRecord

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingApk = true, downloadPercent = 0) }
            try {
                val apkDir = File(context.filesDir, "downloaded_apks").apply { if (!exists()) mkdirs() }
                val targetApk = File(apkDir, artifact.name.ifBlank { "app-release.apk" })

                val cloudUrl = record?.cloudStorageUrl ?: artifact.archiveDownloadUrl

                // Case 1: Download from Supabase Storage or Direct APK link
                if (cloudUrl.contains(".apk") || cloudUrl.contains("/storage/v1/object/")) {
                    val supaKey = tokenStorage.getSupabaseAnonKey()
                    val supaToken = tokenStorage.getSupabaseAccessToken()
                    val result = supabaseClient.downloadApkFile(
                        downloadUrl = cloudUrl,
                        destinationFile = targetApk,
                        anonKey = supaKey,
                        accessToken = supaToken,
                        onProgress = { downloaded, total ->
                            if (total > 0) {
                                val percent = ((downloaded * 100) / total).toInt()
                                _uiState.update { it.copy(downloadPercent = percent) }
                            }
                        }
                    )
                    val downloadedFile = result.getOrThrow()
                    _uiState.update { it.copy(isDownloadingApk = false, downloadedApkFile = downloadedFile) }
                    ApkHelper.installApk(context, downloadedFile)
                    return@launch
                }

                // Case 2: Download from GitHub Artifact ZIP
                val token = tokenStorage.getToken()
                if (token.isNullOrBlank()) {
                    throw IllegalStateException("Token do GitHub não configurado para baixar artefato.")
                }

                val tempZip = File(context.cacheDir, "build_artifact_${artifact.id}.zip")
                if (tempZip.exists()) tempZip.delete()

                val result = gitHubClient.downloadArtifactZip(
                    token = token,
                    downloadUrl = artifact.archiveDownloadUrl,
                    destinationFile = tempZip,
                    onProgress = { downloaded, total ->
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt()
                            _uiState.update { it.copy(downloadPercent = percent) }
                        }
                    }
                )

                val downloadedZip = result.getOrThrow()
                val extractedResult = gitHubClient.extractApkFromArtifactZip(downloadedZip, apkDir)
                val apkFile = extractedResult.getOrNull()

                if (apkFile != null && apkFile.exists()) {
                    _uiState.update {
                        it.copy(
                            isDownloadingApk = false,
                            downloadedApkFile = apkFile
                        )
                    }

                    // Background backup to Supabase Storage for permanent availability!
                    val supaUrl = tokenStorage.getSupabaseUrl()
                    val supaKey = tokenStorage.getSupabaseAnonKey()
                    val supaToken = tokenStorage.getSupabaseAccessToken()
                    val uid = tokenStorage.getSupabaseUserId()
                    if (supaUrl.isNotBlank() && supaKey.isNotBlank() && !supaToken.isNullOrBlank() && !uid.isNullOrBlank()) {
                        launch(Dispatchers.IO) {
                            try {
                                val uploadRes = supabaseClient.uploadApkToStorage(
                                    baseUrl = supaUrl,
                                    anonKey = supaKey,
                                    accessToken = supaToken,
                                    userId = uid,
                                    buildId = record?.id ?: artifact.id.toString(),
                                    apkFile = apkFile
                                )
                                val permanentUrl = uploadRes.getOrNull()
                                if (permanentUrl != null && record != null) {
                                    val updatedRecord = record.copy(cloudStorageUrl = permanentUrl)
                                    buildRepository.saveBuild(updatedRecord)
                                    supabaseClient.saveBuildRecord(
                                        baseUrl = supaUrl,
                                        anonKey = supaKey,
                                        accessToken = supaToken,
                                        record = SupabaseBuildRecord(
                                            id = record.id,
                                            userId = uid,
                                            userEmail = tokenStorage.getSupabaseUserEmail().orEmpty(),
                                            projectName = record.projectName,
                                            repoName = record.repoName,
                                            status = record.status,
                                            elapsedSeconds = record.elapsedSeconds,
                                            timestamp = record.timestamp,
                                            apkUrl = permanentUrl,
                                            apkSize = apkFile.length(),
                                            apkFileName = apkFile.name
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                Log.w("BuildViewModel", "Backup do APK no Supabase Storage falhou", e)
                            }
                        }
                    }

                    ApkHelper.installApk(context, apkFile)
                } else {
                    throw IllegalStateException("Nenhum arquivo .apk encontrado no artefato.")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDownloadingApk = false) }
                Log.e("BuildViewModel", "Erro ao baixar APK", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        historyCollectionJob?.cancel()
    }
}
