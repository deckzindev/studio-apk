package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun MainScreen(
    viewModel: BuildViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onZipSelected(uri)
        }
    }

    when (state.currentScreen) {
        AppScreen.TOKEN -> {
            TokenCleanScreen(
                state = state,
                onTokenChanged = viewModel::onTokenInputChanged,
                onSaveClick = viewModel::saveTokenAndCreateRepo,
                modifier = modifier
            )
        }
        AppScreen.SELECT_ZIP -> {
            SelectZipCleanScreen(
                state = state,
                onSelectZipClick = {
                    zipPickerLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                },
                onHistoryClick = viewModel::goToHistoryScreen,
                onChangeTokenClick = viewModel::goToTokenScreen,
                modifier = modifier
            )
        }
        AppScreen.BUILD_PROGRESS -> {
            BuildProgressCleanScreen(
                state = state,
                onBackClick = viewModel::goToSelectZipScreen,
                onHistoryClick = viewModel::goToHistoryScreen,
                onDownloadApkClick = { viewModel.downloadAndOpenApk(context) },
                modifier = modifier
            )
        }
        AppScreen.HISTORY -> {
            HistoryCleanScreen(
                state = state,
                onBackClick = viewModel::goToSelectZipScreen,
                onOpenRecord = viewModel::openHistoryRecord,
                onDeleteRecord = viewModel::deleteHistoryRecord,
                onSyncHistory = { viewModel.syncHistoryFromGitHub(silent = false) },
                onClearAllHistory = viewModel::clearAllHistory,
                onClearSyncMessage = viewModel::clearHistorySyncMessage,
                modifier = modifier
            )
        }
    }
}
