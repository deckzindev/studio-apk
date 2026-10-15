package com.example.ui

enum class StageStatus {
    PENDING,
    IN_PROGRESS,
    SUCCESS,
    FAILURE
}

data class BuildStageItem(
    val key: String,
    val stepNumber: Int,
    val title: String,
    val subtitle: String,
    val status: StageStatus = StageStatus.PENDING,
    val lines: List<String> = emptyList()
)

object StageParser {

    fun parseStages(
        rawLogs: String,
        buildPhase: BuildPhase
    ): List<BuildStageItem> {
        val allLines = rawLogs.lines()

        val prepLines = mutableListOf<String>()
        val uploadLines = mutableListOf<String>()
        val queueLines = mutableListOf<String>()
        val envLines = mutableListOf<String>()
        val compileLines = mutableListOf<String>()
        val artifactLines = mutableListOf<String>()

        var currentCategory = 0 // 0: prep, 1: upload, 2: queue, 3: env, 4: compile, 5: artifact

        for (line in allLines) {
            val lower = line.lowercase()
            when {
                lower.contains("arquivo zip selecionado") || lower.contains("processando código-fonte") ||
                        lower.contains("projeto:") || lower.contains("estrutura android") ||
                        lower.contains("lendo arquivo zip") -> {
                    currentCategory = 0
                }
                lower.contains("enviando código-fonte") || lower.contains("enviando arquivos") ||
                        lower.contains("criando árvore git") || lower.contains("criando commit") ||
                        lower.contains("commit criado") || lower.contains("repositório alvo:") ||
                        lower.contains("via git blobs") -> {
                    currentCategory = 1
                }
                lower.contains("aguardando github actions") || lower.contains("alocando máquina virtual") ||
                        lower.contains("iniciou o build") || lower.contains("disparando github actions") ||
                        lower.contains("run id:") -> {
                    currentCategory = 2
                }
                lower.contains("checkout") || lower.contains("actions/checkout") ||
                        lower.contains("setup jdk") || lower.contains("set up jdk") ||
                        lower.contains("prepare gradle") || lower.contains("preparando gradle") ||
                        lower.contains("setup-java") || lower.contains("setup-gradle") ||
                        lower.contains("grant execute permission") -> {
                    currentCategory = 3
                }
                lower.contains("assembledebug") || lower.contains("assemblerelease") ||
                        lower.contains("compile debug apk") || lower.contains("build debug apk") ||
                        lower.contains("iniciando compilação do apk") || lower.contains("> task :") ||
                        lower.contains("compiledebugkotlin") || lower.contains("build successful") ||
                        lower.contains("build failed") || lower.contains(":app:") -> {
                    currentCategory = 4
                }
                lower.contains("upload apk artifact") || lower.contains("verificando artefato") ||
                        lower.contains("apk disponível") || lower.contains("upload-artifact") ||
                        lower.contains("artefato apk gerado") || lower.contains("compilação concluída") -> {
                    currentCategory = 5
                }
            }

            when (currentCategory) {
                0 -> prepLines.add(line)
                1 -> uploadLines.add(line)
                2 -> queueLines.add(line)
                3 -> envLines.add(line)
                4 -> compileLines.add(line)
                5 -> artifactLines.add(line)
            }
        }

        val hasFailed = buildPhase == BuildPhase.COMPLETED_FAILURE || buildPhase == BuildPhase.ERROR
        val isSuccess = buildPhase == BuildPhase.COMPLETED_SUCCESS

        // If completed successfully, all 6 stages are SUCCESS
        if (isSuccess) {
            return listOf(
                BuildStageItem(
                    key = "prep",
                    stepNumber = 1,
                    title = "1. Preparação do Projeto",
                    subtitle = "Leitura do arquivo ZIP e verificação dos arquivos Android",
                    status = StageStatus.SUCCESS,
                    lines = prepLines.filter { it.isNotBlank() }
                ),
                BuildStageItem(
                    key = "upload",
                    stepNumber = 2,
                    title = "2. Envio dos Arquivos",
                    subtitle = "Transferência segura do código para a nuvem",
                    status = StageStatus.SUCCESS,
                    lines = uploadLines.filter { it.isNotBlank() }
                ),
                BuildStageItem(
                    key = "queue",
                    stepNumber = 3,
                    title = "3. Inicialização da Nuvem",
                    subtitle = "Alocação do servidor Ubuntu no GitHub Actions",
                    status = StageStatus.SUCCESS,
                    lines = queueLines.filter { it.isNotBlank() }
                ),
                BuildStageItem(
                    key = "env",
                    stepNumber = 4,
                    title = "4. Configuração do Ambiente",
                    subtitle = "Instalação do Java JDK 17 e ferramentas Gradle",
                    status = StageStatus.SUCCESS,
                    lines = envLines.filter { it.isNotBlank() }
                ),
                BuildStageItem(
                    key = "compile",
                    stepNumber = 5,
                    title = "5. Compilação do APK",
                    subtitle = "Execução do Gradle e compilação dos binários",
                    status = StageStatus.SUCCESS,
                    lines = compileLines.filter { it.isNotBlank() }
                ),
                BuildStageItem(
                    key = "artifact",
                    stepNumber = 6,
                    title = "6. Finalização do Aplicativo",
                    subtitle = "Geração e empacotamento do APK final",
                    status = StageStatus.SUCCESS,
                    lines = artifactLines.filter { it.isNotBlank() }
                )
            )
        }

        // Determine statuses based on buildPhase and contents
        val prepStatus = when {
            uploadLines.isNotEmpty() || queueLines.isNotEmpty() || envLines.isNotEmpty() || compileLines.isNotEmpty() -> StageStatus.SUCCESS
            buildPhase == BuildPhase.PREPARING -> StageStatus.IN_PROGRESS
            hasFailed && uploadLines.isEmpty() -> StageStatus.FAILURE
            else -> if (prepLines.isNotEmpty()) StageStatus.SUCCESS else StageStatus.PENDING
        }

        val uploadStatus = when {
            queueLines.isNotEmpty() || envLines.isNotEmpty() || compileLines.isNotEmpty() -> StageStatus.SUCCESS
            buildPhase == BuildPhase.UPLOADING -> StageStatus.IN_PROGRESS
            hasFailed && queueLines.isEmpty() && prepStatus == StageStatus.SUCCESS -> StageStatus.FAILURE
            else -> if (uploadLines.isNotEmpty()) StageStatus.SUCCESS else StageStatus.PENDING
        }

        val queueStatus = when {
            envLines.isNotEmpty() || compileLines.isNotEmpty() -> StageStatus.SUCCESS
            buildPhase == BuildPhase.QUEUED -> StageStatus.IN_PROGRESS
            hasFailed && envLines.isEmpty() && uploadStatus == StageStatus.SUCCESS -> StageStatus.FAILURE
            else -> if (queueLines.isNotEmpty()) StageStatus.SUCCESS else StageStatus.PENDING
        }

        val envStatus = when {
            compileLines.isNotEmpty() -> StageStatus.SUCCESS
            buildPhase == BuildPhase.IN_PROGRESS && compileLines.isEmpty() -> StageStatus.IN_PROGRESS
            hasFailed && compileLines.isEmpty() && queueStatus == StageStatus.SUCCESS -> StageStatus.FAILURE
            else -> if (envLines.isNotEmpty()) StageStatus.SUCCESS else StageStatus.PENDING
        }

        val compileStatus = when {
            hasFailed && compileLines.isNotEmpty() -> StageStatus.FAILURE
            buildPhase == BuildPhase.IN_PROGRESS && compileLines.isNotEmpty() -> StageStatus.IN_PROGRESS
            else -> StageStatus.PENDING
        }

        val artifactStatus = when {
            hasFailed -> StageStatus.FAILURE
            else -> StageStatus.PENDING
        }

        return listOf(
            BuildStageItem(
                key = "prep",
                stepNumber = 1,
                title = "1. Preparação do Projeto",
                subtitle = "Leitura do arquivo ZIP e verificação dos arquivos Android",
                status = prepStatus,
                lines = prepLines.filter { it.isNotBlank() }
            ),
            BuildStageItem(
                key = "upload",
                stepNumber = 2,
                title = "2. Envio dos Arquivos",
                subtitle = "Transferência segura do código para a nuvem",
                status = uploadStatus,
                lines = uploadLines.filter { it.isNotBlank() }
            ),
            BuildStageItem(
                key = "queue",
                stepNumber = 3,
                title = "3. Inicialização da Nuvem",
                subtitle = "Alocação do servidor Ubuntu no GitHub Actions",
                status = queueStatus,
                lines = queueLines.filter { it.isNotBlank() }
            ),
            BuildStageItem(
                key = "env",
                stepNumber = 4,
                title = "4. Configuração do Ambiente",
                subtitle = "Instalação do Java JDK 17 e ferramentas Gradle",
                status = envStatus,
                lines = envLines.filter { it.isNotBlank() }
            ),
            BuildStageItem(
                key = "compile",
                stepNumber = 5,
                title = "5. Compilação do APK",
                subtitle = "Execução do Gradle e compilação dos binários",
                status = compileStatus,
                lines = compileLines.filter { it.isNotBlank() }
            ),
            BuildStageItem(
                key = "artifact",
                stepNumber = 6,
                title = "6. Finalização do Aplicativo",
                subtitle = "Geração e empacotamento do APK final",
                status = artifactStatus,
                lines = artifactLines.filter { it.isNotBlank() }
            )
        )
    }

    fun getCurrentStepNumber(stages: List<BuildStageItem>): Int {
        val inProgress = stages.firstOrNull { it.status == StageStatus.IN_PROGRESS }
        if (inProgress != null) return inProgress.stepNumber
        val lastSuccess = stages.lastOrNull { it.status == StageStatus.SUCCESS }
        if (lastSuccess != null) {
            return if (lastSuccess.stepNumber < stages.size) lastSuccess.stepNumber + 1 else stages.size
        }
        return 1
    }
}
