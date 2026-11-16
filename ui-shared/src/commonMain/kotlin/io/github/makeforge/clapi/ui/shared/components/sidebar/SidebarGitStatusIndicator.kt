package io.github.makeforge.clapi.ui.shared.components.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_fork_right
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import org.jetbrains.compose.resources.painterResource

@Composable
fun SidebarGitStatusIndicator(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    val gitCollection = state.activeGitCollectionNode ?: return
    val gitStatus = state.activeGitStatus
    val branchName = gitStatus?.branch?.ifBlank { gitCollection.gitBranch } ?: gitCollection.gitBranch
    val isDirty = gitStatus != null && (!gitStatus.isClean || gitStatus.hasConflicts)

    LaunchedEffect(
        gitCollection.id,
        gitCollection.gitRemoteUrl,
        gitCollection.gitBranch,
        state.activeTabIndex,
        state.selectedCollectionId,
        state.selectedRequestId,
    ) {
        state.refreshGitStatusForCollection(gitCollection)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ClapiColors.Surface)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { state.showGitSyncDialog = true }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("sidebar-git-indicator"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_fork_right),
            contentDescription = Strings.gitSync,
            tint = ClapiColors.Primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = branchName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = ClapiColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("sidebar-git-branch-label"),
        )
        if (isDirty) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (gitStatus?.hasConflicts == true) ClapiColors.Error else ClapiColors.Primary)
                    .testTag("sidebar-git-dirty-dot"),
            )
        }
    }
}
