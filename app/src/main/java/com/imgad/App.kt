package com.imgad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.Room
import com.imgad.data.local.AppDatabase
import com.imgad.data.local.AssetFileStore
import com.imgad.data.local.KeystoreSecretStore
import com.imgad.data.local.MediaStoreSaver
import com.imgad.data.local.PreferenceDefaultProviderStore
import com.imgad.data.local.PrivateAssetStorage
import com.imgad.data.local.RoomArchiveStore
import com.imgad.data.local.AssetShareManager
import com.imgad.data.remote.DynamicImageGateway
import com.imgad.data.remote.OpenAiProviderModelCatalog
import com.imgad.data.remote.ProviderConnectionTester
import com.imgad.data.repository.GenerationRepository
import com.imgad.data.repository.ProviderRepository
import com.imgad.data.repository.SessionRepository
import com.imgad.domain.model.Asset
import com.imgad.domain.model.ModelProfile
import com.imgad.domain.model.Provider
import com.imgad.domain.port.ArchiveImportPreview
import com.imgad.domain.port.ImportedArchive
import com.imgad.domain.port.StorageUsage
import com.imgad.domain.usecase.EditImage
import com.imgad.domain.usecase.GenerateImage
import com.imgad.domain.usecase.ImportInputAsset
import com.imgad.domain.usecase.RetryGeneration
import com.imgad.domain.usecase.RecoverInterruptedTasks
import com.imgad.domain.usecase.RunStartupRecovery
import com.imgad.ui.create.CreateScreen
import com.imgad.ui.create.CreateViewModel
import com.imgad.ui.create.EditAction
import com.imgad.ui.create.GenerateAction
import com.imgad.ui.create.ImagePreviewScreen
import com.imgad.ui.create.RetryAction
import com.imgad.ui.create.SessionMessages
import com.imgad.ui.history.HistoryScreen
import com.imgad.ui.history.HistoryScreenActions
import com.imgad.ui.history.HistoryViewModel
import com.imgad.ui.settings.ModelEditorScreen
import com.imgad.ui.settings.ProviderEditorScreen
import com.imgad.ui.settings.ProviderModelDiscoveryUiState
import com.imgad.ui.settings.SettingsScreen
import com.imgad.ui.settings.SettingsScreenActions
import com.imgad.ui.settings.SettingsViewModel
import com.imgad.ui.settings.StorageSettingsActions
import com.imgad.ui.settings.StorageSettingsScreen
import com.imgad.ui.settings.StorageUsage as UiStorageUsage
import com.imgad.ui.theme.ImgAdTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun rfc3986Encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    .replace("+", "%20")
    .replace("%7E", "~")
    .replace("*", "%2A")

internal fun createSessionRoute(sessionId: String?): String = "create?sessionId=${rfc3986Encode(sessionId ?: "null")}"

internal fun providerEditorRoute(providerId: String?): String =
    "providerEditor?providerId=${rfc3986Encode(providerId ?: "null")}"

internal fun modelEditorRoute(providerId: String, modelId: String?): String =
    "modelEditor?providerId=${rfc3986Encode(providerId)}&modelId=${rfc3986Encode(modelId ?: "null")}"

class App : android.app.Application() {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, AppDatabase.DB_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }
    val fileStore by lazy { AssetFileStore(this) }
    val secretStore by lazy { KeystoreSecretStore(this) }
    val providerRepository by lazy {
        ProviderRepository(
            database.providerDao(),
            database.modelProfileDao(),
            secretStore,
            PreferenceDefaultProviderStore(this),
        )
    }
    val sessionRepository by lazy { SessionRepository(database.sessionDao(), database.assetDao(), fileStore) }
    val generationRepository by lazy { GenerationRepository(database) }
    val assetShareManager by lazy { AssetShareManager(this) }
    val storage by lazy {
        PrivateAssetStorage(
            root = java.io.File(filesDir, "imgad-assets"),
            referencedPaths = {
                database.assetDao().getActiveLocalUris()
                    .flatMap { row -> listOfNotNull(row.localUri, row.thumbnailUri) }
                    .toSet()
            },
        )
    }
    val imageGateway by lazy { DynamicImageGateway(providerRepository, java.io.File(filesDir, "imgad-assets")) }
    val generateImage by lazy { GenerateImage(imageGateway, generationRepository, fileStore) }
    val editImage by lazy { EditImage(imageGateway, generationRepository, fileStore) }
    val retryGeneration by lazy { RetryGeneration(generationRepository, generateImage, editImage) }
    private val startupRecovery by lazy {
        RunStartupRecovery {
            RecoverInterruptedTasks(generationRepository)()
        }
    }
    val importInputAsset by lazy { ImportInputAsset(fileStore) }
    val connectionTester by lazy { ProviderConnectionTester(providerRepository) }
    val modelCatalog by lazy {
        OpenAiProviderModelCatalog(storedApiKey = providerRepository::getApiKey)
    }
    val archiveStore by lazy { RoomArchiveStore(database, java.io.File(filesDir, "imgad-assets"), secretStore = secretStore) }

    suspend fun recoverInterruptedTasksOnce(): Result<Unit> = startupRecovery.runOnce()
}

