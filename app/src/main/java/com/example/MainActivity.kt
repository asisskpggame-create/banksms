package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AsisViewModel
import com.example.ui.BanksScreen
import com.example.ui.HomeScreen
import com.example.ui.LogsScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.NotificationHelper

enum class AsisTab(
    val route: String,
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    LOGS("logs", R.string.nav_logs, Icons.Filled.History, Icons.Outlined.History),
    BANKS("banks", R.string.nav_banks, Icons.Filled.AccountBalance, Icons.Outlined.AccountBalance),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannel(this)
        InternetWatchService.register(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AsisAppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsisAppScreen(
    asisViewModel: AsisViewModel = viewModel()
) {
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableStateOf(AsisTab.HOME) }

    val settings by asisViewModel.settingsState.collectAsStateWithLifecycle()
    val isOnline by asisViewModel.isOnlineState.collectAsStateWithLifecycle()
    val allLogs by asisViewModel.allLogs.collectAsStateWithLifecycle()
    val queueCount by asisViewModel.queueCount.collectAsStateWithLifecycle()
    val sentCount by asisViewModel.sentCount.collectAsStateWithLifecycle()
    val failedCount by asisViewModel.failedCount.collectAsStateWithLifecycle()
    val lastSentLog by asisViewModel.lastSentLog.collectAsStateWithLifecycle()
    val testConnectionState by asisViewModel.testConnectionState.collectAsStateWithLifecycle()
    val snackbarMessage by asisViewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            asisViewModel.consumeSnackbar()
        }
    }

    // Permission states
    var hasSmsPermissions by remember {
        mutableStateOf(checkSmsPermissionsGranted(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(checkNotificationPermissionGranted(context))
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasSmsPermissions = checkSmsPermissionsGranted(context)
        hasNotificationPermission = checkNotificationPermissionGranted(context)
    }

    val requestAllNeededPermissions = {
        val perms = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionsLauncher.launch(perms)
    }

    // BackHandler for secondary tabs to return to Home screen
    BackHandler(enabled = currentTab != AsisTab.HOME) {
        currentTab = AsisTab.HOME
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_asis_shield),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                for (tab in AsisTab.entries) {
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        modifier = Modifier.testTag("nav_tab_${tab.route}"),
                        icon = {
                            if (tab == AsisTab.LOGS && (queueCount > 0 || failedCount > 0)) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = if (failedCount > 0) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.tertiary
                                            }
                                        ) {
                                            Text((queueCount + failedCount).toString())
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.titleRes)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.titleRes)
                                )
                            }
                        },
                        label = {
                            Text(text = stringResource(id = tab.titleRes))
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AsisTab.HOME -> HomeScreen(
                    settings = settings,
                    isOnline = isOnline,
                    hasSmsPermissions = hasSmsPermissions,
                    hasNotificationPermission = hasNotificationPermission,
                    queueCount = queueCount,
                    sentCount = sentCount,
                    failedCount = failedCount,
                    lastSentLog = lastSentLog,
                    onToggleService = asisViewModel::setServiceEnabled,
                    onRequestPermissions = requestAllNeededPermissions,
                    onFlushQueue = asisViewModel::flushQueueNow,
                    onSimulateSms = asisViewModel::enqueueSimulatedBankSms,
                    onNavigateToSettings = { currentTab = AsisTab.SETTINGS },
                    onNavigateToLogs = { currentTab = AsisTab.LOGS }
                )

                AsisTab.LOGS -> LogsScreen(
                    logs = allLogs,
                    onRetrySingle = asisViewModel::retrySingleLog,
                    onClearAllLogs = asisViewModel::clearAllLogs
                )

                AsisTab.BANKS -> BanksScreen(
                    settings = settings,
                    onToggleFilterEnabled = asisViewModel::setFilterBanksEnabled,
                    onToggleBank = asisViewModel::toggleBankSelection,
                    onSelectAllBanks = asisViewModel::selectAllBanks
                )

                AsisTab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    testConnectionState = testConnectionState,
                    onSaveSettings = asisViewModel::saveSettings,
                    onTestConnection = asisViewModel::runTestConnection,
                    onResetTestState = asisViewModel::resetTestConnectionState
                )
            }
        }
    }
}

private fun checkSmsPermissionsGranted(context: android.content.Context): Boolean {
    val receiveGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECEIVE_SMS
    ) == PackageManager.PERMISSION_GRANTED
    val readGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED
    return receiveGranted && readGranted
}

private fun checkNotificationPermissionGranted(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
