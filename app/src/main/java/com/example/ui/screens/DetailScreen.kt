package com.example.ui.screens

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ui.viewmodel.MovieViewModel
import com.example.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun DetailScreen(
    movieViewModel: MovieViewModel,
    onBackPress: () -> Unit = {},
    playerViewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val mediaTitle = playerViewModel.currentEpisode?.let { ep ->
        "S${ep.seasonNumber} E${ep.episodeNumber}"
    } ?: "Now Playing"

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    LaunchedEffect(playerViewModel.selectedStream) {
        playerViewModel.selectedStream?.url?.let { streamUrl ->
            if (streamUrl.isNotEmpty()) {
                exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    var showQualityMenu by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // ================= 1. GESTURES (LEFT: BRIGHTNESS, RIGHT: VOLUME) =================
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            delay(1200)
                            playerViewModel.hideBrightnessHud()
                            playerViewModel.hideVolumeHud()
                        }
                    }
                ) { change, dragAmount ->
                    val isLeftSide = change.position.x < (size.width / 2)
                    if (isLeftSide) {
                        playerViewModel.adjustBrightness(dragAmount)
                        activity?.window?.let { window ->
                            val lp = window.attributes
                            lp.screenBrightness = playerViewModel.brightness
                            window.attributes = lp
                        }
                    } else {
                        playerViewModel.adjustVolume(dragAmount)
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ================= 2. TOP ACTION BAR =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, start = 14.dp, end = 14.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackPress,
                modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Text(
                text = mediaTitle,
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Quality Button
                IconButton(
                    onClick = { showQualityMenu = true },
                    modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Quality", tint = Color.White)
                }

                // Subtitle/Language Button
                IconButton(
                    onClick = { showSubtitleMenu = true },
                    modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Language, contentDescription = "Language", tint = Color.White)
                }

                // Download Button
                IconButton(
                    onClick = {
                        val stream = playerViewModel.selectedStream
                        if (stream != null && !stream.url.isNullOrEmpty()) {
                            try {
                                val req = DownloadManager.Request(Uri.parse(stream.url)).apply {
                                    setTitle("NextGen Download")
                                    setDescription("Downloading video...")
                                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "video_${System.currentTimeMillis()}.mp4")
                                    setAllowedOverMetered(true)
                                    setAllowedOverRoaming(true)
                                }
                                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                dm.enqueue(req)
                                Toast.makeText(context, "Download Shuru Ho Gaya!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Video stream load ho rahi hai...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.background(Color(0x666C5CE7), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                }
            }
        }

        // ================= 3. HUD INDICATORS (GESTURES) =================
        if (playerViewModel.showBrightnessHud) {
            HudDisplay(
                icon = Icons.Default.BrightnessMedium,
                value = (playerViewModel.brightness * 100).toInt(),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 30.dp)
            )
        }

        if (playerViewModel.showVolumeHud) {
            HudDisplay(
                icon = Icons.Default.VolumeUp,
                value = (playerViewModel.volume * 100).toInt(),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 30.dp)
            )
        }

        // ================= 4. QUALITY DIALOG =================
        if (showQualityMenu) {
            AlertDialog(
                onDismissRequest = { showQualityMenu = false },
                title = { Text("Quality Select Karein", color = Color.White) },
                text = {
                    Column {
                        listOf("1080p (Full HD)", "720p (HD)", "480p (SD)", "Auto").forEach { quality ->
                            TextButton(
                                onClick = {
                                    showQualityMenu = false
                                    Toast.makeText(context, "Quality: $quality selected", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = quality, color = Color.White)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQualityMenu = false }) { Text("Close") }
                },
                containerColor = Color(0xFF161822)
            )
        }

        // ================= 5. SUBTITLE / AUDIO DIALOG =================
        if (showSubtitleMenu) {
            AlertDialog(
                onDismissRequest = { showSubtitleMenu = false },
                title = { Text("Language / Subtitles", color = Color.White) },
                text = {
                    Column {
                        listOf("Default Audio", "Hindi", "English", "Japanese").forEach { lang ->
                            TextButton(
                                onClick = {
                                    showSubtitleMenu = false
                                    Toast.makeText(context, "Audio: $lang selected", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = lang, color = Color.White)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleMenu = false }) { Text("Close") }
                },
                containerColor = Color(0xFF161822)
            )
        }
    }
}

@Composable
fun HudDisplay(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xCC101117), RoundedCornerShape(12.dp))
            .padding(vertical = 16.dp, horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text("$value%", color = Color.White, fontSize = 13.sp)
    }
}
