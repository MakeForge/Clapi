// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.makeforge.clapi.ui.shared.build.GeneratedBuildInfo
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.DocsLinks
import io.github.makeforge.clapi.ui.shared.platform.PlatformColumnVerticalScrollbar
import io.github.makeforge.clapi.ui.shared.platform.openUrlInBrowser
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors

@Composable
fun HelpAboutDialog(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    if (!state.showHelpDialog) return

    val dragState = rememberDraggableDialogState()

    Dialog(onDismissRequest = { state.showHelpDialog = false }) {
        Box(
            modifier = Modifier.draggableDialogBackdrop(dragState, onDismiss = { state.showHelpDialog = false }),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .draggableDialogCard(dragState, cornerRadius = 12.dp)
                    .widthIn(min = 720.dp, max = 920.dp)
                    .testTag("help-about-dialog"),
            ) {
                DialogTitleBar(
                    title = "Help & About",
                    actions = {
                        DocsHelpButton(
                            url = DocsLinks.baseUrl,
                            tooltip = "Online Documentation",
                            tag = "help-about-docs-btn",
                            size = 28.dp,
                        )
                    },
                    closeButtonText = "Close",
                    closeButtonTag = "help-about-close",
                    onClose = { state.showHelpDialog = false },
                    onDrag = dragState::onDrag,
                )

                val scrollState = rememberScrollState()
                Box(modifier = Modifier.fillMaxWidth().height(520.dp).padding(20.dp)) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                    HelpSection("About ${Strings.appName}", DocsLinks.baseUrl) {
                        HelpText("Clapi is a lightweight API client for HTTP workflows with local-first request editing, scripting, environments, and test assertions.")
                    }

                    HelpSection("Documentation", DocsLinks.baseUrl) {
                        HelpText("Comprehensive documentation and guides are available at:")
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { openUrlInBrowser(DocsLinks.baseUrl) }
                                .padding(vertical = 2.dp)
                                .testTag("help-about-docs-link"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = DocsLinks.baseUrl,
                                color = ClapiColors.Primary,
                                fontSize = 12.sp,
                                textDecoration = TextDecoration.Underline,
                            )
                        }
                    }

                    HelpSection("Feature Overview", DocsLinks.baseUrl) {
                        HelpText("• Request builder for methods, URL, params, headers, auth, and body")
                        HelpText("• Pre-request and post-request scripts")
                        HelpText("• Collections, history, environments, and global variables")
                        HelpText("• Multi-tab editing with save and tab management shortcuts")
                        HelpText("• Response viewer plus network/test logs")
                    }

                    HelpSection("How to Use", DocsLinks.baseUrl) {
                        HelpText("1) Create or open a request tab.")
                        HelpText("2) Configure URL, method, auth, headers, and body.")
                        HelpText("3) Choose an environment for variable values.")
                        HelpText("4) Add optional pre-request/post-request scripts.")
                        HelpText("5) Send request and inspect response, logs, and tests.")
                    }

                    HelpSection("Shortcuts", DocsLinks.shortcuts) {
                        ShortcutRow("⌘ + Enter / Ctrl + Enter", "Send request or run MCP tool (or cancel if in progress)")
                        ShortcutRow("⌘ + Shift + [ / Ctrl + Shift + [", "Move active tab left")
                        ShortcutRow("⌘ + Shift + ] / Ctrl + Shift + ]", "Move active tab right")
                        ShortcutRow("⌘ + S / Ctrl + S", "Save active request")
                        ShortcutRow("⌘ + W / Ctrl + W", "Close active tab")
                        ShortcutRow("⌘ + N / Ctrl + N", "Create new request tab")
                        ShortcutRow("⌘ + , / Ctrl + ,", "Open settings")
                    }

                    HelpSection("Scripting Overview", DocsLinks.scriptingArchitecture) {
                        HelpText("Use pre-request scripts to prepare headers, query values, body, and variables before dispatch.")
                        HelpText("Use post-request scripts to assert response behavior and persist extracted values into variables.")
                        HelpText("The default scripting namespace is clapi and can be changed in Settings → Scripts.")
                    }

                    HelpSection("Version & Build Info", DocsLinks.zeroTelemetry) {
                        HelpText("Version: ${GeneratedBuildInfo.APP_VERSION}")
                        HelpText("Build: Kotlin Multiplatform + Compose Multiplatform")
                        HelpText("Supported platforms: macOS, Windows, Linux")
                    }

                    HelpSection("License & Attribution", DocsLinks.techstack) {
                        HelpText("Licensed under the Apache License, Version 2.0. Copyright 2026 MakeForge.")
                        HelpText("Includes software originally developed for ReqLab by snj07 (Copyright 2026 ReqLab Contributors), modified by MakeForge (2026).")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            HelpLink(
                                text = "LICENSE",
                                url = "https://github.com/MakeForge/Clapi/blob/main/LICENSE",
                                testTag = "help-about-license-link",
                            )
                            HelpLink(
                                text = "NOTICE",
                                url = "https://github.com/MakeForge/Clapi/blob/main/NOTICE",
                                testTag = "help-about-notice-link",
                            )
                            HelpLink(
                                text = "Third-Party Licenses",
                                url = DocsLinks.techstack,
                                testTag = "help-about-third-party-link",
                            )
                        }
                    }
                }
                    }
                PlatformColumnVerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
            }
        }
    }
}

@Composable
private fun HelpSection(title: String, helpUrl: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                color = ClapiColors.OnSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            if (helpUrl != null) {
                DocsHelpButton(
                    url = helpUrl,
                    tooltip = "$title documentation",
                    tag = "help-section-${title.lowercase().replace(" ", "-")}-docs-btn",
                    size = 18.dp,
                    iconSize = 13.dp,
                )
            }
        }
        content()
    }
}

@Composable
private fun HelpText(text: String) {
    Text(
        text = text,
        color = ClapiColors.OnSurfaceVariant,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
}

@Composable
private fun HelpLink(text: String, url: String, testTag: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { openUrlInBrowser(url) }
            .padding(vertical = 2.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = ClapiColors.Primary,
            fontSize = 12.sp,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun ShortcutRow(shortcut: String, action: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ClapiColors.SurfaceContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = shortcut,
            color = ClapiColors.OnSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(220.dp),
        )
        Text(
            text = action,
            color = ClapiColors.OnSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}
