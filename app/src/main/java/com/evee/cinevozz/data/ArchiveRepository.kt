package com.evee.cinevozz.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class ArchiveRepository {
    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String): List<ArchiveMovie> = withContext(Dispatchers.IO) {
        val clean = query.replace('"'.toString(), "").trim()
        val q = if (clean.isBlank()) "mediatype:movies" else
            "mediatype:movies AND title:\\"$clean\\""
        val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.toString())
        val url = "https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier&fl[]=title&fl[]=year&fl[]=description&fl[]=licenseurl&fl[]=rights&output=json&rows=60&sort[]=downloads+desc"
        val request = Request.Builder().url(url)
            .header("User-Agent", "CineVozz/1.0 Android app").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Catalogue request failed (${response.code})" }
            val docs = JSONObject(response.body?.string().orEmpty())
                .getJSONObject("response").getJSONArray("docs")
            buildList {
                for (i in 0 until docs.length()) {
                    val d = docs.getJSONObject(i)
                    val item = ArchiveMovie(
                        identifier = d.optString("identifier"),
                        title = d.optString("title", "Untitled film"),
                        year = d.optString("year"),
                        description = d.optString("description"),
                        licenseUrl = d.optString("licenseurl"),
                        rights = d.optString("rights")
                    )
                    if (item.identifier.isNotBlank() && item.isDownloadAllowed) add(item)
                }
            }
        }
    }

    suspend fun findPlayableFile(movie: ArchiveMovie): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://archive.org/metadata/${movie.identifier}")
            .header("User-Agent", "CineVozz/1.0").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Could not read film files (${response.code})" }
            val root = JSONObject(response.body?.string().orEmpty())
            val meta = root.optJSONObject("metadata") ?: JSONObject()
            val license = meta.optString("licenseurl", movie.licenseUrl)
            val rights = meta.optString("rights", movie.rights)
            require(ArchiveMovie.isApprovedLicense(license, rights)) {
                "No supported open licence was found for this item."
            }
            val files = root.optJSONArray("files") ?: error("No files are listed.")
            var selected: String? = null
            for (i in 0 until files.length()) {
                val f = files.optJSONObject(i) ?: continue
                val name = f.optString("name")
                val format = f.optString("format").lowercase()
                if (name.endsWith(".mp4", true) &&
                    !name.startsWith("__") &&
                    f.optString("source") != "derivative") {
                    selected = name
                    if (format.contains("mpeg4")) break
                }
            }
            val file = selected ?: error("No MP4 file is available for this film.")
            val encodedName = URLEncoder.encode(file, "UTF-8").replace("+", "%20")
            "https://archive.org/download/${movie.identifier}/$encodedName"
        }
    }
}
