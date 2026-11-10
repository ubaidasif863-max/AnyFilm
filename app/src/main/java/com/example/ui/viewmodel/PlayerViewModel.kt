package com.example.ui.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.HistoryEntity
import com.example.data.local.MovieDatabase
import com.example.data.model.Episode
import com.example.data.model.MovieSubject
import com.example.data.model.PlayInfo
import com.example.data.model.StreamItem
import com.example.data.model.SubtitleItem
import com.example.data.network.MovieBoxRetrofitClient
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.MovieRepository
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application)
    private val database = MovieDatabase.getDatabase(application)
    private val repository = MovieRepository(
        MovieBoxRetrofitClient.getApi(prefs),
        database.movieDao()
    )
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // --- Purana State ---
    var currentEpisode by mutableStateOf<Episode?>(null)
    var playInfo by mutableStateOf<PlayInfo?>(null)
    var selectedStream by mutableStateOf<StreamItem?>(null)
    var selectedSubtitle by mutableStateOf<SubtitleItem?>(null)
    var isPlaying by mutableStateOf(false)
    var playbackProgressMs by mutableStateOf(0L)
    var isPlaybackLoading by mutableStateOf(false)
    var mediaDurationMs by mutableStateOf(0L)

    // ================= 1. GESTURE STATES (BRIGHTNESS & VOLUME) =================
    var brightness by mutableFloatStateOf(0.5f)
    var showBrightnessHud by mutableStateOf(false)

    var volume by mutableFloatStateOf(
        try {
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
            cur / max
        } catch (e: Exception) {
            0.5f
        }
    )
    var showVolumeHud by mutableStateOf(false)

    // ================= 2. GESTURE FUNCTIONS =================
    // Left Drag: Brightness kam ya zyada
    fun adjustBrightness(dragAmount: Float) {
        brightness = (brightness - (dragAmount * 0.005f)).coerceIn(0.01f, 1.0f)
        showBrightnessHud = true
    }

    fun hideBrightnessHud() {
        showBrightnessHud = false
    }

    // Right Drag: Volume kam ya zyada
    fun adjustVolume(dragAmount: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newRatio = (volume - (dragAmount * 0.005f)).coerceIn(0.0f, 1.0f)
        volume = newRatio
        try {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (newRatio * maxVol).toInt(),
                0
            )
        } catch (e: Exception) {
            // Ignore
        }
        showVolumeHud = true
    }

    fun hideVolumeHud() {
        showVolumeHud = false
    }

    // ================= 3. QUALITY SELECTION =================
    fun selectQuality(stream: StreamItem) {
        selectedStream = stream
        Toast.makeText(getApplication(), "Quality switch ho rahi hai...", Toast.LENGTH_SHORT).show()
    }

    // ================= 4. LANGUAGE / SUBTITLE SELECTION =================
    fun selectSubtitleTrack(subtitle: SubtitleItem?) {
        selectedSubtitle = subtitle
        val lang = subtitle?.language ?: "Default"
        Toast.makeText(getApplication(), "Subtitle/Audio: $lang", Toast.LENGTH_SHORT).show()
    }

    // ================= 5. NATIVE DOWNLOAD FUNCTION =================
    fun downloadCurrentMedia(subject: MovieSubject, episode: Episode?) {
        val streamUrl = selectedStream?.url
        if (streamUrl.isNullOrEmpty()) {
            Toast.makeText(getApplication(), "Download link available nahi hai", Toast.LENGTH_SHORT).show()
            return
        }

        val safeName = subject.name.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val fileName = if (subject.isTvShow && episode != null) {
            "${safeName}_S${episode.seasonNumber}_E${episode.episodeNumber}.mp4"
        } else {
            "${safeName}.mp4"
        }

        try {
            val request = DownloadManager.Request(Uri.parse(streamUrl)).apply {
                setTitle(fileName.removeSuffix(".mp4"))
                setDescription("AnyFilm / NextGen Download")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadManager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(getApplication(), "Download Shuru! Notification bar check karein.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(getApplication(), "Download Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Purana Playback History Function ---
    fun updatePlaybackProgress(subject: MovieSubject, episode: Episode?, positionMs: Long, durationMs: Long) {
        playbackProgressMs = positionMs
        mediaDurationMs = durationMs
        val historyId = if (subject.isTvShow) {
            val ep = episode ?: return
            "${subject.id}_S${ep.seasonNumber}_E${ep.episodeNumber}"
        } else {
            subject.id
        }

        viewModelScope.launch {
            try {
                repository.insertHistory(
                    HistoryEntity(
                        id = historyId,
                        subjectId = subject.id,
                        name = subject.name,
                        poster = subject.poster,
                        isTvShow = subject.isTvShow,
                        season = if (subject.isTvShow) episode?.seasonNumber ?: 1 else 0,
                        episode = if (subject.isTvShow) episode?.episodeNumber ?: 1 else 0,
                        seeTime = positionMs,
                        totalTime = durationMs
                    )
                )
            } catch (e: Exception) {
                // Ignore failure
            }
        }
    }
}
