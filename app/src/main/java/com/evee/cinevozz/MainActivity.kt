package com.evee.cinevozz

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.evee.cinevozz.data.ArchiveMovie
import com.evee.cinevozz.data.ArchiveRepository
import kotlinx.coroutines.launch

private val Black = Color(0xFF080808)
private val SurfaceDark = Color(0xFF151515)
private val SurfaceSoft = Color(0xFF202020)
private val Red = Color(0xFFE50914)
private val TextMuted = Color(0xFFAAAAAA)

class MainActivity : ComponentActivity() {
    private val repository = ArchiveRepository()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Red, secondary = Color(0xFFFF3944),
                    background = Black, surface = SurfaceDark,
                    onBackground = Color.White, onSurface = Color.White
                )
            ) {
                CineVozzApp(repository) { task -> lifecycleScope.launch { task() } }
            }
        }
    }
}

private enum class AppTab(val label: String) {
    HOME("Home"), SEARCH("Search"), LIBRARY("Library"), DOWNLOADS("Downloads")
}

@Composable
private fun CineVozzApp(repository: ArchiveRepository, launch: (suspend () -> Unit) -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var query by remember { mutableStateOf("") }
    var movies by remember { mutableStateOf<List<ArchiveMovie>>(emptyList()) }
    var favorites by remember { mutableStateOf(loadFavorites(context)) }
    var downloads by remember { mutableStateOf(readDownloads(context)) }
    var selected by remember { mutableStateOf<ArchiveMovie?>(null) }
    var playing by remember { mutableStateOf<Pair<ArchiveMovie, String>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf("Discover films you can legally watch for free.") }
    var activeCategory by remember { mutableStateOf("For you") }

    fun runSearch(term: String) {
        query = term
        loading = true
        launch {
            try {
                movies = repository.search(term)
                notice = if (movies.isEmpty()) "No licensed movies found. Try another search." else "${movies.size} movies available"
            } catch (e: Exception) {
                notice = e.message ?: "Unable to load movies. Check your connection."
            } finally { loading = false }
        }
    }

    LaunchedEffect(Unit) { runSearch("") }

    fun toggleFavorite(movie: ArchiveMovie) {
        favorites = if (favorites.contains(movie.identifier)) favorites - movie.identifier
        else favorites + movie.identifier
        saveFavorites(context, favorites)
    }

    fun playMovie(movie: ArchiveMovie) {
        busyId = movie.identifier
        launch {
            try { playing = movie to repository.findPlayableFile(movie) }
            catch (e: Exception) { notice = e.message ?: "Playback unavailable." }
            finally { busyId = null }
        }
    }

    fun downloadMovie(movie: ArchiveMovie) {
        busyId = movie.identifier
        launch {
            try {
                val url = repository.findPlayableFile(movie)
                enqueueDownload(context, url, movie.title)
                notice = "Download started: ${movie.title}"
                downloads = readDownloads(context)
            } catch (e: Exception) { notice = e.message ?: "Download unavailable." }
            finally { busyId = null }
        }
    }

    Column(Modifier.fillMaxSize().background(Black).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("CINE", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("VOZZ", color = Red, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp)
            }
            Surface(color = SurfaceDark, shape = CircleShape) {
                Text("FREE CINEMA", Modifier.padding(horizontal = 13.dp, vertical = 8.dp), color = Red, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                AppTab.HOME -> HomeContent(
                    movies, activeCategory, { category ->
                        activeCategory = category
                        runSearch(if (category == "For you" || category == "Trending") "" else category)
                    }, favorites, ::toggleFavorite, ::playMovie, { selected = it }, loading, notice
                )
                AppTab.SEARCH -> SearchContent(
                    query, { query = it }, { runSearch(query) }, movies, loading, notice,
                    favorites, ::toggleFavorite, { selected = it }
                )
                AppTab.LIBRARY -> LibraryContent(
                    movies.filter { favorites.contains(it.identifier) },
                    favorites, ::toggleFavorite, { selected = it }
                )
                AppTab.DOWNLOADS -> DownloadsContent(downloads, { downloads = readDownloads(context) })
            }
        }

        NavigationBar(containerColor = Color(0xFF101010), contentColor = Color.White) {
            AppTab.values().forEach { item ->
                val icon = when (item) {
                    AppTab.HOME -> Icons.Default.Home
                    AppTab.SEARCH -> Icons.Default.Search
                    AppTab.LIBRARY -> Icons.Default.Bookmark
                    AppTab.DOWNLOADS -> Icons.Default.Download
                }
                NavigationBarItem(
                    selected = tab == item,
                    onClick = {
                        tab = item
                        if (item == AppTab.DOWNLOADS) downloads = readDownloads(context)
                    },
                    icon = { Icon(icon, contentDescription = item.label) },
                    label = { Text(item.label, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Red, selectedTextColor = Red,
                        indicatorColor = Color(0xFF321014), unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
            }
        }
    }

    selected?.let { movie ->
        MovieDetailsDialog(
            movie = movie,
            favorite = favorites.contains(movie.identifier),
            busy = busyId == movie.identifier,
            onFavorite = { toggleFavorite(movie) },
            onPlay = { selected = null; playMovie(movie) },
            onDownload = { selected = null; downloadMovie(movie) },
            onDismiss = { selected = null }
        )
    }
    playing?.let { (movie, url) ->
        PlayerDialog(movie.title, url) { playing = null }
    }
}

@Composable
private fun HomeContent(
    movies: List<ArchiveMovie>, category: String, onCategory: (String) -> Unit,
    favorites: Set<String>, onFavorite: (ArchiveMovie) -> Unit,
    onPlay: (ArchiveMovie) -> Unit, onSelect: (ArchiveMovie) -> Unit,
    loading: Boolean, notice: String
) {
    val categories = listOf("For you", "Trending", "Classic", "Documentary", "Animation", "Short films")
    LazyColumn(contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Text("STREAM SOMETHING GREAT", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Movies for every mood.", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { item ->
                        val active = category == item
                        Surface(
                            color = if (active) Red else SurfaceDark,
                            shape = RoundedCornerShape(30.dp),
                            modifier = Modifier.clickable { onCategory(item) }
                        ) {
                            Text(item, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (movies.isNotEmpty()) {
            item {
                val featured = movies.first()
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(220.dp).clip(RoundedCornerShape(22.dp)).clickable { onSelect(featured) }) {
                    AsyncImage("https://archive.org/services/img/${featured.identifier}", null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                        Text("FEATURED FILM", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text(featured.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(featured.year.ifBlank { "Classic cinema" }, color = TextMuted, fontSize = 11.sp)
                            Spacer(Modifier.width(12.dp))
                            Button(onClick = { onPlay(featured) }, contentPadding = PaddingValues(horizontal = 15.dp, vertical = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(17.dp)); Text(" Watch", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            Column {
                SectionHeading("Popular on CineVozz", "OPEN FILM PICKS")
                MoviePosterRow(movies.take(12), favorites, onFavorite, onSelect)
            }
        }
        item {
            Column {
                SectionHeading("Explore the collection", notice)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 18.dp), color = Red)
                else if (movies.isEmpty()) Text(notice, Modifier.padding(horizontal = 18.dp), color = TextMuted, fontSize = 12.sp)
                else MoviePosterRow(movies.drop(12).take(12), favorites, onFavorite, onSelect)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun MoviePosterRow(
    movies: List<ArchiveMovie>, favorites: Set<String>,
    onFavorite: (ArchiveMovie) -> Unit, onSelect: (ArchiveMovie) -> Unit
) {
    if (movies.isEmpty()) {
        Text("More titles will appear when the catalogue loads.", Modifier.padding(horizontal = 18.dp), color = TextMuted, fontSize = 12.sp)
        return
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(movies, key = { it.identifier }) { movie ->
            Column(Modifier.width(132.dp).clickable { onSelect(movie) }) {
                Box(Modifier.fillMaxWidth().height(184.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceDark)) {
                    AsyncImage("https://archive.org/services/img/${movie.identifier}", null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Surface(Modifier.align(Alignment.TopEnd).padding(6.dp).clickable { onFavorite(movie) }, color = Color(0xCC111111), shape = CircleShape) {
                        Icon(if (favorites.contains(movie.identifier)) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, Modifier.padding(7.dp).size(16.dp), tint = if (favorites.contains(movie.identifier)) Red else Color.White)
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(movie.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(movie.year.ifBlank { "Open cinema" }, color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SearchContent(
    query: String, onQuery: (String) -> Unit, onSearch: () -> Unit,
    movies: List<ArchiveMovie>, loading: Boolean, notice: String,
    favorites: Set<String>, onFavorite: (ArchiveMovie) -> Unit, onSelect: (ArchiveMovie) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text("Find your movie", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text("Search titles in the open-film catalogue.", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.clip(RoundedCornerShape(14.dp)).background(SurfaceDark).padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Red)
            Spacer(Modifier.width(8.dp))
            BasicTextField(query, onQuery, Modifier.weight(1f).padding(vertical = 13.dp), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp), decorationBox = { inner ->
                if (query.isEmpty()) Text("Search movie name...", color = TextMuted, fontSize = 14.sp)
                inner()
            })
            Text("GO", Modifier.clickable { onSearch() }.padding(8.dp), color = Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(if (loading) "Searching..." else notice, color = TextMuted, fontSize = 11.sp)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red)
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(movies, key = { it.identifier }) { movie ->
                SearchMovieRow(movie, favorites.contains(movie.identifier), { onFavorite(movie) }, { onSelect(movie) })
            }
        }
    }
}

@Composable
private fun SearchMovieRow(movie: ArchiveMovie, favorite: Boolean, onFavorite: () -> Unit, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceDark).clickable { onSelect() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage("https://archive.org/services/img/${movie.identifier}", null, Modifier.width(84.dp).height(112.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(movie.year.ifBlank { "Film" }, color = Red, fontSize = 11.sp)
            Text(movie.description.take(90), color = TextMuted, fontSize = 10.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Red else TextMuted) }
    }
}

@Composable
private fun LibraryContent(movies: List<ArchiveMovie>, favorites: Set<String>, onFavorite: (ArchiveMovie) -> Unit, onSelect: (ArchiveMovie) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text("My Library", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text("${favorites.size} saved titles", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        if (movies.isEmpty()) {
            Text("Your watchlist is empty. Tap the heart on a movie to save it here.", color = TextMuted, fontSize = 13.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(movies, key = { it.identifier }) { movie ->
                    SearchMovieRow(movie, true, { onFavorite(movie) }, { onSelect(movie) })
                }
            }
        }
    }
}

@Composable
private fun DownloadsContent(downloads: List<String>, refresh: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Downloads", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("Your offline movie transfers", color = TextMuted, fontSize = 12.sp)
            }
            Text("REFRESH", Modifier.clip(RoundedCornerShape(20.dp)).background(SurfaceDark).clickable { refresh() }.padding(12.dp), color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        if (downloads.isEmpty()) Text("No downloads yet. Open a movie and choose Download to save it for offline viewing.", color = TextMuted, fontSize = 13.sp)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(downloads) { item ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceDark).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, null, tint = Red)
                    Text(item, Modifier.padding(start = 12.dp), color = Color.White, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Files are saved to Movies/CineVozz on your device.", color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun MovieDetailsDialog(
    movie: ArchiveMovie, favorite: Boolean, busy: Boolean,
    onFavorite: () -> Unit, onPlay: () -> Unit, onDownload: () -> Unit, onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.92f).clip(RoundedCornerShape(22.dp)).background(SurfaceDark).padding(16.dp)) {
            AsyncImage("https://archive.org/services/img/${movie.identifier}", null, Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(15.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(14.dp))
            Text(movie.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(movie.year.ifBlank { "Openly licensed film" }, color = Red, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(movie.description.ifBlank { "A film available through the Internet Archive." }.take(500), color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text("RIGHTS: ${movie.licenseUrl.ifBlank { movie.rights.ifBlank { "See source record" } }}", color = Color(0xFFFF8A8A), fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay, enabled = !busy, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.PlayArrow, null)
                    Text(" Play")
                }
                OutlinedButton(onClick = onDownload, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Download, null); Text(" Save")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = Red); Text(if (favorite) " Saved to library" else " Add to library", color = Color.White) }
                TextButton(onClick = onDismiss) { Text("Close", color = TextMuted) }
            }
            Text("Source: Internet Archive • Verify rights on the original item page.", color = TextMuted, fontSize = 9.sp)
        }
    }
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
    DisposableEffect(player) { onDispose { player.release() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).background(SurfaceDark).padding(12.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxWidth().height(230.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close", color = Red) }
        }
    }
}

private fun loadFavorites(context: Context): Set<String> =
    context.getSharedPreferences("cinevozz", Context.MODE_PRIVATE).getStringSet("favorites", emptySet())?.toSet() ?: emptySet()

private fun saveFavorites(context: Context, favorites: Set<String>) {
    context.getSharedPreferences("cinevozz", Context.MODE_PRIVATE).edit().putStringSet("favorites", favorites).apply()
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
                val title = if (titleCol >= 0) c.getString(titleCol) else "Movie"
                val status = if (statusCol >= 0) c.getInt(statusCol) else -1
                val done = if (bytesCol >= 0) c.getLong(bytesCol) else 0L
                val total = if (totalCol >= 0) c.getLong(totalCol) else -1L
                val label = when (status) {
                    DownloadManager.STATUS_PENDING -> "Queued"
                    DownloadManager.STATUS_RUNNING -> "Downloading"
                    DownloadManager.STATUS_PAUSED -> "Paused"
                    DownloadManager.STATUS_SUCCESSFUL -> "Complete"
                    DownloadManager.STATUS_FAILED -> "Failed"
                    else -> "Unknown"
                }
                val progress = if (total > 0) " • ${done * 100 / total}%" else ""
                add("$title — $label$progress")
            }
        }
    }
}

private fun enqueueDownload(context: Context, url: String, title: String) {
    val safeName = title.replace(Regex("[^A-Za-z0-9._ -]"), "").trim().take(70).ifBlank { "CineVozz-film" } + ".mp4"
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(title).setDescription("CineVozz offline movie download").setMimeType("video/mp4")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true).setAllowedOverRoaming(false)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "CineVozz/$safeName")
    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
}
