package com.example.ui.screen.player

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
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.model.MovieSubject
import com.example.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    subject: MovieSubject,
    onBackPress: () -> Unit
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

    // Selected Stream update hone par video load karna
    LaunchedEffect(viewModel.selectedStream) {
        viewModel.selectedStream?.url?.let { streamUrl ->
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

    // Dialogs state
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // ================= 1. GESTURE LISTENER (LEFT/RIGHT DRAG) =================
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            delay(1200)
                            viewModel.hideBrightnessHud()
                            viewModel.hideVolumeHud()
                        }
                    }
                ) { change, dragAmount ->
                    val isLeftSide = change.position.x < (size.width / 2)
                    if (isLeftSide) {
                        // LEFT SIDE: Brightness
                        viewModel.adjustBrightness(dragAmount)
                        activity?.window?.let { window ->
                            val lp = window.attributes
                            lp.screenBrightness = viewModel.brightness
                            window.attributes = lp
                        }
                    } else {
                        // RIGHT SIDE: Volume
                        viewModel.adjustVolume(dragAmount)
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

        // ================= 2. TOP ACTION BAR (QUALITY, AUDIO, DOWNLOAD) =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, start = 14.dp, end = 14.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            IconButton(
                onClick = onBackPress,
                modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            // Title
            Text(
                text = subject.name,
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )

            // Right Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Quality Button
                IconButton(
                    onClick = { showQualityMenu = true },
                    modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Quality", tint = Color.White)
                }

                // Audio / Subtitle Button
                IconButton(
                    onClick = { showSubtitleMenu = true },
                    modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Language, contentDescription = "Audio/Subtitles", tint = Color.White)
                }

                // Download Button
                IconButton(
                    onClick = { viewModel.downloadCurrentMedia(subject, viewModel.currentEpisode) },
                    modifier = Modifier.background(Color(0x666C5CE7), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                }
            }
        }

        // ================= 3. HUD INDICATORS (VOLUME & BRIGHTNESS) =================
        // Brightness HUD (Left)
        if (viewModel.showBrightnessHud) {
            HudDisplay(
                icon = Icons.Default.BrightnessMedium,
                value = (viewModel.brightness * 100).toInt(),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 30.dp)
            )
        }

        // Volume HUD (Right)
        if (viewModel.showVolumeHud) {
            HudDisplay(
                icon = Icons.Default.VolumeUp,
                value = (viewModel.volume * 100).toInt(),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 30.dp)
            )
        }

        // ================= 4. QUALITY SELECTION DIALOG =================
        if (showQualityMenu) {
            AlertDialog(
                onDismissRequest = { showQualityMenu = false },
                title = { Text("Quality Select Karein", color = Color.White) },
                text = {
                    Column {
                        val streams = viewModel.playInfo?.streams ?: emptyList()
                        if (streams.isEmpty()) {
                            Text("Default Auto Stream Active", color = Color.Gray)
                        } else {
                            streams.forEach { stream ->
                                TextButton(
                                    onClick = {
                                        viewModel.selectQuality(stream)
                                        showQualityMenu = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stream.resolution ?: stream.format ?: "Stream Quality",
                                        color = if (viewModel.selectedStream == stream) Color(0xFF6C5CE7) else Color.White
                                    )
                                }
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

        // ================= 5. AUDIO / SUBTITLE DIALOG =================
        if (showSubtitleMenu) {
            AlertDialog(
                onDismissRequest = { showSubtitleMenu = false },
                title = { Text("Audio / Subtitle Select Karein", color = Color.White) },
                text = {
                    Column {
                        val subtitles = viewModel.playInfo?.subtitles ?: emptyList()
                        if (subtitles.isEmpty()) {
                            Text("Koi extra language track nahi mila.", color = Color.Gray)
                        } else {
                            subtitles.forEach { sub ->
                                TextButton(
                                    onClick = {
                                        viewModel.selectSubtitleTrack(sub)
                                        showSubtitleMenu = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = sub.language ?: "Audio Track",
                                        color = if (viewModel.selectedSubtitle == sub) Color(0xFF6C5CE7) else Color.White
                                    )
                                }
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
