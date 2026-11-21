// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

@Composable
actual fun BrandIcon(modifier: Modifier) {
    Icon(
        painter = painterResource("icons/clapi-icon-64.png"),
        contentDescription = "Clapi",
        modifier = modifier,
        tint = androidx.compose.ui.graphics.Color.Unspecified,
    )
}
