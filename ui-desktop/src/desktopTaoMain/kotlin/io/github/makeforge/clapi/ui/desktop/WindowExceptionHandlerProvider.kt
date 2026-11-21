package io.github.makeforge.clapi.ui.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.WindowExceptionHandler
import dev.nucleusframework.window.tao.LocalWindowExceptionHandlerFactory
import dev.nucleusframework.window.tao.WindowExceptionHandlerFactory

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ProvideWindowExceptionHandler(
    onException: (Throwable) -> Unit,
    content: @Composable () -> Unit,
) {
    val factory = remember(onException) {
        WindowExceptionHandlerFactory { _ ->
            WindowExceptionHandler { throwable ->
                onException(throwable)
            }
        }
    }
    CompositionLocalProvider(
        LocalWindowExceptionHandlerFactory provides factory,
        content = content,
    )
}
