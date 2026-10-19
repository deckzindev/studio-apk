package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRed
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalCardBg
import com.example.ui.theme.TerminalCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.ApkHelper
import com.example.util.ZipHelper
import java.util.Locale

/**
 * Screen 1: Mandatory Authentication Screen (Supabase Auth)
 * First-time opening requires mandatory account creation (Nome, E-mail, Senha).
 * Returning users can log in from any device to access all previous builds and APKs.
 * GitHub token is managed globally via Supabase backend.
 */
@Composable
fun AuthCleanScreen(
    state: BuildUiState,
    onNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onSubmitAuth: () -> Unit,
    onToggleAuthMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = TerminalBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Branding Icon
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(AccentCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.isSignUpMode) Icons.Default.Person else Icons.Default.Lock,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (state.isSignUpMode) "Criar Conta" else "Entrar",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (state.isSignUpMode)
                    "Informe seu nome, e-mail e senha"
                else
                    "Informe seu e-mail e senha para continuar",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Form Fields
            if (state.isSignUpMode) {
                OutlinedTextField(
                    value = state.authNameInput,
                    onValueChange = onNameChanged,
                    label = { Text("Nome Completo") },
                    placeholder = { Text("Ex: Carlos Silva", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = AccentCyan)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_name_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = TerminalCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentCyan,
                        focusedLabelColor = AccentCyan
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = state.authEmailInput,
                onValueChange = onEmailChanged,
                label = { Text("E-mail") },
                placeholder = { Text("seu@email.com", color = TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = AccentCyan)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_email_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = TerminalCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentCyan,
                    focusedLabelColor = AccentCyan
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.authPasswordInput,
                onValueChange = onPasswordChanged,
                label = { Text("Senha") },
                placeholder = { Text("Mínimo 6 caracteres", color = TextMuted) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmitAuth() }),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = AccentCyan)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_password_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = TerminalCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentCyan,
                    focusedLabelColor = AccentCyan
                )
            )

            // Error & Success feedback
            AnimatedVisibility(visible = state.authErrorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = state.authErrorMessage ?: "",
                        color = AccentRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            AnimatedVisibility(visible = state.authSuccessMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentEmerald.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = state.authSuccessMessage ?: "",
                        color = AccentEmerald,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Action Button
            Button(
                onClick = onSubmitAuth,
                enabled = !state.isAuthenticating,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isSignUpMode) AccentEmerald else AccentCyan,
                    contentColor = Color(0xFF042114)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_auth_button")
            ) {
                if (state.isAuthenticating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF042114),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (state.isSignUpMode) "Criando conta..." else "Autenticando...",
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = if (state.isSignUpMode) "Criar Conta" else "Entrar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Toggle Between Sign Up and Login
            TextButton(
                onClick = onToggleAuthMode,
                modifier = Modifier.testTag("toggle_auth_mode_button")
            ) {
                Text(
                    text = if (state.isSignUpMode)
                        "Já possui uma conta? Entrar"
                    else
                        "Criar nova conta",
                    color = AccentCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}


/**
 * Screen 2: Select ZIP Screen (Direct, simple & user-focused)
 */
@Composable
fun SelectZipCleanScreen(
    state: BuildUiState,
    onSelectZipClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = TerminalBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header with User Profile, History & Logout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AccentCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (state.currentUser?.name ?: "U").take(1).uppercase(),
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = state.currentUser?.name ?: "Usuário",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                    }
                }

                // Action buttons: History and Logout
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onHistoryClick,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF161E28),
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier.testTag("open_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = AccentCyan
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.historyList.isNotEmpty()) "${state.historyList.size}" else "Histórico",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onLogoutClick,
                        modifier = Modifier.testTag("logout_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Sair da conta",
                            tint = AccentRed.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Center Content: One big clean hero button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = "Selecionar arquivo",
                            tint = AccentEmerald,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = onSelectZipClick,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentEmerald,
                            contentColor = Color(0xFF042114)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("select_zip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Selecionar Arquivo ZIP",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen 3: Modern, Unified Compilation Screen (Tela Única de Compilação).
 * Shows the entire real compilation process without dividing into stages.
 * Displays all real compilation logs with clean categorization, error highlighting,
 * live auto-scroll, and direct APK download/install actions.
 */
@Composable
fun BuildProgressCleanScreen(
    state: BuildUiState,
    onBackClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onDownloadApkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }

    val isSuccess = state.buildPhase == BuildPhase.COMPLETED_SUCCESS
    val isFailure = state.buildPhase == BuildPhase.COMPLETED_FAILURE || state.buildPhase == BuildPhase.ERROR
    val isFinished = isSuccess || isFailure

    val timeFormatted = remember(state.elapsedSeconds) {
        val min = state.elapsedSeconds / 60
        val sec = state.elapsedSeconds % 60
        String.format(Locale.US, "%02d:%02d", min, sec)
    }

    val errorCount = remember(state.buildLogs) {
        state.buildLogs.count { it.contains("[ERRO]", ignoreCase = true) }
    }
    val warnCount = remember(state.buildLogs) {
        state.buildLogs.count { it.contains("[AVISO]", ignoreCase = true) || it.contains("[WARN]", ignoreCase = true) }
    }

    // Auto-scroll to latest log entry
    LaunchedEffect(state.buildLogs.size, autoScroll) {
        if (autoScroll && state.buildLogs.isNotEmpty()) {
            listState.animateScrollToItem(state.buildLogs.size - 1)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = TerminalBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Modern Top Bar (Clean, single-screen header)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalCardBg)
                    .border(1.dp, TerminalCardBorder)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = state.selectedProject?.fileName
                                ?: state.activeHistoryRecord?.projectName
                                ?: "Compilação Android",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        // Live Status Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            if (isSuccess) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AccentEmerald)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Concluído • $timeFormatted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentEmerald,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else if (isFailure) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AccentRed)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Falha na compilação • $timeFormatted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(9.dp),
                                    color = AccentCyan,
                                    strokeWidth = 1.8.dp
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Compilando • $timeFormatted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentCyan,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Quick Action Icons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val allLogs = state.buildLogs.joinToString("\n")
                            ApkHelper.copyToClipboard(context, allLogs, "Studio APK Logs")
                        },
                        modifier = Modifier.testTag("copy_logs_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copiar todos os logs",
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onHistoryClick,
                        modifier = Modifier.testTag("progress_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "Ver Histórico",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Compact Status / Result Banner (No stages, direct feedback)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (isSuccess) {
                    val artifact = state.validApkArtifact
                    val sizeFormatted = artifact?.let { ZipHelper.formatBytes(it.sizeInBytes) } ?: ""

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0C2417))
                            .border(1.dp, AccentEmerald.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(AccentEmerald.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = AccentEmerald,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "APK Gerado com Sucesso!",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Processo finalizado em $timeFormatted${if (sizeFormatted.isNotBlank()) " • $sizeFormatted" else ""}",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                } else if (isFailure) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF281114))
                            .border(1.dp, AccentRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(AccentRed.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Error,
                                    contentDescription = null,
                                    tint = AccentRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Falha na Compilação",
                                    fontWeight = FontWeight.Bold,
                                    color = AccentRed,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = state.buildStatusMessage.ifBlank { "Identifique o motivo nos logs abaixo em vermelho." },
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                } else {
                    // Running Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TerminalCardBg)
                            .border(1.dp, TerminalCardBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = AccentCyan,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = state.buildStatusMessage.ifBlank { "Compilando aplicativo no GitHub Actions..." },
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.5.dp)),
                                color = AccentCyan,
                                trackColor = Color(0xFF1E2838)
                            )
                        }
                    }
                }
            }

            // Central Log Terminal Card (Clean, organized, real compilation logs)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF090D14))
                    .border(1.dp, Color(0xFF1E2838), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Terminal Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121824))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Terminal,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LOGS REAIS",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // Total Count Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E2838))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${state.buildLogs.size}",
                                    fontSize = 10.5.sp,
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Error Count Badge (if any)
                            if (errorCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AccentRed.copy(alpha = 0.2f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$errorCount erro(s)",
                                        fontSize = 10.5.sp,
                                        color = AccentRed,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Warning Count Badge (if any)
                            if (warnCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AccentAmber.copy(alpha = 0.2f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$warnCount aviso(s)",
                                        fontSize = 10.5.sp,
                                        color = AccentAmber,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Terminal Controls: Auto-Scroll toggle & Copy
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Auto-scroll toggle pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (autoScroll) Color(0xFF162536) else Color(0xFF161A22))
                                    .clickable { autoScroll = !autoScroll }
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Auto-rolagem",
                                        tint = if (autoScroll) AccentCyan else TextMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (autoScroll) "Auto ON" else "Auto OFF",
                                        fontSize = 10.sp,
                                        color = if (autoScroll) AccentCyan else TextMuted,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Copy logs icon button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E2838))
                                    .clickable {
                                        val allLogs = state.buildLogs.joinToString("\n")
                                        ApkHelper.copyToClipboard(context, allLogs, "Studio APK Logs")
                                    }
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copiar",
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Terminal Logs Body
                    if (state.buildLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = AccentCyan,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Iniciando compilação e aguardando logs...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            itemsIndexed(state.buildLogs) { index, rawLine ->
                                RealLogLineItem(
                                    lineNumber = index + 1,
                                    raw = rawLine
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Actions (Clean, direct buttons)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalCardBg)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (isSuccess && state.validApkArtifact != null) {
                    val artifact = state.validApkArtifact
                    val sizeFormatted = ZipHelper.formatBytes(artifact.sizeInBytes)

                    Button(
                        onClick = onDownloadApkClick,
                        enabled = !state.isDownloadingApk,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentEmerald,
                            contentColor = Color(0xFF042114)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("download_apk_button")
                    ) {
                        if (state.isDownloadingApk) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF042114),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (state.downloadPercent > 0) "Baixando APK (${state.downloadPercent}%)..." else "Baixando APK...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Android,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (state.downloadedApkFile != null) "Instalar Aplicativo" else "Instalar Aplicativo ($sizeFormatted)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isFinished) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onHistoryClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E2838),
                                contentColor = TextPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("view_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AccentCyan
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ver Histórico", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = onBackClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E2838),
                                contentColor = TextPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("new_project_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = TextMuted
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isSuccess) "Novo Projeto" else "Tentar Outro", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    // While running: Option to minimize or view history
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onBackClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1A2230),
                                contentColor = TextSecondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Text("Executar em 2º Plano", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = onHistoryClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1A2230),
                                contentColor = TextSecondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = AccentCyan
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Ver Histórico", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Log Line Composable:
 * Parses timestamps, level tags ([OK], [ERRO], [AVISO], [INFO]), and renders with clear
 * visual hierarchy and immediate error identification.
 */
@Composable
private fun RealLogLineItem(
    lineNumber: Int,
    raw: String
) {
    var text = raw.trim()
    var time: String? = null

    // Extract [HH:mm:ss] timestamp if present
    if (text.startsWith("[") && text.length >= 10 && text[9] == ']' && text.substring(1, 9).count { it == ':' } == 2) {
        time = text.substring(1, 9)
        text = text.substring(10).trim()
    }

    val level = when {
        text.startsWith("[OK]", true) -> "OK"
        text.startsWith("[SUCESSO]", true) -> "SUCESSO"
        text.startsWith("[ERRO]", true) -> "ERRO"
        text.startsWith("[AVISO]", true) || text.startsWith("[WARN]", true) -> "AVISO"
        text.startsWith("[INFO]", true) -> "INFO"
        text.startsWith("[AGUARDANDO]", true) -> "AGUARDANDO"
        else -> null
    }

    val message = if (level != null) {
        text.substringAfter("]").trim()
    } else {
        text
    }

    val isError = level == "ERRO" || raw.contains("FAILURE:", true) || raw.contains("error:", true)
    val isOk = level == "OK" || level == "SUCESSO"
    val isWarn = level == "AVISO"

    val rowBg = when {
        isError -> Color(0x28F85149)
        isOk -> Color(0x0C00E599)
        else -> Color.Transparent
    }

    val textColor = when {
        isError -> Color(0xFFFF7B72)
        isOk -> Color(0xFF7EE787)
        isWarn -> AccentAmber
        else -> Color(0xFFD2D8E0)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(rowBg)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Line number
        Text(
            text = String.format(Locale.US, "%02d", lineNumber),
            color = Color(0xFF485160),
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(22.dp)
        )

        // Timestamp
        if (time != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = time,
                color = Color(0xFF6E7681),
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Level Pill
        if (level != null) {
            val pillBg = when (level) {
                "ERRO" -> AccentRed.copy(alpha = 0.25f)
                "OK", "SUCESSO" -> AccentEmerald.copy(alpha = 0.2f)
                "AVISO" -> AccentAmber.copy(alpha = 0.25f)
                else -> AccentCyan.copy(alpha = 0.18f)
            }
            val pillColor = when (level) {
                "ERRO" -> AccentRed
                "OK", "SUCESSO" -> AccentEmerald
                "AVISO" -> AccentAmber
                else -> AccentCyan
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(pillBg)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = level,
                    color = pillColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Log message
        Text(
            text = message,
            color = textColor,
            fontSize = 11.5.sp,
            fontWeight = if (isError) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

