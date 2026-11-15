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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.SmsRecord
import com.example.ui.AsisViewModel
import com.example.ui.BanksScreen
import com.example.ui.HomeScreen
import com.example.ui.LogsScreen
import com.example.ui.SettingsScreen
import com.example.ui.components.AsisMotion
import com.example.ui.components.StatusPill
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
        runCatching { NotificationHelper.ensureChannel(this) }
        runCatching { InternetWatchService.register(this) }
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val vmFactory = remember(application) {
                        AsisViewModel.provideFactory(application)
                    }
                    val asisViewModel: AsisViewModel = viewModel(factory = vmFactory)
                    AsisAppScreen(asisViewModel = asisViewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsisAppScreen(
    asisViewModel: AsisViewModel = viewModel(
        factory = (LocalContext.current.applicationContext as? android.app.Application)?.let {
            AsisViewModel.provideFactory(it)
        }
    )
) {
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableStateOf(AsisTab.HOME) }

    val settings by asisViewModel.settingsState.collectAsStateWithLifecycle()
    val isOnline by asisViewModel.isOnlineState.collectAsStateWithLifecycle()
    val serverStatus by asisViewModel.serverStatusState.collectAsStateWithLifecycle()
    val allLogs by asisViewModel.allLogs.collectAsStateWithLifecycle()
    val allInvoices by asisViewModel.allInvoices.collectAsStateWithLifecycle()
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

    var hasSmsPermissions by remember {
        mutableStateOf(checkSmsPermissionsGranted(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(checkNotificationPermissionGranted(context))
    }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(checkBatteryOptimizationIgnored(context))
    }
    var didAutoPromptPermissions by rememberSaveable { mutableStateOf(false) }
    var didAutoPromptBatteryOpt by rememberSaveable { mutableStateOf(false) }

    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isIgnoringBatteryOptimizations = checkBatteryOptimizationIgnored(context)
    }

    val requestIgnoreBatteryOptimizations = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !checkBatteryOptimizationIgnored(context)) {
            val directIntent = android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:${context.packageName}")
            )
            val launched = runCatching {
                batteryOptimizationLauncher.launch(directIntent)
                true
            }.getOrDefault(false)

            if (!launched) {
                runCatching {
                    val fallbackIntent = android.content.Intent(
                        android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                    )
                    batteryOptimizationLauncher.launch(fallbackIntent)
                }
            }
        }
        Unit
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val smsGrantedNow = checkSmsPermissionsGranted(context)
        hasSmsPermissions = smsGrantedNow
        hasNotificationPermission = checkNotificationPermissionGranted(context)
        isIgnoringBatteryOptimizations = checkBatteryOptimizationIgnored(context)
        if (smsGrantedNow) {
            asisViewModel.onSmsPermissionsGranted()
        }
        if (!isIgnoringBatteryOptimizations && !didAutoPromptBatteryOpt) {
            didAutoPromptBatteryOpt = true
            requestIgnoreBatteryOptimizations()
        }
    }

    val requestAllNeededPermissions = {
        val perms = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        runCatching { permissionsLauncher.launch(perms) }
        Unit
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val wasGranted = hasSmsPermissions
                val nowGranted = checkSmsPermissionsGranted(context)
                hasSmsPermissions = nowGranted
                hasNotificationPermission = checkNotificationPermissionGranted(context)
                isIgnoringBatteryOptimizations = checkBatteryOptimizationIgnored(context)
                if (!wasGranted && nowGranted) {
                    asisViewModel.onSmsPermissionsGranted()
                }
                if (!nowGranted && !didAutoPromptPermissions) {
                    didAutoPromptPermissions = true
                    requestAllNeededPermissions()
                } else if (nowGranted && !isIgnoringBatteryOptimizations && !didAutoPromptBatteryOpt) {
                    didAutoPromptBatteryOpt = true
                    requestIgnoreBatteryOptimizations()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Dynamic BroadcastReceiver registration alongside static AndroidManifest registration
    // to ensure immediate foreground SMS_RECEIVED delivery across all OEM ROMs.
    DisposableEffect(context, hasSmsPermissions) {
        val dynamicSmsReceiver = if (hasSmsPermissions) SmsReceiver() else null
        if (dynamicSmsReceiver != null) {
            runCatching {
                val filter = android.content.IntentFilter(
                    android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION
                ).apply {
                    priority = 999
                }
                ContextCompat.registerReceiver(
                    context,
                    dynamicSmsReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
            }
        }
        onDispose {
            if (dynamicSmsReceiver != null) {
                runCatching { context.unregisterReceiver(dynamicSmsReceiver) }
            }
        }
    }

    BackHandler(enabled = currentTab != AsisTab.HOME) {
        currentTab = AsisTab.HOME
    }

    val badgeCount = queueCount + failedCount

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_asis_shield),
                                            contentDescription = null,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = stringResource(id = R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(id = R.string.app_subtitle),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            val statusColor = when (serverStatus.level) {
                                com.example.network.ServerConnectionLevel.GREEN -> Color(0xFF10B981)
                                com.example.network.ServerConnectionLevel.YELLOW -> Color(0xFFF59E0B)
                                com.example.network.ServerConnectionLevel.RED -> Color(0xFFEF4444)
                            }
                            val statusText = when (serverStatus.level) {
                                com.example.network.ServerConnectionLevel.GREEN -> "آنلاین"
                                com.example.network.ServerConnectionLevel.YELLOW -> if (!settings.isConfigured) "نیازمند تنظیم" else "هشدار"
                                com.example.network.ServerConnectionLevel.RED -> "آفلاین"
                            }
                            StatusPill(
                                label = statusText,
                                color = statusColor,
                                isPulsing = serverStatus.level == com.example.network.ServerConnectionLevel.GREEN || serverStatus.isChecking,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .clickable { asisViewModel.checkServerConnectionNow() }
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            },
            bottomBar = {
                if (!useNavRail) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = Color(0xFF2D3545)
                            )
                            NavigationBar(
                                containerColor = Color(0xFF141821),
                                tonalElevation = 0.dp,
                                windowInsets = WindowInsets.navigationBars
                            ) {
                                val firstPair = listOf(AsisTab.HOME, AsisTab.LOGS)
                                val secondPair = listOf(AsisTab.BANKS, AsisTab.SETTINGS)

                                for (tab in firstPair) {
                                    val selected = currentTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        modifier = Modifier.testTag("nav_tab_${tab.route}"),
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color(0xFF3B82F6),
                                            selectedTextColor = Color(0xFF3B82F6),
                                            indicatorColor = Color(0xFF3B82F6).copy(alpha = 0.14f),
                                            unselectedIconColor = Color(0xFF8E98A8),
                                            unselectedTextColor = Color(0xFF8E98A8)
                                        ),
                                        icon = {
                                            NavTabIcon(
                                                tab = tab,
                                                selected = selected,
                                                badgeCount = badgeCount,
                                                hasErrors = failedCount > 0
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(id = tab.titleRes),
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    )
                                }

                                // Center spacer for the overlapping circular power toggle button
                                Spacer(modifier = Modifier.weight(0.65f))

                                for (tab in secondPair) {
                                    val selected = currentTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        modifier = Modifier.testTag("nav_tab_${tab.route}"),
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color(0xFF3B82F6),
                                            selectedTextColor = Color(0xFF3B82F6),
                                            indicatorColor = Color(0xFF3B82F6).copy(alpha = 0.14f),
                                            unselectedIconColor = Color(0xFF8E98A8),
                                            unselectedTextColor = Color(0xFF8E98A8)
                                        ),
                                        icon = {
                                            NavTabIcon(
                                                tab = tab,
                                                selected = selected,
                                                badgeCount = badgeCount,
                                                hasErrors = failedCount > 0
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(id = tab.titleRes),
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // Prominent circular orange/emerald power toggle button overlapping the center of the bottom bar
                        val powerColor by animateColorAsState(
                            targetValue = if (settings.serviceEnabled) Color(0xFFF59E0B) else Color(0xFF475569),
                            animationSpec = AsisMotion.smoothSpring(),
                            label = "powerButtonColor"
                        )
                        val powerScale by animateFloatAsState(
                            targetValue = if (settings.serviceEnabled) 1.04f else 0.96f,
                            animationSpec = AsisMotion.snappySpring(),
                            label = "powerButtonScale"
                        )
                        Surface(
                            shape = CircleShape,
                            color = powerColor,
                            border = BorderStroke(4.dp, Color(0xFF141821)),
                            shadowElevation = if (settings.serviceEnabled) 10.dp else 4.dp,
                            modifier = Modifier
                                .offset(y = (-14).dp)
                                .size(54.dp)
                                .scale(powerScale)
                                .clickable {
                                    asisViewModel.setServiceEnabled(!settings.serviceEnabled)
                                }
                                .testTag("bottom_bar_power_toggle")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.PowerSettingsNew,
                                    contentDescription = "روشن/خاموش کردن سرویس",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(0.15f))
                        for (tab in AsisTab.entries) {
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                modifier = Modifier.testTag("nav_tab_${tab.route}"),
                                icon = {
                                    NavTabIcon(
                                        tab = tab,
                                        selected = selected,
                                        badgeCount = badgeCount,
                                        hasErrors = failedCount > 0
                                    )
                                },
                                label = {
                                    Text(text = stringResource(id = tab.titleRes))
                                }
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            val movingForward = targetState.ordinal > initialState.ordinal
                            val enterOffset: (Int) -> Int = { fullWidth ->
                                if (movingForward) -fullWidth / 10 else fullWidth / 10
                            }
                            val exitOffset: (Int) -> Int = { fullWidth ->
                                if (movingForward) fullWidth / 14 else -fullWidth / 14
                            }
                            (slideInHorizontally(
                                animationSpec = AsisMotion.offsetSpring(),
                                initialOffsetX = enterOffset
                            ) + fadeIn(tween(220)) + scaleIn(
                                initialScale = 0.98f,
                                animationSpec = AsisMotion.smoothSpring()
                            )).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(170),
                                    targetOffsetX = exitOffset
                                ) + fadeOut(tween(150)) + scaleOut(
                                    targetScale = 0.98f,
                                    animationSpec = tween(150)
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .fillMaxHeight(),
                        label = "tabTransition"
                    ) { tab ->
                        when (tab) {
                            AsisTab.HOME -> {
                                val errorLogs = remember(allLogs) {
                                    allLogs.filter {
                                        it.statusEnum == com.example.data.SmsStatus.FAILED ||
                                            (it.statusEnum == com.example.data.SmsStatus.RETRY &&
                                                (it.attempts > 0 || !it.lastError.isNullOrBlank()))
                                    }
                                }
                                HomeScreen(
                                    settings = settings,
                                    isOnline = isOnline,
                                    serverStatus = serverStatus,
                                    onCheckServerConnection = asisViewModel::checkServerConnectionNow,
                                    hasSmsPermissions = hasSmsPermissions,
                                    hasNotificationPermission = hasNotificationPermission,
                                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                                    queueCount = queueCount,
                                    sentCount = sentCount,
                                    failedCount = failedCount,
                                    lastSentLog = lastSentLog,
                                    onToggleService = asisViewModel::setServiceEnabled,
                                    onRequestPermissions = requestAllNeededPermissions,
                                    onRequestIgnoreBatteryOptimizations = requestIgnoreBatteryOptimizations,
                                    onFlushQueue = asisViewModel::flushQueueNow,
                                    onSimulateSms = asisViewModel::enqueueSimulatedBankSms,
                                    onNavigateToSettings = { currentTab = AsisTab.SETTINGS },
                                    onNavigateToLogs = { currentTab = AsisTab.LOGS },
                                    errorLogs = errorLogs,
                                    onRetrySingleLog = asisViewModel::retrySingleLog,
                                    onRetryAllFailed = asisViewModel::retryAllFailedLogs,
                                    invoices = allInvoices,
                                    onRegisterInvoice = asisViewModel::registerInvoiceAndVerifyLogs,
                                    onRemoveInvoice = asisViewModel::removeInvoice
                                )
                            }

                            AsisTab.LOGS -> LogsScreen(
                                logs = allLogs,
                                onApproveManually = asisViewModel::approveRecordManually,
                                onRetrySingle = asisViewModel::retrySingleLog,
                                onRetryAllFailed = asisViewModel::retryAllFailedLogs,
                                onClearAllLogs = asisViewModel::clearAllLogs,
                                onMarkAsPersonal = asisViewModel::markDepositAsPersonalIgnored
                            )

                            AsisTab.BANKS -> BanksScreen(
                                settings = settings,
                                onToggleFilterEnabled = asisViewModel::setFilterBanksEnabled,
                                onToggleBank = asisViewModel::toggleBankSelection,
                                onSelectAllBanks = asisViewModel::selectAllBanks,
                                onAddRegisteredBank = asisViewModel::addRegisteredBankCard,
                                onRemoveRegisteredBank = asisViewModel::removeRegisteredBankCard,
                                onLoadReceivedSenders = asisViewModel::loadReceivedSmsSenders
                            )

                            AsisTab.SETTINGS -> SettingsScreen(
                                settings = settings,
                                testConnectionState = testConnectionState,
                                onSaveSettings = asisViewModel::saveSettings,
                                onToggleAutoApproveInApp = asisViewModel::setAutoApproveInApp,
                                onTestConnection = asisViewModel::runTestConnection,
                                onTestPendingEndpoint = asisViewModel::runTestPendingEndpoint,
                                onResetTestState = asisViewModel::resetTestConnectionState,
                                hasNotificationPermission = hasNotificationPermission,
                                onRequestNotificationPermission = requestAllNeededPermissions,
                                onToggleNotifyServerError = asisViewModel::setNotifyOnServerConnectionError,
                                onToggleNotifySensitiveSms = asisViewModel::setNotifyOnSensitiveBankSms,
                                onToggleNotifyAmbiguousManual = asisViewModel::setNotifyOnAmbiguousOrManual,
                                onUpdateSensitiveSmsMinToman = asisViewModel::setSensitiveSmsMinToman,
                                onSendTestNotification = asisViewModel::sendTestPushNotification
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavTabIcon(
    tab: AsisTab,
    selected: Boolean,
    badgeCount: Int,
    hasErrors: Boolean
) {
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1.0f,
        animationSpec = AsisMotion.snappySpring(),
        label = "navIconScale_${tab.route}"
    )
    if (tab == AsisTab.LOGS && badgeCount > 0) {
        BadgedBox(
            modifier = Modifier.scale(iconScale),
            badge = {
                Badge(
                    containerColor = if (hasErrors) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                ) {
                    Text(badgeCount.toString())
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
            contentDescription = stringResource(tab.titleRes),
            modifier = Modifier.scale(iconScale)
        )
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

internal fun checkBatteryOptimizationIgnored(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    } else {
        true
    }
}