private class AppViewModelFactory(private val app: App) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(CreateViewModel::class.java) -> CreateViewModel(
            providers = emptyList(),
            models = emptyList(),
            generate = GenerateAction { sessionId, request -> app.generateImage(sessionId, request) },
            edit = EditAction { sessionId, request -> app.editImage(sessionId, request) },
            retryAction = RetryAction { messageId -> app.retryGeneration(messageId) },
            sessionMessages = SessionMessages { id -> app.generationRepository.observeSessionContent(id) },
        ) as T
        modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(app.sessionRepository) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
            app.providerRepository,
            app.connectionTester,
            app.modelCatalog,
        ) as T
        else -> error("Unsupported ViewModel: ${modelClass.name}")
    }
}

@Composable
fun ImgAdApp() {
    val context = LocalContext.current.applicationContext
    val app = context as? App ?: return
    val navController = rememberNavController()
    val providers by app.providerRepository.observeEnabledProviders().collectAsStateWithLifecycle(emptyList())
    val models by observeCatalogModels(app.providerRepository, providers).collectAsStateWithLifecycle(emptyList())
    val factory = remember(app) { AppViewModelFactory(app) }
    val createViewModel: CreateViewModel = viewModel(factory = factory)
    val historyViewModel: HistoryViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(providers, models) {
        createViewModel.updateCatalog(providers, models, app.providerRepository.getDefaultProviderId())
    }
    LaunchedEffect(app) {
        app.recoverInterruptedTasksOnce().onFailure { error ->
            snackbarHostState.showSnackbar("启动恢复失败：${error.message ?: "未知错误"}")
        }
    }
    ImgAdTheme {
        Surface(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().testTag("imgad-root"),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppNavigation(app, navController, createViewModel, historyViewModel, settingsViewModel, context, snackbarHostState)
        }
    }
}

@Composable
private fun observeCatalogModels(repository: ProviderRepository, providers: List<Provider>): Flow<List<ModelProfile>> =
    remember(providers.map(Provider::id)) {
        if (providers.isEmpty()) flowOf(emptyList())
        else combine(providers.map { repository.observeEnabledModels(it.id) }) { lists -> lists.flatMap { it } }
    }

