package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.makeforge.clapi.core.model.importers.ImportDialogStep
import io.github.makeforge.clapi.core.model.importers.ImportScanResult
import io.github.makeforge.clapi.core.model.importers.ImportSourceType
import io.github.makeforge.clapi.core.model.importers.SpecFormat
import io.github.makeforge.clapi.core.model.opencollection.Environment
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.storage.importers.SpecificationImporter
import io.github.makeforge.clapi.ui.shared.components.sidebar.collapseNodeRecursively
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_assignment
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_content_paste
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_description
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_folder
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_public
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_upload_file
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_warning
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.persistence.toCollectionNode
import io.github.makeforge.clapi.ui.shared.persistence.toMutableKeyValue
import io.github.makeforge.clapi.ui.shared.platform.PlatformLazyVerticalScrollbar
import io.github.makeforge.clapi.ui.shared.platform.pickFileForImport
import io.github.makeforge.clapi.ui.shared.platform.readFromClipboard
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

private val WarningColor = Color(0xFFF59E0B)

internal class CandidateFolderItemState(
    val id: String,
    val originalName: String,
    initialName: String,
    val folder: FolderItem,
    val requestCount: Int,
    initialSelected: Boolean = true,
) {
    var name by mutableStateOf(initialName)
    var selected by mutableStateOf(initialSelected)
}

internal class CandidateEnvironmentItemState(
    val id: String,
    val originalName: String,
    initialName: String,
    val environment: Environment,
    val variableCount: Int,
    initialSelected: Boolean = true,
) {
    var name by mutableStateOf(initialName)
    var selected by mutableStateOf(initialSelected)
}

