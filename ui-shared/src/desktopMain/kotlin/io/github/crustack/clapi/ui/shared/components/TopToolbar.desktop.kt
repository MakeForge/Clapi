package io.github.crustack.clapi.ui.shared.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
actual fun BrandIcon() {
    Icon(
        painter = painterResource("icons/clapi-icon-64.png"),
        contentDescription = "Clapi",
        modifier = Modifier.size(28.dp),
        tint = androidx.compose.ui.graphics.Color.Unspecified,
    )
}
