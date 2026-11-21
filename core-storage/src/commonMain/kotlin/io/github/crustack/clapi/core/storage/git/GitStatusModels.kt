package io.github.crustack.clapi.core.storage.git

enum class GitChangeKind {
    MODIFIED,
    ADDED,
    DELETED,
    UNTRACKED,
    CONFLICTED,
}

data class GitFileChange(
    val relativePath: String,
    val collectionName: String,
    val changeKind: GitChangeKind,
)

data class CollectionGitStatus(
    val branch: String = "main",
    val isClean: Boolean = true,
    val hasConflicts: Boolean = false,
    val changes: List<GitFileChange> = emptyList(),
    val aheadCount: Int = 0,
    val behindCount: Int = 0,
    val lastError: String? = null,
)

data class GitRepositoryClone(
    val repoId: String,
    val remoteUrl: String,
    val workingDirectory: String,
    val activeBranch: String,
    val registeredCollections: Set<String> = emptySet(),
)