@Composable
fun ImportSpecificationDialog(
    state: AppState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentStep by remember { mutableStateOf(ImportDialogStep.SOURCE_SELECTION) }
    var sourceType by remember { mutableStateOf(ImportSourceType.FILE) }

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var fileContent by remember { mutableStateOf<String?>(null) }
    var clipboardContent by remember { mutableStateOf<String?>(null) }

    var isScanning by remember { mutableStateOf(false) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var scanResult by remember { mutableStateOf<ImportScanResult.Success?>(null) }

    val candidateFolders = remember { mutableStateListOf<CandidateFolderItemState>() }
    val candidateEnvironments = remember { mutableStateListOf<CandidateEnvironmentItemState>() }

    val scope = rememberCoroutineScope()

    // Read clipboard when switching to CLIPBOARD tab
    LaunchedEffect(sourceType) {
        if (sourceType == ImportSourceType.CLIPBOARD) {
            clipboardContent = readFromClipboard()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier
                .dialogEscape(onDismiss = onDismiss)
                .widthIn(min = 550.dp, max = 650.dp)
                .heightIn(min = 450.dp, max = 600.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                .testTag("import-specification-dialog"),
            color = ClapiColors.Surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ClapiColors.SurfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painter = painterResource(Res.drawable.ic_upload_file),
                        contentDescription = null,
                        tint = ClapiColors.Primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (currentStep == ImportDialogStep.SOURCE_SELECTION) {
                            Strings.importDialogTitle
                        } else {
                            Strings.importSelectItems
                        },
                        color = ClapiColors.OnSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (currentStep == ImportDialogStep.ITEM_SELECTION && scanResult != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(ClapiColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("import-format-badge")
                        ) {
                            val badgeText = when (scanResult?.format) {
                                SpecFormat.OPENAPI_V3 -> "OpenAPI 3.0"
                                SpecFormat.OPENCOLLECTION -> "OpenCollection"
                                SpecFormat.POSTMAN -> "Postman"
                                SpecFormat.INSOMNIA -> "Insomnia v5"
                                null -> "OpenCollection"
                            }
                            Text(
                                badgeText,
                                color = ClapiColors.Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_close),
                            contentDescription = Strings.close,
                            tint = ClapiColors.OnSurfaceDim,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = ClapiColors.Border)

                // Body content based on step
                if (currentStep == ImportDialogStep.SOURCE_SELECTION) {
                    // Step 1: Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        val isFile = sourceType == ImportSourceType.FILE
                        ImportTypeButton(
                            text = Strings.importTabFile,
                            isFile,
                            testTag = "import-tab-file",
                            icon = painterResource(Res.drawable.ic_upload_file)
                        ) { sourceType = ImportSourceType.FILE }

                        Spacer(Modifier.width(8.dp))

                        val isClipboard = sourceType == ImportSourceType.CLIPBOARD
                        ImportTypeButton(
                            text = Strings.importTabClipboard,
                            isClipboard,
                            testTag = "import-tab-clipboard",
                            icon = painterResource(Res.drawable.ic_content_paste)
                        ) {
                            sourceType = ImportSourceType.CLIPBOARD
                            clipboardContent = readFromClipboard()
                        }
                    }

                    // Source body
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                    ) {
                        if (sourceType == ImportSourceType.FILE) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                                    .background(ClapiColors.SurfaceContainer.copy(alpha = 0.5f))
                                    .padding(24.dp)
                                    .testTag("import-dropzone"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                if (selectedFileName != null && fileContent != null) {
                                    Icon(painter = painterResource(Res.drawable.ic_description),
                                        contentDescription = null,
                                        tint = ClapiColors.Primary,
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        selectedFileName ?: "",
                                        color = ClapiColors.OnSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.testTag("import-file-name"),
                                    )
                                    Text(
                                        "${(fileContent?.length ?: 0) / 1024} KB",
                                        color = ClapiColors.OnSurfaceDim,
                                        fontSize = 12.sp,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = {
                                            selectedFileName = null
                                            fileContent = null
                                            scanErrorMessage = null
                                        },
                                        modifier = Modifier.testTag("import-file-clear-button")
                                    ) {
                                        Text(Strings.t("import_remove_file").ifBlank { "Remove" })
                                    }
                                } else {
                                    Icon(painter = painterResource(Res.drawable.ic_upload_file),
                                        contentDescription = null,
                                        tint = ClapiColors.OnSurfaceDim,
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        Strings.importDropFilesHere,
                                        color = ClapiColors.OnSurfaceDim,
                                        fontSize = 13.sp,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            pickFileForImport { name, content ->
                                                selectedFileName = name
                                                fileContent = content
                                                scanErrorMessage = null
                                            }
                                        },
                                        modifier = Modifier.testTag("import-choose-file-button")
                                    ) {
                                        Text(Strings.importChooseFiles)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        Strings.importSupportedFormats,
                                        color = ClapiColors.OnSurfaceDim.copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    SupportedFormatBadges()
                                }
                            }
                        } else {
                            // Clipboard Tab
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                                    .background(ClapiColors.SurfaceContainer.copy(alpha = 0.5f))
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(painter = painterResource(Res.drawable.ic_assignment),
                                    contentDescription = null,
                                    tint = if (!clipboardContent.isNullOrBlank()) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    Strings.importClipboardDesc,
                                    color = ClapiColors.OnSurface,
                                    fontSize = 13.sp,
                                )
                                Spacer(Modifier.height(12.dp))
                                if (!clipboardContent.isNullOrBlank()) {
                                    val length = clipboardContent?.length ?: 0
                                    Text(
                                        "Clipboard payload ready ($length characters)",
                                        color = ClapiColors.Primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.testTag("import-clipboard-content"),
                                    )
                                } else {
                                    Text(
                                        Strings.importClipboardEmpty,
                                        color = ClapiColors.OnSurfaceDim,
                                        fontSize = 12.sp,
                                        modifier = Modifier.testTag("import-clipboard-empty"),
                                    )
                                }
                            }
                        }
                    }

                    // Error banner
                    if (scanErrorMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.Error.copy(alpha = 0.15f))
                                .border(1.dp, ClapiColors.Error, RoundedCornerShape(6.dp))
                                .padding(12.dp)
                                .testTag("import-scan-error"),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painter = painterResource(Res.drawable.ic_warning),
                                    contentDescription = null,
                                    tint = ClapiColors.Error,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    scanErrorMessage ?: "",
                                    color = ClapiColors.Error,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    // Footer for Step 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("import-cancel-button")
                        ) {
                            Text(Strings.cancel)
                        }

                        Spacer(Modifier.width(8.dp))

                        val hasContent = if (sourceType == ImportSourceType.FILE) {
                            !fileContent.isNullOrBlank()
                        } else {
                            !clipboardContent.isNullOrBlank()
                        }

                        Button(
                            onClick = {
                                val contentToScan = if (sourceType == ImportSourceType.FILE) {
                                    fileContent ?: ""
                                } else {
                                    clipboardContent ?: ""
                                }
                                scope.launch {
                                    isScanning = true
                                    scanErrorMessage = null
                                    val result = withContext(Dispatchers.Default) {
                                        SpecificationImporter.default().scanContent(contentToScan)
                                    }
                                    when (result) {
                                        is ImportScanResult.Success -> {
                                            scanResult = result
                                            candidateFolders.clear()
                                            result.folders.forEach { f ->
                                                candidateFolders.add(
                                                    CandidateFolderItemState(
                                                        id = f.id,
                                                        originalName = f.originalName,
                                                        initialName = f.name,
                                                        folder = f.folder,
                                                        requestCount = f.requestCount,
                                                        initialSelected = f.selected,
                                                    )
                                                )
                                            }
                                            candidateEnvironments.clear()
                                            result.environments.forEach { e ->
                                                candidateEnvironments.add(
                                                    CandidateEnvironmentItemState(
                                                        id = e.id,
                                                        originalName = e.originalName,
                                                        initialName = e.name,
                                                        environment = e.environment,
                                                        variableCount = e.variableCount,
                                                        initialSelected = e.selected,
                                                    )
                                                )
                                            }
                                            currentStep = ImportDialogStep.ITEM_SELECTION
                                            isScanning = false
                                        }
                                        is ImportScanResult.Failure -> {
                                            scanErrorMessage = result.message
                                            isScanning = false
                                        }
                                    }
                                }
                            },
                            enabled = hasContent && !isScanning,
                            modifier = Modifier.testTag("import-scan-button")
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = ClapiColors.OnPrimary,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(Strings.importScanning)
                            } else {
                                Text(Strings.importScan)
                            }
                        }
                    }
                } else {
                    // Step 2: ITEM_SELECTION
                    val listState = rememberLazyListState()
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .padding(end = 12.dp)
                        ) {
                        if (candidateFolders.isNotEmpty()) {
                            item {
                                Text(
                                    Strings.folders,
                                    color = ClapiColors.OnSurfaceDim,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items(candidateFolders, key = { it.id }) { cFolder ->
                                val hasConflict = state.collections.any { it.name == cFolder.name }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ClapiColors.SurfaceContainer)
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = cFolder.selected,
                                        onCheckedChange = { cFolder.selected = it },
                                        modifier = Modifier.testTag("import-folder-check-${cFolder.id}"),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(painter = painterResource(Res.drawable.ic_folder),
                                        contentDescription = null,
                                        tint = ClapiColors.Primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    BasicTextField(
                                        value = cFolder.name,
                                        onValueChange = { cFolder.name = it },
                                        textStyle = TextStyle(
                                            color = ClapiColors.OnSurface,
                                            fontSize = 13.sp,
                                        ),
                                        cursorBrush = SolidColor(ClapiColors.Primary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                            .testTag("import-folder-name-${cFolder.id}"),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${cFolder.requestCount} requests",
                                        color = ClapiColors.OnSurfaceDim,
                                        fontSize = 11.sp,
                                        modifier = Modifier.testTag("import-folder-requests-${cFolder.id}")
                                    )
                                    if (hasConflict) {
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    WarningColor.copy(alpha = 0.15f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                .testTag("import-folder-conflict-${cFolder.id}")
                                        ) {
                                            Text(
                                                Strings.importOverwriteWarning,
                                                color = WarningColor,
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (candidateEnvironments.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    Strings.environments,
                                    color = ClapiColors.OnSurfaceDim,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items(candidateEnvironments, key = { it.id }) { cEnv ->
                                val hasConflict = state.environments.any { it.name == cEnv.name }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ClapiColors.SurfaceContainer)
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = cEnv.selected,
                                        onCheckedChange = { cEnv.selected = it },
                                        modifier = Modifier.testTag("import-env-check-${cEnv.id}"),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(painter = painterResource(Res.drawable.ic_public),
                                        contentDescription = null,
                                        tint = ClapiColors.Primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    BasicTextField(
                                        value = cEnv.name,
                                        onValueChange = { cEnv.name = it },
                                        textStyle = TextStyle(
                                            color = ClapiColors.OnSurface,
                                            fontSize = 13.sp,
                                        ),
                                        cursorBrush = SolidColor(ClapiColors.Primary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                            .testTag("import-env-name-${cEnv.id}"),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${cEnv.variableCount} variables",
                                        color = ClapiColors.OnSurfaceDim,
                                        fontSize = 11.sp,
                                    )
                                    if (hasConflict) {
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    WarningColor.copy(alpha = 0.15f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                .testTag("import-env-conflict-${cEnv.id}")
                                        ) {
                                            Text(
                                                Strings.importOverwriteWarning,
                                                color = WarningColor,
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    PlatformLazyVerticalScrollbar(
                        listState = listState,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }

                    // Footer for Step 2
                    val hasSelectedItem = candidateFolders.any { it.selected } || candidateEnvironments.any { it.selected }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("import-cancel-button")
                        ) {
                            Text(Strings.cancel)
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val selectedFolders = candidateFolders.filter { it.selected }
                                for (cFolder in selectedFolders) {
                                    val baseNode = cFolder.folder.toCollectionNode()
                                    val node = if (cFolder.name.isNotBlank()) {
                                        baseNode.copy(name = cFolder.name)
                                    } else {
                                        baseNode
                                    }
                                    collapseNodeRecursively(node, state)
                                    val existingIndex = state.collections.indexOfFirst { it.name == node.name }
                                    if (existingIndex >= 0) {
                                        state.collections[existingIndex] = node
                                    } else {
                                        state.collections.add(node)
                                    }
                                }

                                val selectedEnvs = candidateEnvironments.filter { it.selected }
                                for (cEnv in selectedEnvs) {
                                    val envState = EnvState(
                                        name = cEnv.name,
                                        variables = cEnv.environment.variables.map { it.toMutableKeyValue() },
                                        color = cEnv.environment.color,
                                        description = cEnv.environment.description,
                                        clientCertificates = cEnv.environment.clientCertificates,
                                        extends = cEnv.environment.extends,
                                        dotEnvFilePath = cEnv.environment.dotEnvFilePath,
                                    )
                                    val existingEnvIndex = state.environments.indexOfFirst { it.name == cEnv.name }
                                    if (existingEnvIndex >= 0) {
                                        state.environments[existingEnvIndex] = envState
                                    } else {
                                        state.environments.add(envState)
                                    }
                                }

                                WorkspaceRepository.save(state)
                                state.log(
                                    "Import completed: ${selectedFolders.size} folder(s), ${selectedEnvs.size} environment(s)",
                                    LogLevel.SUCCESS
                                )
                                onDismiss()
                            },
                            enabled = hasSelectedItem,
                            modifier = Modifier.testTag("import-confirm-button")
                        ) {
                            Text(Strings.importAction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportTypeButton(
    text: String,
    selected: Boolean,
    icon: Painter,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val contentColor =
        if (selected) ClapiColors.Primary.copy(alpha = 0.15f) else ClapiColors.SurfaceContainer
    val buttonModifier = Modifier
        .clip(RoundedCornerShape(6.dp))
        .background(contentColor)
        .let { if (testTag != null) it.testTag(testTag) else it }
    OutlinedButton(
        onClick =  onClick,
        modifier = buttonModifier,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
        ),
        border = BorderStroke(1.dp, ClapiColors.Border),
    ) {
        val textColor = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceDim
        Icon(
            icon,
            contentDescription = null,
            tint = textColor,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}

@Composable
fun SupportedFormatBadges(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.testTag("import-supported-formats-badges"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FormatBadgePill(
            label = Strings.importBadgeOpenApi,
            testTag = "import-format-badge-openapi",
        )
        FormatBadgePill(
            label = Strings.importBadgeOpenCollection,
            testTag = "import-format-badge-opencollection",
        )
        FormatBadgePill(
            label = Strings.importBadgePostman,
            testTag = "import-format-badge-postman",
        )
        FormatBadgePill(
            label = Strings.importBadgeInsomnia,
            testTag = "import-format-badge-insomnia",
        )
    }
}

@Composable
private fun FormatBadgePill(
    label: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ClapiColors.SurfaceContainer)
            .border(1.dp, ClapiColors.Border.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag(testTag),
    ) {
        Text(
            text = label,
            color = ClapiColors.OnSurfaceDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
