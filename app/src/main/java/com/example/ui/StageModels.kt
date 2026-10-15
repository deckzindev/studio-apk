package com.example.ui

enum class BuildStepState {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    ERROR
}

data class SimpleBuildStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val state: BuildStepState = BuildStepState.PENDING
)

object BuildStepHelper {
    fun createSteps(currentStep: Int, isError: Boolean = false, isCompleted: Boolean = false): List<SimpleBuildStep> {
        val step1State = when {
            isCompleted || currentStep > 1 -> BuildStepState.COMPLETED
            currentStep == 1 && isError -> BuildStepState.ERROR
            currentStep == 1 -> BuildStepState.IN_PROGRESS
            else -> BuildStepState.PENDING
        }

        val step2State = when {
            isCompleted || currentStep > 2 -> BuildStepState.COMPLETED
            currentStep == 2 && isError -> BuildStepState.ERROR
            currentStep == 2 -> BuildStepState.IN_PROGRESS
            else -> BuildStepState.PENDING
        }

        val step3State = when {
            isCompleted || currentStep > 3 -> BuildStepState.COMPLETED
            currentStep == 3 && isError -> BuildStepState.ERROR
            currentStep == 3 -> BuildStepState.IN_PROGRESS
            else -> BuildStepState.PENDING
        }

        val step4State = when {
            isCompleted -> BuildStepState.COMPLETED
            isError && currentStep >= 4 -> BuildStepState.ERROR
            currentStep == 4 -> BuildStepState.IN_PROGRESS
            else -> BuildStepState.PENDING
        }

        return listOf(
            SimpleBuildStep(
                stepNumber = 1,
                title = "Preparação do Projeto",
                description = "Lendo e validando arquivos do projeto",
                state = step1State
            ),
            SimpleBuildStep(
                stepNumber = 2,
                title = "Envio para a Nuvem",
                description = "Transferindo arquivos com segurança",
                state = step2State
            ),
            SimpleBuildStep(
                stepNumber = 3,
                title = "Compilação no Servidor",
                description = "Gerando o aplicativo Android (APK)",
                state = step3State
            ),
            SimpleBuildStep(
                stepNumber = 4,
                title = "Aplicativo Pronto",
                description = "Empacotado e pronto para instalação",
                state = step4State
            )
        )
    }
}