@Composable
private fun AppNavigation(
    app: App,
    navController: NavHostController,
    createViewModel: CreateViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    context: Context,
    snackbarHostState: SnackbarHostState,
) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    var previewAsset by remember { mutableStateOf<Asset?>(null) }
    var previewSnapshot by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val usage = remember { mutableStateOf<StorageUsage?>(null) }
    var archiveStatus by remember { mutableStateOf<String?>(null) }
    var exportIncludeSecrets by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<String?>(null) }
    var importPassword by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<ImportedArchive?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) {
            exportIncludeSecrets = false
            exportPassword = null
            return@rememberLauncherForActivityResult
        }
        val includeSecrets = exportIncludeSecrets
        val password = exportPassword?.toCharArray()
        scope.launch(Dispatchers.IO) {
            try {
                val result = runCatching {
                    val snapshot = app.archiveStore.snapshot(includeSecrets)
                    context.contentResolver.openOutputStream(uri)?.use { app.archiveStore.export(snapshot, it, password) }
                        ?: error("无法打开导出文件")
                }
                withContext(Dispatchers.Main) {
                    archiveStatus = result.fold(
                        onSuccess = { "导出完成" },
                        onFailure = { it.message ?: "导出失败" },
                    )
                }
            } finally {
                password?.fill('\u0000')
                withContext(NonCancellable + Dispatchers.Main) {
                    exportIncludeSecrets = false
                    exportPassword = null
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            importPassword = null
            return@rememberLauncherForActivityResult
        }
        val password = importPassword?.toCharArray()
        scope.launch(Dispatchers.IO) {
            try {
                val result = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { app.archiveStore.import(it, password) }
                        ?: error("无法打开导入文件")
                }
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = {
                            pendingImport = it
                            archiveStatus = "归档已读取，请确认导入"
                        },
                        onFailure = { archiveStatus = it.message ?: "导入失败" },
                    )
                }
            } finally {
                password?.fill('\u0000')
                withContext(NonCancellable + Dispatchers.Main) { importPassword = null }
            }
        }
    }
    LaunchedEffect(app) {
        runCatching { withContext(Dispatchers.IO) { app.storage.readUsage() } }
            .onSuccess { usage.value = it }
            .onFailure { archiveStatus = it.message ?: "读取存储占用失败" }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple("create", "创作", Icons.Default.Create),
                    Triple("history", "历史", Icons.Default.History),
                    Triple("settings", "设置", Icons.Default.Settings),
                ).forEach { (target, label, icon) ->
                    NavigationBarItem(
                        selected = route.startsWith(target),
                        onClick = {
                            val destination = if (target == "create") createSessionRoute(null) else target
                            if (!navController.popBackStack(destination, inclusive = false)) {
                                navController.navigate(destination) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = createSessionRoute(null),
            ) {
                composable(
                    route = "create?sessionId={sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType; nullable = true }),
                ) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId")?.takeUnless { it == "null" }
                    LaunchedEffect(sessionId) {
                        previewAsset = null
                        previewSnapshot = null
                        createViewModel.openSession(sessionId)
                    }
                    if (previewAsset != null) {
                        val asset = requireNotNull(previewAsset)
                        ImagePreviewScreen(
                            asset = asset,
                            requestSnapshotJson = previewSnapshot,
                            onClose = { previewAsset = null },
                            onSave = { selected ->
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val file = java.io.File(selected.localUri)
                                            MediaStoreSaver(context).save(file.readBytes(), selected.mediaType)
                                        }
                                    }
                                    snackbarHostState.showSnackbar(
                                        result.fold(
                                            onSuccess = { "已保存到系统图库" },
                                            onFailure = { it.message ?: "保存图片失败" },
                                        ),
                                    )
                                }
                            },
                            onShare = { selected ->
                                runCatching {
                                    val intent = app.assetShareManager.createShareIntent(selected)
                                    context.startActivity(
                                        Intent.createChooser(intent, "分享图片")
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }.onFailure { error ->
                                    scope.launch { snackbarHostState.showSnackbar(error.message ?: "分享图片失败") }
                                }
                            },
                        )
                    } else {
                        CreateScreen(
                            viewModel = createViewModel,
                            onPickedUris = { uris ->
                                uris.forEach { uri ->
                                    scope.launch(Dispatchers.IO) {
                                        runCatching { app.importInputAsset(uri) }
                                            .onSuccess { imported -> withContext(Dispatchers.Main) { createViewModel.addAsset(imported) } }
                                            .onFailure { error -> withContext(Dispatchers.Main) { createViewModel.reportError(error.message ?: "导入图片失败") } }
                                    }
                                }
                            },
                            onPickedMask = { uri ->
                                scope.launch(Dispatchers.IO) {
                                    runCatching { app.importInputAsset(uri).copy(source = com.imgad.domain.model.AssetSource.MASK) }
                                        .onSuccess { imported -> withContext(Dispatchers.Main) { createViewModel.setMask(imported) } }
                                        .onFailure { error -> withContext(Dispatchers.Main) { createViewModel.reportError(error.message ?: "导入蒙版失败") } }
                                }
                            },
                            onPreview = { selected ->
                                previewAsset = selected
                                previewSnapshot = selected.messageId?.let { id ->
                                    createViewModel.uiState.value.messages.firstOrNull { it.id == id }?.requestSnapshotJson
                                }
                            },
                            onCopyError = { details ->
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("错误详情", details))
                            },
                        )
                    }
                }
                composable("history") {
                    HistoryScreen(historyViewModel, HistoryScreenActions { id -> navController.navigate(createSessionRoute(id)) })
                }
                composable("settings") {
                    SettingsScreen(settingsViewModel, SettingsScreenActions(
                        onEditProvider = { id -> navController.navigate(providerEditorRoute(id)) },
                        onDeleteProvider = { id, replacement -> settingsViewModel.deleteProvider(id, replacement) },
                        onEditModel = { id ->
                            val providerId = settingsState.selectedProviderId.orEmpty()
                            navController.navigate(modelEditorRoute(providerId, id))
                        },
                        onDeleteModel = settingsViewModel::deleteModel,
                        onOpenStorage = { navController.navigate("storage") },
                    ))
                }
                composable(
                    route = "providerEditor?providerId={providerId}",
                    arguments = listOf(navArgument("providerId") { type = NavType.StringType; nullable = true }),
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("providerId")?.takeUnless { it == "null" }
                    val initial = settingsState.providers.firstOrNull { it.id == id }
                    ProviderEditorScreen(
                        initial = initial,
                        apiKeyVisible = settingsState.apiKeyVisible,
                        discovery = ProviderModelDiscoveryUiState(
                            modelIds = settingsState.discoveredModelIds,
                            selectedModelIds = settingsState.selectedDiscoveredModelIds,
                            searchQuery = settingsState.modelSearchQuery,
                            isLoading = settingsState.isFetchingModels,
                            errorMessage = settingsState.modelFetchError,
                        ),
                        onSave = { provider, key, modelIds ->
                            settingsViewModel.saveProviderWithModels(provider, key, modelIds) {
                                navController.popBackStack()
                            }
                        },
                        onFetchModels = settingsViewModel::fetchModels,
                        onSearchQueryChanged = settingsViewModel::updateModelSearchQuery,
                        onToggleModel = settingsViewModel::toggleDiscoveredModel,
                        onClearDiscoveredModels = settingsViewModel::clearDiscoveredModels,
                        onToggleApiKeyVisibility = settingsViewModel::toggleApiKeyVisibility,
                        onBack = {
                            settingsViewModel.clearDiscoveredModels()
                            navController.popBackStack()
                        },
                    )
                }
                composable(
                    route = "modelEditor?providerId={providerId}&modelId={modelId}",
                    arguments = listOf(
                        navArgument("providerId") { type = NavType.StringType },
                        navArgument("modelId") { type = NavType.StringType; nullable = true },
                    ),
                ) { backStackEntry ->
                    val providerId = backStackEntry.arguments?.getString("providerId").orEmpty()
                    val modelId = backStackEntry.arguments?.getString("modelId")
                    val initial = settingsState.models.firstOrNull { it.id == modelId }
                    ModelEditorScreen(providerId, initial, { model -> settingsViewModel.saveModel(model) { navController.popBackStack() } }) {
                        navController.popBackStack()
                    }
                }
                composable("storage") {
                    LaunchedEffect(Unit) {
                        runCatching { withContext(Dispatchers.IO) { app.storage.readUsage() } }
                            .onSuccess { usage.value = it }
                            .onFailure { archiveStatus = it.message ?: "读取存储占用失败" }
                    }
                    val current = usage.value ?: StorageUsage(0, 0)
                    StorageSettingsScreen(
                        usage = UiStorageUsage(current.bytes, current.files),
                        statusMessage = archiveStatus,
                        importPreview = pendingImport?.let {
                            ArchiveImportPreview(
                                providers = it.data.providers.size,
                                sessions = it.data.sessions.size,
                                assets = it.data.assets.size,
                            )
                        },
                        actions = StorageSettingsActions(
                            onExport = { includeSecrets, password ->
                                exportIncludeSecrets = includeSecrets
                                exportPassword = password
                                exportLauncher.launch("imgad-export.zip")
                            },
                            onImport = { password ->
                                pendingImport = null
                                importPassword = password
                                importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            },
                            onConfirmImport = {
                                pendingImport?.let { imported ->
                                    scope.launch(Dispatchers.IO) {
                                        val result = runCatching {
                                            app.archiveStore.apply(imported)
                                            app.storage.readUsage()
                                        }
                                        withContext(Dispatchers.Main) {
                                            result.fold(
                                                onSuccess = {
                                                    pendingImport = null
                                                    usage.value = it
                                                    archiveStatus = "导入完成"
                                                },
                                                onFailure = { archiveStatus = it.message ?: "导入失败" },
                                            )
                                        }
                                    }
                                }
                            },
                            onCancelImport = {
                                pendingImport = null
                                importPassword = null
                                exportIncludeSecrets = false
                                exportPassword = null
                            },
                            onClear = {
                                scope.launch(Dispatchers.IO) {
                                    val result = runCatching { app.storage.clearUnused() }
                                    withContext(Dispatchers.Main) {
                                        result.fold(
                                            onSuccess = {
                                                usage.value = it
                                                archiveStatus = "缓存清理完成"
                                            },
                                            onFailure = { archiveStatus = it.message ?: "缓存清理失败" },
                                        )
                                    }
                                }
                            },
                        ),
                    )
                }
            }
        }
    }
}
