package com.example.ui.screens

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
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

    // ExoPlayer Instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Video stream change listener
    LaunchedEffect(playerViewModel.selectedStream) {
        playerViewModel.selectedStream?.url?.let { streamUrl ->
            exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // Clean up
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    var showQualityMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var currentTracks by remember { mutableStateOf<Tracks?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // ================= 1. GESTURE ENGINE (DRAG LEFT/RIGHT) =================
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
        // Video View
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

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Quality Button
                IconButton(
                    onClick = {
                        currentTracks = exoPlayer.currentTracks
                        showQualityMenu = true
                    },
                    modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Quality", tint = Color.White)
                }

                // Audio / Language Button
                IconButton(
                    onClick = {
                        currentTracks = exoPlayer.currentTracks
                        showAudioMenu = true
                    },
                    modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Language, contentDescription = "Audio Language", tint = Color.White)
                }

                // Download Button
                IconButton(
                    onClick = {
                        playerViewModel.selectedStream?.url?.let { downloadUrl ->
                            val dm = context.getSystemService(Activity.DOWNLOAD_SERVICE) as android.app.DownloadManager
                            val request = android.app.DownloadManager.Request(android.net.Uri.parse(downloadUrl)).apply {
                                setTitle("NextGen Video")
                                setDescription("Downloading stream...")
                                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "video_${System.currentTimeMillis()}.mp4")
                                setAllowedOverMetered(true)
                                setAllowedOverRoaming(true)
                            }
                            dm.enqueue(request)
                            android.widget.Toast.makeText(context, "Download shuru ho gaya!", android.widget.Toast.SHORT).show()
                        }
                    },
                    modifier = Modifier.background(Color(0x666C5CE7), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                }
            }
        }

        // ================= 3. HUD INDICATORS (VOLUME & BRIGHTNESS) =================
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
                        listOf("Auto", "1080p", "720p", "480p", "360p").forEach { quality ->
                            TextButton(
                                onClick = {
                                    val maxPixels = when (quality) {
                                        "1080p" -> 1920 * 1080
                                        "720p" -> 1280 * 720
                                        "480p" -> 854 * 480
                                        "360p" -> 640 * 360
                                        else -> Int.MAX_VALUE
                                    }
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setMaxVideoSize(maxPixels, maxPixels)
                                        .build()
                                    showQualityMenu = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(quality, color = Color.White, fontSize = 16.sp)
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

        // ================= 5. AUDIO LANGUAGE DIALOG =================
        if (showAudioMenu) {
            AlertDialog(
                onDismissRequest = { showAudioMenu = false },
                title = { Text("Audio Language Select Karein", color = Color.White) },
                text = {
                    Column {
                        val audioTracks = mutableListOf<Pair<Int, String>>()
                        currentTracks?.groups?.forEachIndexed { index, group ->
                            if (group.type == C.TRACK_TYPE_AUDIO) {
                                val format = group.getTrackFormat(0)
                                val name = format.language?.uppercase() ?: format.label ?: "Track ${index + 1}"
                                audioTracks.add(Pair(index, name))
                            }
                        }

                        if (audioTracks.isEmpty()) {
                            Text("Default Audio Track Active", color = Color.Gray)
                        } else {
                            audioTracks.forEach { (index, langName) ->
                                TextButton(
                                    onClick = {
                                        currentTracks?.groups?.getOrNull(index)?.let { grp ->
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setOverrideForType(TrackSelectionOverride(grp.mediaTrackGroup, 0))
                                                .build()
                                        }
                                        showAudioMenu = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(langName, color = Color.White, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioMenu = false }) { Text("Close") }
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
