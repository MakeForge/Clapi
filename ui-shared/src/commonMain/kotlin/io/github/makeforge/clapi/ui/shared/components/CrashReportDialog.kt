package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.makeforge.clapi.ui.shared.build.GeneratedBuildInfo
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_content_copy
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_open_in_new
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_refresh
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.copyToClipboard
import io.github.makeforge.clapi.ui.shared.platform.openUrlInBrowser
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import java.net.URLEncoder

/**
 * Returns a human-readable operating system string (e.g., "Linux 6.6.137+ (amd64)").
 */
fun getOperatingSystemString(): String {
    val osName = System.getProperty("os.name") ?: "Unknown OS"
    val osVersion = System.getProperty("os.version") ?: ""
    val osArch = System.getProperty("os.arch") ?: ""
    return listOf(osName, osVersion, if (osArch.isNotBlank()) "($osArch)" else "")
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

/**
 * Encodes query parameter.
 */
private fun String?.asQueryParam(paramName: String): String {
    return this?.takeIf { it.isNotEmpty() }?.let {
        "&$paramName=${URLEncoder.encode(it, "UTF-8")}"
    } ?: ""
}

/**
 * Builds a GitHub issue URL for prefilling the `bug_report.yml` issue template.
 */
fun buildGitHubIssueUrl(
    stackTrace: String?,
    version: String = GeneratedBuildInfo.APP_VERSION,
    operatingSystem: String = getOperatingSystemString(),
    title: String? = null,
    whatHappened: String? = null,
    repository: String = "MakeForge/Clapi",
    maxUrlLength: Int = 2000,
    truncateLogsToFit: Boolean = true,
): String {
    val defaultTitle = title ?: "Crash: Unexpected exception occurred"
    val defaultWhatHappened = whatHappened ?: "An unexpected crash/exception occurred while using Clapi."

    val base = "https://github.com/$repository/issues/new?labels=bug&projects=&template=bug_report.yml" +
        defaultTitle.asQueryParam("title") +
        version.asQueryParam("version") +
        operatingSystem.asQueryParam("operating-system") +
        defaultWhatHappened.asQueryParam("what-happened")

    if (stackTrace.isNullOrBlank()) {
        if (base.length > maxUrlLength && !truncateLogsToFit) {
            throw IllegalArgumentException("Given stacktrace is too long to build a valid URL!")
        }
        return base
    }

    val fullUrl = "$base${stackTrace.asQueryParam("logs")}"
    if (fullUrl.length <= maxUrlLength) {
        return fullUrl
    }

    if (!truncateLogsToFit) {
        throw IllegalArgumentException("Given stacktrace is too long to build a valid URL!")
    }

    // Truncate logs to fit within maxUrlLength
    val prefix = "&logs="
    val availableForLogs = maxUrlLength - base.length - prefix.length
    if (availableForLogs <= 40) {
        return base
    }

    val suffix = "\n... [truncated]"
    val encodedSuffix = URLEncoder.encode(suffix, "UTF-8")
    var budget = availableForLogs - encodedSuffix.length
    val sb = StringBuilder()
    for (line in stackTrace.lineSequence()) {
        val encodedLine = URLEncoder.encode(line + "\n", "UTF-8")
        if (budget - encodedLine.length < 0) break
        sb.append(line).append("\n")
        budget -= encodedLine.length
    }

    return if (sb.isNotEmpty()) {
        "$base$prefix${URLEncoder.encode(sb.toString().trimEnd() + suffix, "UTF-8")}"
    } else {
        "$base$prefix${URLEncoder.encode("[Logs truncated - please paste from clipboard]", "UTF-8")}"
    }
}

/**
 * Formats full crash details for clipboard copying.
 */
fun formatCrashDetails(
    throwable: Throwable,
    version: String = GeneratedBuildInfo.APP_VERSION,
    operatingSystem: String = getOperatingSystemString(),
): String {
    return buildString {
        appendLine("Clapi Version: $version")
        appendLine("Operating System: $operatingSystem")
        appendLine("Exception: ${throwable::class.qualifiedName ?: throwable::class.simpleName}: ${throwable.message}")
        appendLine()
        appendLine("Stack Trace:")
        append(throwable.stackTraceToString())
    }
}

/**
 * Consistent Clapi-styled dialog card content for displaying unexpected crashes and fatal exceptions,
 * with options to copy full diagnostic details and report the issue on GitHub.
 */
@Composable
fun CrashReportContent(
    throwable: Throwable,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    appVersion: String = GeneratedBuildInfo.APP_VERSION,
    operatingSystem: String = getOperatingSystemString(),
    onReportBug: ((url: String) -> Unit)? = null,
    onCopyDetails: ((String) -> Unit)? = null,
) {
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val stackTrace = remember(throwable) { throwable.stackTraceToString() }
    val exceptionTitle = remember(throwable) {
        throwable.message?.takeIf { it.isNotBlank() } ?: (throwable::class.simpleName ?: "Unexpected Error")
    }

    val fullCrashDetails = remember(throwable, appVersion, operatingSystem) {
        formatCrashDetails(throwable, appVersion, operatingSystem)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClapiColors.Background)
            .padding(20.dp)
            .testTag("crash-dialog"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = exceptionTitle,
                    color = ClapiColors.OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("crash-dialog-message"),
                )
                throwable::class.qualifiedName?.let { fqName ->
                    Text(
                        text = fqName,
                        color = ClapiColors.Error,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        // Stack trace scroll area
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                .padding(8.dp),
        ) {
            SelectionContainer {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stackTrace,
                        color = ClapiColors.OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .verticalScroll(scrollState)
                            .padding(end = 14.dp)
                            .testTag("crash-dialog-stacktrace"),
                    )
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }

        // Environment & Version info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Version: $appVersion  •  OS: $operatingSystem",
                color = ClapiColors.OnSurfaceDim,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Copy Details button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ClapiColors.SurfaceContainer)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                    .clickable {
                        if (onCopyDetails != null) {
                            onCopyDetails(fullCrashDetails)
                        } else {
                            copyToClipboard(fullCrashDetails)
                        }
                        copied = true
                        scope.launch {
                            delay(2000)
                            copied = false
                        }
                    }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("crash-dialog-copy-button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_content_copy),
                    contentDescription = null,
                    tint = if (copied) ClapiColors.StatusSuccess else ClapiColors.OnSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (copied) "Copied!" else "Copy Details",
                    color = if (copied) ClapiColors.StatusSuccess else ClapiColors.OnSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Report on GitHub button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ClapiColors.Primary)
                    .clickable {
                        val issueUrl = buildGitHubIssueUrl(
                            stackTrace = stackTrace,
                            version = appVersion,
                            operatingSystem = operatingSystem,
                            title = "Crash: ${throwable::class.simpleName ?: "Unexpected Error"}: ${throwable.message?.take(60) ?: ""}".trim(),
                        )
                        if (onReportBug != null) {
                            onReportBug(issueUrl)
                        } else {
                            openUrlInBrowser(issueUrl)
                        }
                    }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("crash-dialog-report-button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_open_in_new),
                    contentDescription = null,
                    tint = ClapiColors.OnPrimary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Report on GitHub",
                    color = ClapiColors.OnPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Restart button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ClapiColors.SurfaceContainer)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                    .clickable(onClick = onRestart)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("crash-dialog-restart-button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_refresh),
                    contentDescription = null,
                    tint = ClapiColors.OnSurface,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Restart",
                    color = ClapiColors.OnSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Consistent Clapi-styled dialog for displaying unexpected crashes and fatal exceptions,
 * with options to copy full diagnostic details and report the issue on GitHub.
 */
@Composable
fun CrashReportDialog(
    throwable: Throwable,
    onDismiss: () -> Unit = {},
    onRestart: () -> Unit = onDismiss,
    modifier: Modifier = Modifier,
    appVersion: String = GeneratedBuildInfo.APP_VERSION,
    operatingSystem: String = getOperatingSystemString(),
    onReportBug: ((url: String) -> Unit)? = null,
    onCopyDetails: ((String) -> Unit)? = null,
) {
    val dragState = rememberDraggableDialogState()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.draggableDialogBackdrop(dragState, onDismiss = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            CrashReportContent(
                throwable = throwable,
                onRestart = onRestart,
                modifier = modifier
                    .draggableDialogCard(dragState, cornerRadius = 12.dp)
                    .widthIn(min = 440.dp, max = 640.dp)
                    .heightIn(min = 360.dp, max = 520.dp),
                appVersion = appVersion,
                operatingSystem = operatingSystem,
                onReportBug = onReportBug,
                onCopyDetails = onCopyDetails,
            )
        }
    }
}
