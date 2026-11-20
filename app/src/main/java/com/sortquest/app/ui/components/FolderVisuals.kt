package com.sortquest.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sortquest.app.core.image.thumbnailModelFor
import com.sortquest.app.domain.model.FileKind
import com.sortquest.app.domain.model.FolderIcon
import com.sortquest.app.ui.theme.Neon

val FolderIcon.vector: ImageVector
    get() = when (this) {
        FolderIcon.RECEIPT -> Icons.Rounded.Receipt
        FolderIcon.BANK -> Icons.Rounded.AccountBalance
        FolderIcon.TAX -> Icons.Rounded.Calculate
        FolderIcon.HEALTH -> Icons.Rounded.LocalHospital
        FolderIcon.CONTRACT -> Icons.Rounded.Handshake
        FolderIcon.WORK -> Icons.Rounded.Work
        FolderIcon.SCHOOL -> Icons.Rounded.School
        FolderIcon.HOME -> Icons.Rounded.Home
        FolderIcon.VEHICLE -> Icons.Rounded.DirectionsCar
        FolderIcon.TRAVEL -> Icons.Rounded.Flight
        FolderIcon.IDENTITY -> Icons.Rounded.Person
        FolderIcon.SCREENSHOT -> Icons.Rounded.Screenshot
        FolderIcon.PHOTO -> Icons.Rounded.Image
        FolderIcon.VIDEO -> Icons.Rounded.Movie
        FolderIcon.MUSIC -> Icons.Rounded.MusicNote
        FolderIcon.BOOK -> Icons.Rounded.AutoStories
        FolderIcon.DOCUMENT -> Icons.Rounded.Description
        FolderIcon.ARCHIVE -> Icons.Rounded.Archive
        FolderIcon.APP -> Icons.Rounded.Android
        FolderIcon.CALENDAR -> Icons.Rounded.CalendarMonth
        FolderIcon.OTHER -> Icons.Rounded.Inventory2
        FolderIcon.CUSTOM -> Icons.Rounded.CreateNewFolder
    }

val FolderIcon.color: Color
    get() = when (this) {
        FolderIcon.RECEIPT, FolderIcon.BANK, FolderIcon.TAX, FolderIcon.CONTRACT, FolderIcon.WORK,
        FolderIcon.SCHOOL, FolderIcon.HOME, FolderIcon.VEHICLE, FolderIcon.IDENTITY, FolderIcon.DOCUMENT -> Neon.Cyan
        FolderIcon.HEALTH, FolderIcon.TRAVEL -> Neon.Lime
        FolderIcon.SCREENSHOT, FolderIcon.PHOTO, FolderIcon.VIDEO, FolderIcon.MUSIC -> Neon.Magenta
        FolderIcon.BOOK, FolderIcon.ARCHIVE -> Neon.Violet
        FolderIcon.APP -> Neon.Lime
        FolderIcon.CALENDAR, FolderIcon.CUSTOM -> Neon.Amber
        FolderIcon.OTHER -> Neon.TextMuted
    }

/** Pastille ronde d'un dossier (icône + teinte). */
@Composable
fun FolderBadge(icon: FolderIcon, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).background(icon.color.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon.vector, contentDescription = null, tint = icon.color, modifier = Modifier.size(size * 0.55f)) }
}

/**
 * Miniature d'un fichier pour les listes : vraie image (photo, 1re page PDF, image de vidéo)
 * ou, à défaut, l'icône de son type. Permet d'identifier un fichier d'un coup d'œil.
 */
@Composable
fun FileThumbnail(path: String, kind: FileKind, lastModified: Long, extension: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val context = LocalContext.current
    val model = remember(path, lastModified) { thumbnailModelFor(path, kind, lastModified) }
    var loaded by remember(path) { mutableStateOf(false) }
    Box(modifier.size(size).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        if (!loaded) FileKindIcon(extension, size = size)
        if (model != null) {
            AsyncImage(
                model = remember(model) { ImageRequest.Builder(context).data(model).size(LIST_THUMB_PX).build() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val LIST_THUMB_PX = 192
