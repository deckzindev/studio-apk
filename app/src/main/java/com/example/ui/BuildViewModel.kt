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
                            steps = BuildStepHelper.createSteps(stepNum)
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
        val supaUrl = tokenStorage.getSupabaseUrl().ifBlank { state.supabaseUrlInput.trim() }
        val supaKey = tokenStorage.getSupabaseAnonKey().ifBlank { state.supabaseAnonKeyInput.trim() }

        if (supaUrl.isBlank() || supaKey.isBlank()) {
            _uiState.update {
                it.copy(
                    authErrorMessage = "Serviço de autenticação temporariamente indisponível."
                )
            }
            return
        }

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
            // Sign Up validation
            val name = state.authNameInput.trim()
            val confirm = state.authConfirmPasswordInput.trim()

            if (name.isBlank()) {
                _uiState.update { it.copy(authErrorMessage = "Por favor, informe seu nome completo.") }
                return
            }
            if (password != confirm) {
                _uiState.update { it.copy(authErrorMessage = "As senhas não coincidem.") }
                return
            }

            // Dedicated repository name for this user: gitbuilder-<userslug>-builds
            val userSlug = name.filter { it.isLetterOrDigit() }.lowercase().take(12).ifBlank {
                email.substringBefore("@").filter { it.isLetterOrDigit() }.lowercase().take(12)
            }
            val dedicatedRepo = "gitbuilder-$userSlug-builds"

            viewModelScope.launch {
                _uiState.update { it.copy(isAuthenticating = true, authErrorMessage = null) }
                try {
                    val signUpResult = supabaseClient.signUp(
                        baseUrl = supaUrl,
                        anonKey = supaKey,
                        name = name,
                        email = email,
                        password = password,
                        dedicatedRepo = dedicatedRepo
                    )

                    val session = signUpResult.getOrThrow()
                    val user = session.user
                    val finalUid = user.id.ifBlank { UUID.randomUUID().toString() }
                    val finalName = user.name.ifBlank { name }
                    var accessToken = session.accessToken
                    var refreshToken = session.refreshToken

                    if (accessToken.isBlank()) {
                        val autoLogin = supabaseClient.signIn(supaUrl, supaKey, email, password).getOrNull()
                        if (autoLogin != null) {
                            accessToken = autoLogin.accessToken
                            refreshToken = autoLogin.refreshToken
                        }
                    }

                    // Save session securely so user only enters credentials once
                    tokenStorage.saveSupabaseSession(
                        userId = finalUid,
                        email = email,
                        name = finalName,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        dedicatedRepo = dedicatedRepo
                    )

                    val persistentUser = SupabaseUser(
                        id = finalUid,
                        email = email,
                        name = finalName,
                        dedicatedRepo = dedicatedRepo
                    )

                    // Fetch and apply the admin's GitHub token configured globally in Supabase
                    var githubToken = tokenStorage.getToken().orEmpty()
                    if (githubToken.isBlank()) {
                        val remoteToken = supabaseClient.fetchGlobalGitHubToken(supaUrl, supaKey).getOrNull()
                        if (!remoteToken.isNullOrBlank()) {
                            tokenStorage.saveToken(remoteToken)
                            githubToken = remoteToken
                        }
                    }

                    if (githubToken.isNotBlank()) {
                        val ghUserResult = gitHubClient.verifyToken(githubToken)
                        val ghUser = ghUserResult.getOrNull()
                        if (ghUser != null) {
                            tokenStorage.saveUserInfo(ghUser)
                            // Create dedicated repository on GitHub automatically for this user in background!
                            gitHubClient.getOrCreateRepo(githubToken, ghUser.login, dedicatedRepo)
                        }
                    }

                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            currentScreen = AppScreen.SELECT_ZIP,
                            currentUser = persistentUser,
                            dedicatedRepo = dedicatedRepo,
                            targetRepo = dedicatedRepo,
                            authErrorMessage = null,
                            authSuccessMessage = "Conta criada com sucesso!"
                        )
                    }

                    observeUserHistory(persistentUser.id)
                    syncHistory(silent = true)

                } catch (e: Exception) {
                    Log.e("BuildViewModel", "Erro no cadastro", e)
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            authErrorMessage = e.localizedMessage ?: "Falha ao cadastrar no Supabase."
                        )
                    }
                }
            }

        } else {
            // Sign In
            viewModelScope.launch {
                _uiState.update { it.copy(isAuthenticating = true, authErrorMessage = null) }
                try {
                    val signInResult = supabaseClient.signIn(
                        baseUrl = supaUrl,
                        anonKey = supaKey,
                        email = email,
                        password = password
                    )

                    val session = signInResult.getOrThrow()
                    val user = session.user
                    val dedicatedRepo = user.dedicatedRepo ?: tokenStorage.getDedicatedRepo() ?: "gitbuilder-${user.email.substringBefore("@")}-builds"

                    tokenStorage.saveSupabaseSession(
                        userId = user.id,
                        email = user.email,
                        name = user.name,
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                        dedicatedRepo = dedicatedRepo
                    )

                    val savedToken = tokenStorage.getToken().orEmpty()
                    val savedUsername = tokenStorage.getUsername()
                    val cachedGithubUser = if (!savedUsername.isNullOrBlank()) {
                        GitHubUser(login = savedUsername, name = tokenStorage.getUserDisplayName(), avatarUrl = tokenStorage.getAvatarUrl())
                    } else null

                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            currentScreen = AppScreen.SELECT_ZIP,
                            currentUser = user,
                            dedicatedRepo = dedicatedRepo,
                            targetRepo = dedicatedRepo,
                            tokenInput = savedToken,
                            tokenUser = cachedGithubUser,
                            authErrorMessage = null
                        )
                    }

                    observeUserHistory(user.id)
                    syncHistory(silent = false)

                } catch (e: Exception) {
                    Log.e("BuildViewModel", "Erro no login", e)
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            authErrorMessage = e.localizedMessage ?: "Falha ao realizar login."
                        )
                    }
                }
            }
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


    // Build flow initiated by user selecting ZIP
    fun onZipSelected(uri: Uri) {
        val newBuildId = UUID.randomUUID().toString()
        currentBuildId = newBuildId
        currentBuildStartTime = System.currentTimeMillis()
        currentProjectName = "Projeto Android"

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
                        buildStatusMessage = "Enviando projeto para a nuvem...",
                        steps = BuildStepHelper.createSteps(2)
                    )
                }
                BuildForegroundService.updateStatus(
                    context = context,
                    stepNumber = 2,
                    statusMessage = "Enviando código para a nuvem..."
                )

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
                    }
                )

                val commitSha = uploadResult.getOrThrow()

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
                activeHistoryRecord = record
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
     * 1. Baixa o histórico do Supabase (nuvem persistente vinculada à conta).
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
                        if (totalSynced > 0) "Histórico sincronizado com a nuvem ($totalSynced registros)."
                        else "Nenhum histórico novo na nuvem."
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
