package io.github.crustack.clapi.ui.shared.components.dialogs.env

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.crustack.clapi.core.network.keepassxc.createKeePassXcClient
import io.github.crustack.clapi.core.network.keeper.createKeeperClient
import io.github.crustack.clapi.ui.shared.components.VariableAwareTextField
import io.github.crustack.clapi.ui.shared.components.handPointerHover
import io.github.crustack.clapi.ui.shared.generated.resources.*
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.platform.*
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.MutableExternalSecret
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jetbrains.compose.resources.painterResource

internal const val ENV_RESIZE_EDGE_DP = 12f
internal const val ENV_RESIZE_CORNER_DP = 20f

@Composable
internal fun EnvironmentResizeHandles(
    onResizeStart: (EnvironmentResizeHandle) -> Unit,
    onResize: (EnvironmentResizeHandle, Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(ENV_RESIZE_EDGE_DP.dp)
                .pointerHoverIcon(horizontalResizeCursor)
                .platformResizeCursorStyle(isHorizontal = true)
                .environmentResizeHandle(
                    handle = EnvironmentResizeHandle.RIGHT,
                    onResizeStart = { onResizeStart(EnvironmentResizeHandle.RIGHT) },
                    onResize = { dx, dy -> onResize(EnvironmentResizeHandle.RIGHT, dx, dy) },
                    onResizeEnd = onResizeEnd,
                )
                .testTag("env-resize-right"),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(ENV_RESIZE_EDGE_DP.dp)
                .pointerHoverIcon(verticalResizeCursor)
                .platformResizeCursorStyle(isHorizontal = false)
                .environmentResizeHandle(
                    handle = EnvironmentResizeHandle.BOTTOM,
                    onResizeStart = { onResizeStart(EnvironmentResizeHandle.BOTTOM) },
                    onResize = { dx, dy -> onResize(EnvironmentResizeHandle.BOTTOM, dx, dy) },
                    onResizeEnd = onResizeEnd,
                )
                .testTag("env-resize-bottom"),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(ENV_RESIZE_CORNER_DP.dp)
                .pointerHoverIcon(nwseResizeCursor)
                .platformDiagonalResizeCursorStyle(isNwSe = true)
                .environmentResizeHandle(
                    handle = EnvironmentResizeHandle.BOTTOM_RIGHT,
                    onResizeStart = { onResizeStart(EnvironmentResizeHandle.BOTTOM_RIGHT) },
                    onResize = { dx, dy -> onResize(EnvironmentResizeHandle.BOTTOM_RIGHT, dx, dy) },
                    onResizeEnd = onResizeEnd,
                )
                .testTag("env-resize-corner"),
        ) {
            val gripColor = ClapiColors.OnSurfaceDim
            Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                val step = size.minDimension / 4f
                for (i in 1..3) {
                    val o = step * i
                    drawLine(
                        color = gripColor,
                        start = Offset(size.width - o, size.height),
                        end = Offset(size.width, size.height - o),
                        strokeWidth = 1.5f,
                    )
                }
            }
        }
    }
}

internal fun Modifier.lockedResizeCursor(handle: EnvironmentResizeHandle?): Modifier = when (handle) {
    EnvironmentResizeHandle.RIGHT ->
        pointerHoverIcon(horizontalResizeCursor, overrideDescendants = true)
            .platformResizeCursorStyle(isHorizontal = true)
    EnvironmentResizeHandle.BOTTOM ->
        pointerHoverIcon(verticalResizeCursor, overrideDescendants = true)
            .platformResizeCursorStyle(isHorizontal = false)
    EnvironmentResizeHandle.BOTTOM_RIGHT ->
        pointerHoverIcon(nwseResizeCursor, overrideDescendants = true)
            .platformDiagonalResizeCursorStyle(isNwSe = true)
    else -> this
}

@Composable
internal fun Modifier.environmentResizeHandle(
    handle: EnvironmentResizeHandle,
    onResizeStart: () -> Unit,
    onResize: (dxDp: Float, dyDp: Float) -> Unit,
    onResizeEnd: () -> Unit,
): Modifier {
    val layout = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val onStartState = rememberUpdatedState(onResizeStart)
    val onDragState = rememberUpdatedState(onResize)
    val onEndState = rememberUpdatedState(onResizeEnd)
    return onGloballyPositioned { layout.value = it }
        .pointerInput(handle) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                val originRoot = layout.value?.takeIf { it.isAttached }?.localToRoot(down.position)
                    ?: return@awaitEachGesture
                down.consume()
                onStartState.value()
                try {
                    drag(down.id) { change ->
                        val currentRoot = layout.value?.takeIf { it.isAttached }?.localToRoot(change.position)
                        if (currentRoot != null) {
                            change.consume()
                            onDragState.value(
                                (currentRoot.x - originRoot.x).toDp().value,
                                (currentRoot.y - originRoot.y).toDp().value,
                            )
                        }
                    }
                } finally {
                    onEndState.value()
                }
            }
        }
}
