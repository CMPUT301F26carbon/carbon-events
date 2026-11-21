/*
 * ChatPhotos.kt (Tausif)
 *
 * Photos in a chat: the button that picks one from the phone, the preview above the message box
 * before it is sent, the photo inside a message, and the full-screen view when it is tapped.
 *
 * Outstanding issues: photos are picked from the phone's gallery; there is no in-app camera.
 */
package com.carbon.events

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The round photo button beside the message box. It opens the phone's photo picker and hands
 * back the chosen photo, shrunk to [ChatPhotos.MAX_SIDE] pixels, or null if it couldn't be read.
 */
@Composable
fun ChatPhotoButton(onPicked: (String?) -> Unit, enabled: Boolean = true) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(compressImageToBase64(context, uri, maxSide = ChatPhotos.MAX_SIDE))
    }
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(EventsColors.Tint).border(1.dp, EventsColors.TintStroke, CircleShape)
            .clickable(enabled = enabled) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            .testTag("chat_photo_button"),
        contentAlignment = Alignment.Center,
    ) { EventsIcon(R.drawable.ic_image, tint = EventsColors.Text, size = 22.dp, contentDescription = "Add a photo") }
}

/** The photo waiting to be sent, above the message box, with a cross to take it off. */
@Composable
fun PendingPhoto(base64: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val bitmap = remember(base64) { decodeBase64Image(base64) }
    Box(modifier.size(96.dp).testTag("chat_pending_photo")) {
        if (bitmap != null) Image(bitmap, "Photo to send", Modifier.fillMaxSize().clip(EventsShapes.Medium), contentScale = ContentScale.Crop)
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove).testTag("chat_pending_photo_remove"),
            contentAlignment = Alignment.Center,
        ) { EventsIcon(R.drawable.ic_close, tint = Color.White, size = 16.dp, contentDescription = "Remove photo") }
    }
}

/** A photo inside a message bubble; tapping it opens it full screen, and [onLongClick] (if given) runs on a long press. */
@Composable
fun MessagePhoto(base64: String, messageId: String, onLongClick: (() -> Unit)? = null, onOpen: () -> Unit) {
    val bitmap = remember(base64) { decodeBase64Image(base64) }
    Box(
        Modifier.widthIn(max = 252.dp).heightIn(max = 320.dp).clip(RoundedCornerShape(12.dp))
            .combinedClickable(onLongClick = onLongClick, onClick = onOpen).testTag("chat_photo_$messageId"),
    ) {
        if (bitmap != null) Image(bitmap, "Photo", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        else Box(Modifier.size(160.dp).background(EventsColors.Tint), contentAlignment = Alignment.Center) {
            EventsIcon(R.drawable.ic_image, tint = EventsColors.TextSecondary, size = 28.dp, contentDescription = "Photo couldn't be shown")
        }
    }
}

/** A chat photo full screen on black, closed with the cross or the back button. */
@Composable
fun PhotoViewer(base64: String, onClose: () -> Unit) {
    val bitmap = remember(base64) { decodeBase64Image(base64) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("chat_photo_viewer")) {
            if (bitmap != null) Image(bitmap, "Photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Box(Modifier.statusBarsPadding().padding(PageMargin).align(Alignment.TopStart)) {
                GlassIconButton(R.drawable.ic_close, "Close", onClose, Modifier.testTag("chat_photo_close"))
            }
        }
    }
}
