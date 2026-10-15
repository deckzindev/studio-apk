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
import com.example.data.TokenStorage
import com.example.data.db.AppDatabase
import com.example.data.db.BuildRecord
import com.example.data.db.BuildRepository
import com.example.service.BuildForegroundService
import com.example.util.ApkHelper
import com.example.util.ProjectAnalysis
import com.example.util.ZipHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.random.Random

enum class AppScreen {
    TOKEN,
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
    val currentScreen: AppScreen = AppScreen.TOKEN,
    val tokenInput: String = "",
    val isSavingToken: Boolean = false,
    val tokenUser: GitHubUser? = null,
    val tokenError: String? = null,
    val targetRepo: String = "",

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

    val historyList: List<BuildRecord> = emptyList(),
    val activeHistoryRecord: BuildRecord? = null,
    val isSyncingHistory: Boolean = false,
    val historySyncMessage: String? = null
)

class BuildViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStorage = TokenStorage(application)
    private val gitHubClient = GitHubApiClient()
    private val database = AppDatabase.getDatabase(application)
    private val buildRepository = BuildRepository(database.buildRecordDao())

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    private var currentBuildId: String? = null
    private var currentBuildStartTime: Long = 0L
    private var currentProjectName: String = "Projeto Android"
    private var timerJob: Job? = null

    init {
        // Collect local history safely
        viewModelScope.launch {
            buildRepository.allBuilds
                .catch { e -> Log.e("BuildViewModel", "Erro ao carregar histórico: ${e.message}", e) }
                .collect { builds -> _uiState.update { it.copy(historyList = builds) } }
        }

        // Listen to background service updates for real-time progress
        viewModelScope.launch {
            BuildForegroundService.serviceState.collect { serviceUpdate ->
                if (serviceUpdate.isRunning) {
                    val stepNum = serviceUpdate.stepNumber
                    val phase = when (stepNum) {
                        1 -> BuildPhase.PREPARING
                        2 -> BuildPhase.UPLOADING
                        3 -> BuildPhase.COMPILING
                        4 -> BuildPhase.COMPLETED_SUCCESS
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

        val savedToken = tokenStorage.getToken()
        val savedUsername = tokenStorage.getUsername()
        val savedAvatar = tokenStorage.getAvatarUrl()
        val savedDisplayName = tokenStorage.getUserDisplayName()
        val savedRepo = tokenStorage.getTargetRepo()

        if (!savedToken.isNullOrBlank()) {
            val cachedUser = if (!savedUsername.isNullOrBlank()) {
                GitHubUser(
                    login = savedUsername,
                    name = savedDisplayName,
                    avatarUrl = savedAvatar
                )
            } else null

            _uiState.update {
                it.copy(
                    currentScreen = AppScreen.SELECT_ZIP,
                    tokenInput = savedToken,
                    tokenUser = cachedUser,
                    targetRepo = savedRepo
                )
            }

            syncHistoryFromGitHub(silent = true)
        } else {
            _uiState.update { it.copy(currentScreen = AppScreen.TOKEN) }
        }
    }

    fun onTokenInputChanged(token: String) {
        _uiState.update { it.copy(tokenInput = token, tokenError = null) }
    }

    fun goToHistoryScreen() {
        _uiState.update { it.copy(currentScreen = AppScreen.HISTORY) }
        syncHistoryFromGitHub(silent = true)
    }

    fun goToSelectZipScreen() {
        _uiState.update { it.copy(currentScreen = AppScreen.SELECT_ZIP) }
    }

    fun goToTokenScreen() {
        _uiState.update { it.copy(currentScreen = AppScreen.TOKEN) }
    }

    fun openHistoryRecord(record: BuildRecord) {
        val artifactItem = if (record.artifactId != null && record.artifactDownloadUrl != null) {
            ArtifactItem(
                id = record.artifactId,
                name = record.apkFileName ?: "app-debug.apk",
                sizeInBytes = record.artifactSize ?: 0,
                archiveDownloadUrl = record.artifactDownloadUrl
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

    fun syncHistoryFromGitHub(silent: Boolean = false) {
        val token = tokenStorage.getToken()
        val user = _uiState.value.tokenUser ?: (tokenStorage.getUsername()?.let {
            GitHubUser(login = it, name = tokenStorage.getUserDisplayName(), avatarUrl = tokenStorage.getAvatarUrl())
        })
        val repo = _uiState.value.targetRepo.ifBlank { tokenStorage.getTargetRepo() }

        if (token.isNullOrBlank() || user == null || repo.isBlank()) {
            if (!silent) {
                _uiState.update { it.copy(historySyncMessage = "Token ou repositório não configurados.") }
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingHistory = true, historySyncMessage = null) }
            try {
                val runsResult = gitHubClient.getRecentWorkflowRuns(token, user.login, repo, perPage = 30)
                val runs = runsResult.getOrThrow()

                var syncedCount = 0
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
                                name = existing.apkFileName ?: "app-debug.apk",
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
                        apkFileName = artifactItem?.name
                    )

                    buildRepository.saveBuild(record)
                    syncedCount++
                }

                _uiState.update {
                    it.copy(
                        isSyncingHistory = false,
                        historySyncMessage = if (!silent) {
                            if (syncedCount > 0) "Histórico sincronizado ($syncedCount compilações encontradas)."
                            else "Nenhuma compilação encontrada no GitHub."
                        } else null
                    )
                }
            } catch (e: Exception) {
                Log.e("BuildViewModel", "Erro ao sincronizar com GitHub", e)
                _uiState.update {
                    it.copy(
                        isSyncingHistory = false,
                        historySyncMessage = if (!silent) "Erro ao sincronizar: ${e.localizedMessage}" else null
                    )
                }
            }
        }
    }

    private fun parseIsoTimestamp(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(isoString)?.time
        } catch (_: Exception) {
            try {
                val alt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                alt.parse(isoString.take(19))?.time
            } catch (_: Exception) {
                null
            }
        }
    }

    fun saveTokenAndCreateRepo() {
        val token = _uiState.value.tokenInput.trim()
        if (token.isBlank()) {
            _uiState.update { it.copy(tokenError = "Insira seu token do GitHub.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingToken = true, tokenError = null) }

            val verifyResult = gitHubClient.verifyToken(token)
            if (verifyResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isSavingToken = false,
                        tokenError = verifyResult.exceptionOrNull()?.localizedMessage ?: "Token inválido."
                    )
                }
                return@launch
            }

            val user = verifyResult.getOrThrow()
            val randomSuffix = Random.nextInt(100000, 999999)
            val randomRepoName = "apk-builder-$randomSuffix"

            val repoResult = gitHubClient.createRandomRepo(token, randomRepoName)
            if (repoResult.isFailure) {
                val fallbackResult = gitHubClient.getOrCreateRepo(token, user.login, randomRepoName)
                if (fallbackResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isSavingToken = false,
                            tokenError = "Erro ao criar repositório no GitHub: ${fallbackResult.exceptionOrNull()?.localizedMessage}"
                        )
                    }
                    return@launch
                }
            }

            tokenStorage.saveToken(token)
            tokenStorage.saveUserInfo(user)
            tokenStorage.saveTargetRepo(randomRepoName)

            _uiState.update {
                it.copy(
                    isSavingToken = false,
                    tokenUser = user,
                    targetRepo = randomRepoName,
                    currentScreen = AppScreen.SELECT_ZIP,
                    tokenError = null
                )
            }
        }
    }

    fun onZipSelected(uri: Uri) {
        val newBuildId = UUID.randomUUID().toString()
        currentBuildId = newBuildId
        currentBuildStartTime = System.currentTimeMillis()
        currentProjectName = "Projeto Android"

        viewModelScope.launch {
            val initialSteps = BuildStepHelper.createSteps(1)

            _uiState.update {
                it.copy(
                    isReadingZip = true,
                    currentScreen = AppScreen.BUILD_PROGRESS,
                    buildPhase = BuildPhase.PREPARING,
                    currentStepNumber = 1,
                    buildStatusMessage = "Lendo e verificando arquivo ZIP...",
                    steps = initialSteps,
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

            // Save initial in-progress record to database
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
        val token = tokenStorage.getToken()
        val user = state.tokenUser
        val project = state.selectedProject
        val repoName = state.targetRepo.ifBlank { tokenStorage.getTargetRepo() }
        val buildId = currentBuildId ?: UUID.randomUUID().toString()

        if (token.isNullOrBlank() || user == null || project == null) {
            _uiState.update {
                it.copy(
                    buildPhase = BuildPhase.ERROR,
                    buildStatusMessage = "Configuração incompleta.",
                    steps = BuildStepHelper.createSteps(1, isError = true)
                )
            }
            updateFinalRecord(buildId, "FAILURE", null)
            return
        }

        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                // Step 2: Uploading
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

                // Step 3: Compiling in cloud - Hand off to independent background service!
                _uiState.update {
                    it.copy(
                        buildPhase = BuildPhase.COMPILING,
                        currentStepNumber = 3,
                        buildStatusMessage = "Compilando aplicativo no servidor...",
                        steps = BuildStepHelper.createSteps(3)
                    )
                }

                // Delegate polling directly to the Foreground Service so it NEVER stops when app is minimized!
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
        viewModelScope.launch {
            try {
                val record = BuildRecord(
                    id = buildId,
                    projectName = projectName,
                    repoName = _uiState.value.targetRepo.ifBlank { tokenStorage.getTargetRepo() },
                    status = "IN_PROGRESS",
                    elapsedSeconds = 0L,
                    timestamp = System.currentTimeMillis(),
                    logsText = "Compilação em andamento..."
                )
                buildRepository.saveBuild(record)
            } catch (e: Exception) {
                Log.e("BuildViewModel", "Erro ao salvar registro inicial", e)
            }
        }
    }

    private fun updateFinalRecord(buildId: String, status: String, artifact: ArtifactItem?) {
        viewModelScope.launch {
            try {
                val record = BuildRecord(
                    id = buildId,
                    projectName = currentProjectName,
                    repoName = _uiState.value.targetRepo.ifBlank { tokenStorage.getTargetRepo() },
                    status = status,
                    elapsedSeconds = _uiState.value.elapsedSeconds,
                    timestamp = if (currentBuildStartTime > 0L) currentBuildStartTime else System.currentTimeMillis(),
                    logsText = if (status == "SUCCESS") "Compilação concluída com sucesso." else "Compilação finalizou com erro.",
                    artifactId = artifact?.id,
                    artifactDownloadUrl = artifact?.archiveDownloadUrl,
                    artifactSize = artifact?.sizeInBytes,
                    apkFileName = artifact?.name
                )
                buildRepository.saveBuild(record)
            } catch (e: Exception) {
                Log.e("BuildViewModel", "Erro ao atualizar registro final", e)
            }
        }
    }

    fun downloadAndOpenApk(context: Context) {
        val token = tokenStorage.getToken() ?: return
        val artifact = _uiState.value.validApkArtifact ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingApk = true, downloadPercent = 0) }
            try {
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
                val apkDir = File(context.filesDir, "downloaded_apks")
                val extractedResult = gitHubClient.extractApkFromArtifactZip(downloadedZip, apkDir)
                val apkFile = extractedResult.getOrNull()

                if (apkFile != null && apkFile.exists()) {
                    _uiState.update {
                        it.copy(
                            isDownloadingApk = false,
                            downloadedApkFile = apkFile
                        )
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
    }
}
