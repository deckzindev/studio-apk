package com.example.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.data.ArtifactItem
import com.example.data.GitHubApiClient
import com.example.data.db.AppDatabase
import com.example.data.db.BuildRecord
import com.example.data.db.BuildRepository
import com.example.util.NotificationHelper
import com.example.util.ZipHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

data class LiveBuildServiceState(
    val isRunning: Boolean = false,
    val stepNumber: Int = 1,
    val statusMessage: String = "",
    val projectName: String = "",
    val elapsedSeconds: Long = 0L,
    val isFinished: Boolean = false,
    val isSuccess: Boolean = false,
    val validApkArtifact: ArtifactItem? = null,
    val errorMessage: String? = null,
    val logs: List<String> = emptyList()
)

class BuildForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.example.service.action.START"
        const val ACTION_UPDATE = "com.example.service.action.UPDATE"
        const val ACTION_START_POLLING = "com.example.service.action.START_POLLING"
        const val ACTION_STOP = "com.example.service.action.STOP"

        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_PROJECT_NAME = "extra_project_name"
        const val EXTRA_STEP_NUMBER = "extra_step_number"
        const val EXTRA_PROGRESS_PERCENT = "extra_progress_percent"
        const val EXTRA_IS_DONE = "extra_is_done"
        const val EXTRA_IS_SUCCESS = "extra_is_success"
        const val EXTRA_APK_PATH = "extra_apk_path"

        const val EXTRA_TOKEN = "extra_token"
        const val EXTRA_OWNER = "extra_owner"
        const val EXTRA_REPO = "extra_repo"
        const val EXTRA_COMMIT_SHA = "extra_commit_sha"
        const val EXTRA_BUILD_ID = "extra_build_id"
        const val EXTRA_START_TIME = "extra_start_time"

        private val _serviceState = MutableStateFlow(LiveBuildServiceState())
        val serviceState = _serviceState.asStateFlow()

        fun appendLog(message: String) {
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val entry = "[$time] $message"
            _serviceState.update { current ->
                current.copy(logs = current.logs + entry)
            }
        }

        fun clearLogs() {
            _serviceState.update { it.copy(logs = emptyList()) }
        }

        fun startService(
            context: Context,
            projectName: String = "Projeto Android",
            stepNumber: Int = 1,
            statusMessage: String = "Iniciando preparação..."
        ) {
            NotificationHelper.setupChannels(context)
            _serviceState.update {
                it.copy(
                    isRunning = true,
                    stepNumber = stepNumber,
                    statusMessage = statusMessage,
                    projectName = projectName,
                    isFinished = false,
                    isSuccess = false,
                    validApkArtifact = null,
                    errorMessage = null
                )
            }
            val intent = Intent(context, BuildForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_STEP_NUMBER, stepNumber)
                putExtra(EXTRA_STATUS, statusMessage)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateStatus(
            context: Context,
            stepNumber: Int,
            statusMessage: String,
            progressPercent: Int? = null
        ) {
            _serviceState.update {
                it.copy(
                    stepNumber = stepNumber,
                    statusMessage = statusMessage
                )
            }
            val intent = Intent(context, BuildForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_STEP_NUMBER, stepNumber)
                putExtra(EXTRA_STATUS, statusMessage)
                if (progressPercent != null) {
                    putExtra(EXTRA_PROGRESS_PERCENT, progressPercent)
                }
            }
            context.startService(intent)
        }

        fun startPolling(
            context: Context,
            token: String,
            owner: String,
            repo: String,
            commitSha: String,
            projectName: String,
            buildId: String,
            startTime: Long
        ) {
            NotificationHelper.setupChannels(context)
            val intent = Intent(context, BuildForegroundService::class.java).apply {
                action = ACTION_START_POLLING
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_OWNER, owner)
                putExtra(EXTRA_REPO, repo)
                putExtra(EXTRA_COMMIT_SHA, commitSha)
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_BUILD_ID, buildId)
                putExtra(EXTRA_START_TIME, startTime)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun finishService(
            context: Context,
            isSuccess: Boolean,
            projectName: String,
            message: String,
            apkFile: File? = null
        ) {
            NotificationHelper.dismissProgressNotification(context)
            _serviceState.update {
                it.copy(
                    isRunning = false,
                    isFinished = true,
                    isSuccess = isSuccess,
                    statusMessage = message
                )
            }
            val intent = Intent(context, BuildForegroundService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_STATUS, message)
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_IS_DONE, true)
                putExtra(EXTRA_IS_SUCCESS, isSuccess)
                apkFile?.let { putExtra(EXTRA_APK_PATH, it.absolutePath) }
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gitHubClient = GitHubApiClient()
    private var backgroundPollingJob: Job? = null

    private var currentProjectName = "Projeto Android"
    private var lastUpdateTime = 0L
    private var lastStepNumber = -1
    private var lastStatusMessage = ""
    private var lastPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.setupChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val projectName = intent?.getStringExtra(EXTRA_PROJECT_NAME) ?: currentProjectName
        currentProjectName = projectName
        val statusMessage = intent?.getStringExtra(EXTRA_STATUS) ?: "Compilando..."
        val stepNumber = intent?.getIntExtra(EXTRA_STEP_NUMBER, 1) ?: 1
        val hasPercent = intent?.hasExtra(EXTRA_PROGRESS_PERCENT) == true
        val progressPercent = if (hasPercent) intent.getIntExtra(EXTRA_PROGRESS_PERCENT, 0) else null

        val isDone = intent?.getBooleanExtra(EXTRA_IS_DONE, false) ?: false
        val isSuccess = intent?.getBooleanExtra(EXTRA_IS_SUCCESS, false) ?: false
        val apkPath = intent?.getStringExtra(EXTRA_APK_PATH)
        val apkFile = apkPath?.let { File(it) }

        when (action) {
            ACTION_START -> {
                lastStepNumber = stepNumber
                lastStatusMessage = statusMessage
                lastUpdateTime = System.currentTimeMillis()

                val notification = buildProgressNotification(stepNumber, statusMessage, projectName, progressPercent, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NotificationHelper.PROGRESS_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NotificationHelper.PROGRESS_NOTIFICATION_ID, notification)
                }
            }

            ACTION_UPDATE -> {
                val now = System.currentTimeMillis()
                val isStepChanged = stepNumber != lastStepNumber
                val isPercentChanged = progressPercent != null && progressPercent != lastPercent
                val timePassed = (now - lastUpdateTime) >= 500L

                if (isStepChanged || isPercentChanged || (timePassed && statusMessage != lastStatusMessage)) {
                    lastStepNumber = stepNumber
                    lastStatusMessage = statusMessage
                    if (progressPercent != null) lastPercent = progressPercent
                    lastUpdateTime = now

                    val notification = buildProgressNotification(stepNumber, statusMessage, currentProjectName, progressPercent, 0)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    manager.notify(NotificationHelper.PROGRESS_NOTIFICATION_ID, notification)
                }
            }

            ACTION_START_POLLING -> {
                val token = intent?.getStringExtra(EXTRA_TOKEN).orEmpty()
                val owner = intent?.getStringExtra(EXTRA_OWNER).orEmpty()
                val repo = intent?.getStringExtra(EXTRA_REPO).orEmpty()
                val commitSha = intent?.getStringExtra(EXTRA_COMMIT_SHA).orEmpty()
                val buildId = intent?.getStringExtra(EXTRA_BUILD_ID).orEmpty()
                val startTime = intent?.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis()) ?: System.currentTimeMillis()

                val initialNotif = buildProgressNotification(3, "Compilando no GitHub Actions...", currentProjectName, null, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NotificationHelper.PROGRESS_NOTIFICATION_ID,
                        initialNotif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NotificationHelper.PROGRESS_NOTIFICATION_ID, initialNotif)
                }

                startIndependentBackgroundPolling(token, owner, repo, commitSha, currentProjectName, buildId, startTime)
            }

            ACTION_STOP -> {
                backgroundPollingJob?.cancel()
                NotificationHelper.dismissProgressNotification(this)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                if (isDone) {
                    NotificationHelper.showCompletionAlert(
                        context = this,
                        isSuccess = isSuccess,
                        projectName = projectName,
                        message = statusMessage,
                        apkFile = apkFile
                    )
                }
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startIndependentBackgroundPolling(
        token: String,
        owner: String,
        repo: String,
        commitSha: String,
        projectName: String,
        buildId: String,
        startTime: Long
    ) {
        backgroundPollingJob?.cancel()
        backgroundPollingJob = serviceScope.launch {
            val database = AppDatabase.getDatabase(applicationContext)
            val buildRepository = BuildRepository(database.buildRecordDao())

            var runId: Long? = null
            var retryCount = 0

            // Step 3: Fast & resilient run discovery
            while (isActive && runId == null && retryCount < 40) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                _serviceState.update {
                    it.copy(
                        isRunning = true,
                        stepNumber = 3,
                        statusMessage = "Aguardando servidor iniciar...",
                        projectName = projectName,
                        elapsedSeconds = elapsed
                    )
                }
                updateNotificationLive(3, "Aguardando servidor iniciar...", projectName, elapsed)

                delay(2000)

                // 1. Try finding by head_sha directly
                val directRun = gitHubClient.findWorkflowRunForCommit(token, owner, repo, commitSha).getOrNull()
                if (directRun != null) {
                    runId = directRun.id
                    appendLog("[INFO] Workflow localizado no GitHub Actions: Run #${directRun.runNumber ?: 1} (ID: $runId)")
                    appendLog("[INFO] Status inicial do runner: ${directRun.status ?: "in_progress"}")
                    break
                }

                // 2. Fallback: check top recent runs
                val recentRuns = gitHubClient.getRecentWorkflowRuns(token, owner, repo, 5).getOrNull()
                val matchedRecent = recentRuns?.firstOrNull { it.headSha == commitSha }
                    ?: if (retryCount > 8) {
                        recentRuns?.firstOrNull { it.status == "in_progress" || it.status == "queued" }
                    } else null

                if (matchedRecent != null) {
                    runId = matchedRecent.id
                    appendLog("[INFO] Workflow localizado no GitHub Actions: Run #${matchedRecent.runNumber ?: 1} (ID: $runId)")
                    appendLog("[INFO] Status inicial do runner: ${matchedRecent.status ?: "in_progress"}")
                    break
                }

                retryCount++
            }

            if (runId == null) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val errMsg = "Tempo limite aguardando o servidor iniciar."
                appendLog("[ERRO] $errMsg")
                _serviceState.update {
                    it.copy(
                        isRunning = false,
                        isFinished = true,
                        isSuccess = false,
                        statusMessage = errMsg,
                        errorMessage = errMsg,
                        elapsedSeconds = elapsed
                    )
                }
                saveRecordToDb(buildRepository, buildId, projectName, repo, "FAILURE", elapsed, null)

                NotificationHelper.dismissProgressNotification(applicationContext)
                ServiceCompat.stopForeground(this@BuildForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                NotificationHelper.showCompletionAlert(applicationContext, false, projectName, errMsg)
                stopSelf()
                return@launch
            }

            var isCompleted = false
            var consecutiveErrors = 0
            val loggedSteps = mutableSetOf<String>()

            while (isActive && !isCompleted) {
                delay(2500)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000

                // Safety timeout: 25 minutes
                if (elapsed > 25 * 60) {
                    val timeoutMsg = "Tempo limite excedido na compilação."
                    appendLog("[ERRO] $timeoutMsg")
                    _serviceState.update {
                        it.copy(
                            isRunning = false,
                            stepNumber = 3,
                            isFinished = true,
                            isSuccess = false,
                            statusMessage = timeoutMsg,
                            errorMessage = timeoutMsg,
                            elapsedSeconds = elapsed
                        )
                    }
                    saveRecordToDb(buildRepository, buildId, projectName, repo, "FAILURE", elapsed, null, runId)
                    NotificationHelper.dismissProgressNotification(applicationContext)
                    ServiceCompat.stopForeground(this@BuildForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    NotificationHelper.showCompletionAlert(applicationContext, false, projectName, timeoutMsg)
                    stopSelf()
                    return@launch
                }

                val runResult = gitHubClient.getWorkflowRun(token, owner, repo, runId)
                if (runResult.isFailure) {
                    consecutiveErrors++
                    if (consecutiveErrors >= 15) { // 35+ seconds of persistent errors
                        val errMsg = "Erro de conexão persistente com o servidor."
                        appendLog("[ERRO] $errMsg")
                        _serviceState.update {
                            it.copy(
                                isRunning = false,
                                isFinished = true,
                                isSuccess = false,
                                statusMessage = errMsg,
                                errorMessage = errMsg,
                                elapsedSeconds = elapsed
                            )
                        }
                        saveRecordToDb(buildRepository, buildId, projectName, repo, "FAILURE", elapsed, null, runId)
                        NotificationHelper.dismissProgressNotification(applicationContext)
                        ServiceCompat.stopForeground(this@BuildForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        NotificationHelper.showCompletionAlert(applicationContext, false, projectName, errMsg)
                        stopSelf()
                        return@launch
                    }
                    continue
                }

                consecutiveErrors = 0
                val currentRun = runResult.getOrNull() ?: continue

                val status = currentRun.status?.lowercase()
                val conclusion = currentRun.conclusion?.lowercase()

                // Query and log real steps from workflow jobs
                val jobs = gitHubClient.getWorkflowJobs(token, owner, repo, runId).getOrDefault(emptyList())
                for (job in jobs) {
                    for (step in job.steps) {
                        val stepKey = "${job.id}_${step.number}_${step.status}_${step.conclusion}"
                        if (loggedSteps.add(stepKey)) {
                            when {
                                step.conclusion == "success" -> appendLog("[OK] Concluído: ${step.name}")
                                step.conclusion == "failure" -> appendLog("[ERRO] Falha na etapa: ${step.name}")
                                step.status == "in_progress" -> appendLog("[INFO] Executando: ${step.name}...")
                            }
                        }
                    }
                }

                if (status == "in_progress" || status == "queued") {
                    val statusText = if (status == "queued") "Fila do servidor..." else "Compilando no GitHub..."
                    _serviceState.update {
                        it.copy(
                            isRunning = true,
                            stepNumber = 3,
                            statusMessage = statusText,
                            projectName = projectName,
                            elapsedSeconds = elapsed
                        )
                    }
                    updateNotificationLive(3, statusText, projectName, elapsed)
                }

                if (status == "completed") {
                    isCompleted = true

                    val artifactsResult = gitHubClient.getArtifacts(token, owner, repo, runId)
                    val artifacts = artifactsResult.getOrDefault(emptyList())
                    val apkArtifact = artifacts.firstOrNull {
                        it.name.contains("apk", ignoreCase = true) && !it.expired
                    }

                    val isSuccess = conclusion == "success"
                    val finalMsg = if (isSuccess) "Compilação concluída com sucesso!" else "A compilação falhou no servidor."

                    if (isSuccess) {
                        appendLog("[OK] Execução concluída com sucesso no GitHub Actions.")
                        appendLog("[INFO] Verificando artefatos gerados pelo fluxo de compilação...")
                        if (apkArtifact != null) {
                            appendLog("[OK] Artefato APK gerado: ${apkArtifact.name} (${ZipHelper.formatBytes(apkArtifact.sizeInBytes)})")
                            appendLog("[SUCESSO] Aplicativo APK pronto para download e instalação!")
                        } else {
                            appendLog("[INFO] Workflow finalizado. APK gerado com sucesso.")
                        }
                    } else {
                        appendLog("[ERRO] O runner do GitHub Actions finalizou com status: ${conclusion ?: "falha"}")
                        for (job in jobs) {
                            if (job.conclusion == "failure") {
                                appendLog("[ERRO] Etapa com falha no job '${job.name}'")
                                val rawLogs = gitHubClient.getJobLogs(token, owner, repo, job.id).getOrNull()
                                if (!rawLogs.isNullOrBlank()) {
                                    val cleanLines = rawLogs.lines()
                                        .map { it.replace(Regex("\u001B\\[[;\\d]*m"), "").trim() }
                                        .filter { l -> l.contains("FAILURE:", true) || l.contains("error:", true) || l.contains("What went wrong:", true) }
                                        .takeLast(8)
                                    if (cleanLines.isNotEmpty()) {
                                        appendLog("[ERRO] Detalhes do erro no Gradle:")
                                        cleanLines.forEach { appendLog("   $it") }
                                    }
                                }
                            }
                        }
                        appendLog("[ERRO] Processo de compilação interrompido devido a erro.")
                    }

                    _serviceState.update {
                        it.copy(
                            isRunning = false,
                            stepNumber = 3,
                            isFinished = true,
                            isSuccess = isSuccess,
                            statusMessage = finalMsg,
                            validApkArtifact = apkArtifact,
                            elapsedSeconds = elapsed
                        )
                    }

                    saveRecordToDb(buildRepository, buildId, projectName, repo, if (isSuccess) "SUCCESS" else "FAILURE", elapsed, apkArtifact, runId)

                    // Immediate, guaranteed cleanup of progress notification
                    NotificationHelper.dismissProgressNotification(applicationContext)
                    ServiceCompat.stopForeground(this@BuildForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)

                    NotificationHelper.showCompletionAlert(
                        context = applicationContext,
                        isSuccess = isSuccess,
                        projectName = projectName,
                        message = if (isSuccess) "APK pronto para instalação!" else "Falha no processo de compilação.",
                        apkFile = null
                    )

                    stopSelf()
                }
            }
        }
    }

    private fun updateNotificationLive(stepNumber: Int, message: String, projectName: String, elapsedSeconds: Long) {
        val notification = buildProgressNotification(stepNumber, message, projectName, null, elapsedSeconds)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NotificationHelper.PROGRESS_NOTIFICATION_ID, notification)
    }

    private suspend fun saveRecordToDb(
        repo: BuildRepository,
        buildId: String,
        projectName: String,
        repoName: String,
        status: String,
        elapsedSeconds: Long,
        artifact: ArtifactItem?,
        workflowRunId: Long? = null
    ) {
        try {
            val allLogs = _serviceState.value.logs.joinToString("\n")
            val record = BuildRecord(
                id = buildId,
                projectName = projectName,
                repoName = repoName,
                status = status,
                elapsedSeconds = elapsedSeconds,
                timestamp = System.currentTimeMillis(),
                logsText = allLogs.ifBlank { if (status == "SUCCESS") "Compilação concluída com sucesso." else "Compilação finalizou com erro." },
                artifactId = artifact?.id,
                artifactDownloadUrl = artifact?.archiveDownloadUrl,
                artifactSize = artifact?.sizeInBytes,
                apkFileName = artifact?.name,
                workflowRunId = workflowRunId
            )
            repo.saveBuild(record)
        } catch (e: Exception) {
            Log.e("BuildForegroundService", "Erro ao salvar no banco", e)
        }
    }

    private fun buildProgressNotification(
        stepNumber: Int,
        message: String,
        projectName: String,
        progressPercent: Int?,
        elapsedSeconds: Long
    ): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeFormatted = if (elapsedSeconds > 0) {
            val min = elapsedSeconds / 60
            val sec = elapsedSeconds % 60
            String.format(Locale.US, " • %02d:%02d", min, sec)
        } else ""

        val title = "Studio APK • $projectName"
        val contentText = "$message$timeFormatted"

        val builder = NotificationCompat.Builder(this, NotificationHelper.PROGRESS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(pendingIntent)

        if (progressPercent != null && progressPercent in 0..100) {
            builder.setProgress(100, progressPercent, false)
        } else if (stepNumber in 1..3) {
            builder.setProgress(3, stepNumber, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundPollingJob?.cancel()
    }
}
