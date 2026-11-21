package io.github.crustack.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.crustack.ui.shared.theme.ClapiColors

@Composable
fun ErrorMessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 560.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ClapiColors.Surface)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(12.dp))
                .padding(20.dp)
                .testTag("error-dialog"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = ClapiColors.Error, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(message, color = ClapiColors.OnSurface, fontSize = 13.sp)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "OK",
                    color = ClapiColors.OnPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.Primary)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("error-dialog-ok"),
                )
            }
        }
    }
}
