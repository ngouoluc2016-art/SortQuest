package com.sortquest.app.data.mapper

import com.sortquest.app.data.local.entity.AutoRuleEntity
import com.sortquest.app.data.local.entity.FileEntity
import com.sortquest.app.data.local.entity.SortHistoryEntity
import com.sortquest.app.data.local.entity.ThematicFolderEntity
import com.sortquest.app.data.local.entity.TrashEntity
import com.sortquest.app.data.local.entity.UserProgressEntity
import com.sortquest.app.domain.model.Badge
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.model.FileKind
import com.sortquest.app.domain.model.FolderIcon
import com.sortquest.app.domain.model.ThematicFolder
import com.sortquest.app.domain.model.HistoryEntry
import com.sortquest.app.domain.model.Level
import com.sortquest.app.domain.model.SortRecord
import com.sortquest.app.domain.model.SortRule
import com.sortquest.app.domain.model.TrashItem
import com.sortquest.app.domain.model.UserProgress

fun FileEntity.toDomain() = FileItem(
    id = id,
    path = path,
    name = name,
    extension = extension,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    kind = kind,
    category = category,
    suggestedTargetDir = suggestedTargetDir,
    matchedKeyword = matchedKeyword,
    isDuplicate = isDuplicate,
    status = status,
    partialHash = partialHash,
    contentHash = contentHash,
    suggestedLabel = suggestedLabel,
    suggestedIcon = suggestedIcon?.let { runCatching { FolderIcon.valueOf(it) }.getOrNull() },
)

fun ThematicFolderEntity.toDomain() = ThematicFolder(
    id = id,
    name = name,
    description = description,
    relativePath = relativePath,
    icon = runCatching { FolderIcon.valueOf(icon) }.getOrDefault(FolderIcon.CUSTOM),
    keywords = keywordsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
    extensions = extensionsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
    kinds = kindsCsv.split(',').mapNotNull { runCatching { FileKind.valueOf(it.trim()) }.getOrNull() }.toSet(),
    enabled = enabled,
    builtIn = builtIn,
    locked = locked,
    splitByYear = splitByYear,
    sortOrder = sortOrder,
)

fun ThematicFolder.toEntity() = ThematicFolderEntity(
    id = id,
    name = name,
    description = description,
    relativePath = relativePath,
    icon = icon.name,
    keywordsCsv = keywords.joinToString(","),
    extensionsCsv = extensions.joinToString(","),
    kindsCsv = kinds.joinToString(",") { it.name },
    enabled = enabled,
    builtIn = builtIn,
    locked = locked,
    splitByYear = splitByYear,
    sortOrder = sortOrder,
)

fun SortHistoryEntity.toDomain() = SortRecord(
    id = id,
    fileId = fileId,
    fileName = fileName,
    action = action,
    source = source,
    fromPath = fromPath,
    toPath = toPath,
    sizeBytes = sizeBytes,
    wasDuplicate = wasDuplicate,
    xpGained = xpGained,
    timestamp = timestamp,
    ruleId = ruleId,
    batchId = batchId,
)

fun SortHistoryEntity.toHistoryEntry() = HistoryEntry(toDomain(), undone)

fun TrashEntity.toDomain() = TrashItem(
    id = id,
    fileId = fileId,
    fileName = fileName,
    originalPath = originalPath,
    trashPath = trashPath,
    sizeBytes = sizeBytes,
    trashedAt = trashedAt,
    expiresAt = expiresAt,
)

fun AutoRuleEntity.toDomain() = SortRule(
    id = id,
    name = name,
    keyword = keyword,
    extensions = extensionsCsv.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
    minSizeBytes = minSizeBytes,
    targetDir = targetDir,
    enabled = enabled,
    priority = priority,
    createdAt = createdAt,
    lastRunAt = lastRunAt,
    filesMoved = filesMoved,
)

fun SortRule.toEntity() = AutoRuleEntity(
    id = id,
    name = name,
    keyword = keyword,
    extensionsCsv = extensions.joinToString(","),
    minSizeBytes = minSizeBytes,
    targetDir = targetDir,
    enabled = enabled,
    priority = priority,
    createdAt = createdAt,
    lastRunAt = lastRunAt,
    filesMoved = filesMoved,
)

private fun String.toBadges(): Set<Badge> =
    split(',').filter { it.isNotBlank() }
        .mapNotNull { runCatching { Badge.valueOf(it) }.getOrNull() } // badge retiré = ignoré
        .toSet()

private fun Set<Badge>.toCsv(): String = sortedBy { it.ordinal }.joinToString(",") { it.name }

fun UserProgressEntity.toDomain() = UserProgress(
    totalXp = totalXp,
    bytesFreed = bytesFreed,
    filesSorted = filesSorted,
    duplicatesTrashed = duplicatesTrashed,
    autoPilotMoves = autoPilotMoves,
    currentStreak = currentStreak,
    bestStreak = bestStreak,
    lastActiveEpochDay = lastActiveEpochDay,
    bestCombo = bestCombo,
    badges = badgesCsv.toBadges(),
    celebratedLevel = Level.fromIndex(celebratedLevelIndex),
    seenBadges = seenBadgesCsv.toBadges(),
)

fun UserProgress.toEntity() = UserProgressEntity(
    totalXp = totalXp,
    levelIndex = level.ordinal,
    bytesFreed = bytesFreed,
    filesSorted = filesSorted,
    duplicatesTrashed = duplicatesTrashed,
    autoPilotMoves = autoPilotMoves,
    currentStreak = currentStreak,
    bestStreak = bestStreak,
    lastActiveEpochDay = lastActiveEpochDay,
    bestCombo = bestCombo,
    badgesCsv = badges.toCsv(),
    celebratedLevelIndex = celebratedLevel.ordinal,
    seenBadgesCsv = seenBadges.toCsv(),
)
