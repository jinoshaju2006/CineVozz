package com.evee.cinevozz

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.evee.cinevozz.data.ArchiveMovie
import com.evee.cinevozz.data.ArchiveRepository
import kotlinx.coroutines.launch

private val Ink = Color(0xFF09070F)
private val Panel = Color(0xFF191323)
private val Purple = Color(0xFF9B6BFF)
private val Muted = Color(0xFFB7AEC5)

class MainActivity : ComponentActivity() {
    private val repository = ArchiveRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Purple,
                    background = Ink,
                    surface = Panel,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                CineVozzScreen(
                    repository = repository,
                    onLaunch = { task -> lifecycleScope.launch { task() } }
                )
            }
        }
    }
}

@Composable
private fun CineVozzScreen(
    repository: ArchiveRepository,
    onLaunch: (suspend () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var movies by remember { mutableStateOf<List<ArchiveMovie>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Discover films that are openly licensed for free viewing and downloads.") }
    var playing by remember { mutableStateOf<Pair<ArchiveMovie, String>?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var showDownloads by remember { mutableStateOf(false) }
    var downloads by remember { mutableStateOf<List<String>>(emptyList()) }

    fun search() {
        loading = true
        message = "Searching the open-film catalogue…"
        onLaunch {
            try {
                movies = repository.search(query)
                message = if (movies.isEmpty()) {
                    "No matching films with a supported licence were found. Try another title."
                } else {
                    "${movies.size} openly licensed films found."
                }
            } catch (e: Exception) {
                message = e.message ?: "Could not load films. Check your internet connection."
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CINEVOZZ", color = Color.White, fontSize = 25.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("FREE & LICENSED CINEMA", color = Purple, fontSize = 10.sp,
                    letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = {
                downloads = readDownloads(context)
                showDownloads = true
            }) {
                Text("Downloads", color = Purple, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("Discover films", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Search public-domain and openly licensed movies you can save offline.",
            color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search movie title…", color = Muted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Purple,
                    unfocusedBorderColor = Color(0xFF40354F),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Purple
                )
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { search() },
                enabled = !loading,
                modifier = Modifier.size(54.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (loading) LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(), color = Purple
        )
        Text(message, color = Muted, fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            items(movies, key = { it.identifier }) { movie ->
                MovieCard(
                    movie = movie,
                    busy = busyId == movie.identifier,
                    onPlay = {
                        busyId = movie.identifier
                        onLaunch {
                            try {
                                val stream = repository.findPlayableFile(movie)
                                playing = movie to stream
                            } catch (e: Exception) {
                                message = e.message ?: "Playback is unavailable."
                            } finally {
                                busyId = null
                            }
                        }
                    },
                    onDownload = {
                        busyId = movie.identifier
                        onLaunch {
                            try {
                                val fileUrl = repository.findPlayableFile(movie)
                                enqueueDownload(context, fileUrl, movie.title)
                                message = "Download queued: ${movie.title}"
                            } catch (e: Exception) {
                                message = e.message ?: "Download could not be started."
                            } finally {
                                busyId = null
                            }
                        }
                    }
                )
            }
        }
    }

    playing?.let { (movie, url) ->
        PlayerDialog(title = movie.title, url = url, onDismiss = { playing = null })
    }

    if (showDownloads) {
        AlertDialog(
            onDismissRequest = { showDownloads = false },
            title = { Text("My downloads") },
            text = {
                if (downloads.isEmpty()) {
                    Text("No downloads yet. Downloaded films are saved in Movies/CineVozz.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        downloads.forEach { Text(it, fontSize = 12.sp, color = Muted) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { downloads = readDownloads(context) }) {
                    Text("Refresh", color = Purple)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloads = false }) {
                    Text("Close", color = Purple)
                }
            },
            containerColor = Panel
        )
    }
}

@Composable
private fun MovieCard(
    movie: ArchiveMovie,
    busy: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(movie.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (movie.year.isNotBlank()) Text(movie.year, color = Purple, fontSize = 12.sp)
            Spacer(Modifier.height(7.dp))
            Text(
                movie.description.take(230).ifBlank {
                    "An openly licensed film hosted by the Internet Archive."
                },
                color = Muted, fontSize = 12.sp, lineHeight = 17.sp
            )
            Spacer(Modifier.height(9.dp))
            Text(
                "Licence: ${movie.licenseUrl.ifBlank { "Public domain (rights statement)" }}",
                color = Color(0xFFBFA5FF), fontSize = 10.sp
            )
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = onPlay,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple)
                ) {
                    if (busy) CircularProgressIndicator(
                        Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White
                    ) else Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Watch")
                }
                OutlinedButton(
                    onClick = onDownload,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Download")
                }
            }
            Text(
                "Source: Internet Archive • Check item rights before reuse.",
                color = Muted, fontSize = 9.sp
            )
        }
    }
}

private fun readDownloads(context: Context): List<String> {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val cursor = manager.query(DownloadManager.Query())
    return cursor.use { c ->
        buildList {
            val titleCol = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val bytesCol = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            while (c.moveToNext()) {
                val title = if (titleCol >= 0) c.getString(titleCol) else "Movie download"
                val status = if (statusCol >= 0) c.getInt(statusCol) else -1
                val downloaded = if (bytesCol >= 0) c.getLong(bytesCol) else 0L
                val total = if (totalCol >= 0) c.getLong(totalCol) else -1L
                val label = when (status) {
                    DownloadManager.STATUS_PENDING -> "Queued"
                    DownloadManager.STATUS_RUNNING -> "Downloading"
                    DownloadManager.STATUS_PAUSED -> "Paused"
                    DownloadManager.STATUS_SUCCESSFUL -> "Complete"
                    DownloadManager.STATUS_FAILED -> "Failed"
                    else -> "Unknown"
                }
                val percent = if (total > 0) " • ${downloaded * 100 / total}%" else ""
                add("$title — $label$percent")
            }
        }
    }
}

private fun enqueueDownload(context: Context, url: String, title: String) {
    val safeName = title.replace(Regex("[^A-Za-z0-9._ -]"), "")
        .trim().take(70).ifBlank { "CineVozz-film" } + ".mp4"
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(title)
        .setDescription("CineVozz offline movie download")
        .setMimeType("video/mp4")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(false)
        .setDestinationInExternalPublicDir(
            Environment.DIRECTORY_MOVIES, "CineVozz/$safeName"
        )
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}

@Composable
private fun PlayerDialog(title: String, url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White) },
        text = {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Purple) }
        },
        containerColor = Panel
    )
}
