package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GutendexResponse(
    val count: Int = 0,
    val results: List<GutendexBook> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GutendexBook(
    val id: Int,
    val title: String,
    val authors: List<GutendexAuthor> = emptyList(),
    val subjects: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    @Json(name = "download_count") val downloadCount: Int = 0,
    val formats: Map<String, String> = emptyMap()
) {
    val authorDisplay: String
        get() = authors.firstOrNull()?.name
            ?.split(",")
            ?.reversed()
            ?.joinToString(" ") { it.trim() }
            ?.ifBlank { "Autor desconocido" }
            ?: "Autor desconocido"

    val epubDownloadUrl: String?
        get() = formats["application/epub+zip"]
            ?: formats.entries.firstOrNull { it.key.contains("epub", ignoreCase = true) }?.value

    val coverImageUrl: String?
        get() = formats["image/jpeg"]
}

@JsonClass(generateAdapter = true)
data class GutendexAuthor(
    val name: String = ""
)

@JsonClass(generateAdapter = true)
data class SupabaseCatalogItem(
    val id: Long,
    val slug: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    @Json(name = "item_type") val itemType: String,
    @Json(name = "file_format") val fileFormat: String,
    @Json(name = "storage_path") val storagePath: String,
    @Json(name = "cover_path") val coverPath: String? = null,
    @Json(name = "language_code") val languageCode: String? = null,
    val subjects: List<String> = emptyList(),
    @Json(name = "file_size_bytes") val fileSizeBytes: Long = 0L,
    @Json(name = "download_count") val downloadCount: Long = 0L,
    @Json(name = "game_version") val gameVersion: String = "1.0.0",
    @Json(name = "update_manifest_path") val updateManifestPath: String? = null,
    @Json(name = "package_entry") val packageEntry: String = "index.html",
    @Json(name = "sha256") val sha256: String? = null,
    @Json(name = "release_notes") val releaseNotes: String = "",
    @Json(name = "minimum_app_version") val minimumAppVersion: String = "1.0.0"
)

private interface SupabaseCatalogApi {
    @GET("rest/v1/luminabook_catalog_items")
    suspend fun fetchPublishedItems(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String = "id,slug,title,author,description,item_type,file_format,storage_path,cover_path,language_code,subjects,file_size_bytes,download_count,game_version,update_manifest_path,package_entry,sha256,release_notes,minimum_app_version",
        @Query("is_published") published: String = "eq.true",
        @Query("item_type") itemType: String,
        @Query("file_format") fileFormat: String? = null,
        @Query("title") title: String? = null,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50
    ): List<SupabaseCatalogItem>
}

class OnlineCatalogService {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/') + "/"
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .followRedirects(true)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val api: SupabaseCatalogApi by lazy {
        requireConfiguration()
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseCatalogApi::class.java)
    }

    suspend fun fetchBooks(
        query: String?,
        languageCode: String?,
        topic: String?
    ): List<GutendexBook> = withContext(Dispatchers.IO) {
        requireConfiguration()
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val items = api.fetchPublishedItems(
            apiKey = apiKey,
            authorization = "Bearer $anonKey",
            itemType = "eq.book",
            fileFormat = "eq.epub",
            title = catalogTitleFilter(normalizedQuery)
        )
        items.asSequence()
            .filter { languageCode.isNullOrBlank() || it.languageCode.equals(languageCode, ignoreCase = true) }
            .filter { topic.isNullOrBlank() || it.subjects.any { subject -> subject.contains(topic, ignoreCase = true) } }
            .map { item ->
                val fileUrl = publicStorageUrl(item.storagePath)
                val formats = buildMap {
                    put("application/epub+zip", fileUrl)
                    item.coverPath?.takeIf { it.isNotBlank() }?.let {
                        put("image/jpeg", publicStorageUrl(it))
                    }
                }
                GutendexBook(
                    id = item.id.toInt(),
                    title = item.title,
                    authors = listOf(GutendexAuthor(item.author)),
                    subjects = item.subjects,
                    languages = listOfNotNull(item.languageCode),
                    downloadCount = item.downloadCount.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                    formats = formats
                )
            }
            .toList()
    }


    suspend fun fetchGames(query: String? = null): List<SupabaseCatalogItem> = withContext(Dispatchers.IO) {
        requireConfiguration()
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val items = api.fetchPublishedItems(
            apiKey = apiKey,
            authorization = "Bearer $anonKey",
            itemType = "eq.game",
            title = catalogTitleFilter(normalizedQuery)
        )
        val resolvedItems = mutableListOf<SupabaseCatalogItem>()
        for (item in items) {
            var resolved = item.copy(
                coverPath = item.coverPath?.takeIf { it.isNotBlank() }?.let { publicStorageUrl(it) }
            )
            val manifestPath = item.updateManifestPath?.takeIf { it.isNotBlank() }
            if (manifestPath != null) {
                resolved = runCatching {
                    val manifestBytes = downloadCatalogAsset(manifestPath)
                    val manifest = JSONObject(manifestBytes.toString(Charsets.UTF_8))
                    val remoteVersion = manifest.optString("version").takeIf { it.isNotBlank() } ?: item.gameVersion
                    val remoteFile = manifest.optString("file").takeIf { it.isNotBlank() } ?: item.storagePath
                    item.copy(
                        coverPath = resolved.coverPath,
                        gameVersion = remoteVersion,
                        storagePath = remoteFile,
                        releaseNotes = manifest.optString("notes", item.releaseNotes),
                        sha256 = manifest.optString("sha256").takeIf { it.isNotBlank() } ?: item.sha256,
                        packageEntry = manifest.optString("package_entry", item.packageEntry)
                    )
                }.getOrDefault(resolved)
            }
            resolvedItems += resolved
        }
        resolvedItems
    }

    suspend fun downloadCatalogAsset(path: String): ByteArray = downloadBytes(publicStorageUrl(path))

    suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        requireAllowedDownloadUrl(url)
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Error HTTP ${response.code}")
            requireAllowedDownloadUrl(response.request.url.toString())
            val body = response.body ?: throw IllegalStateException("Respuesta vacía")
            val maxBytes = 64L * 1024L * 1024L
            if (body.contentLength() == 0L) throw IllegalStateException("El archivo descargado está vacío")
            if (body.contentLength() > maxBytes) throw IllegalStateException("El archivo supera el límite de descarga de 64 MiB")
            val initialCapacity = body.contentLength().takeIf { it > 0L }?.coerceAtMost(262144L)?.toInt() ?: 32768
            val output = ByteArrayOutputStream(initialCapacity)
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw IllegalStateException("El archivo supera el límite de descarga de 64 MiB")
                    output.write(buffer, 0, read)
                }
            }
            if (output.size() == 0) throw IllegalStateException("El archivo descargado está vacío")
            output.toByteArray()
        }
    }

    private fun catalogTitleFilter(query: String?): String? {
        val safe = query?.filter { it.isLetterOrDigit() || it.isWhitespace() || it == '-' || it == 39.toChar() }
            ?.trim()?.take(80)?.takeIf { it.isNotBlank() } ?: return null
        return "ilike.*$safe*"
    }

    private fun requireAllowedDownloadUrl(rawUrl: String) {
        val uri = Uri.parse(rawUrl)
        val allowedHosts = setOf(Uri.parse(baseUrl).host?.lowercase(), "www.gutenberg.org", "aleph.gutenberg.org", "gutenberg.org", "raw.githubusercontent.com")
        require(uri.scheme.equals("https", true) && uri.userInfo == null && uri.port == -1 &&
            uri.host?.lowercase() in allowedHosts) { "La dirección de descarga no está permitida" }
    }

    private fun publicStorageUrl(path: String): String {
        val candidate = path.trim()
        if (candidate.startsWith("https://", ignoreCase = true)) {
            val uri = Uri.parse(candidate)
            val host = uri.host?.lowercase()
            require(uri.scheme.equals("https", ignoreCase = true) &&
                uri.userInfo == null && uri.port == -1 &&
                host in setOf("www.gutenberg.org", "raw.githubusercontent.com")) {
                "La fuente externa del catálogo no está permitida"
            }
            return candidate
        }

        val encodedPath = candidate.split("/")
            .filter { it.isNotBlank() }
            .joinToString("/") { Uri.encode(it) }
        require(encodedPath.isNotBlank() && !candidate.startsWith("/") && !candidate.split("/").any { it == ".." }) {
            "Ruta de almacenamiento no válida"
        }
        return "${baseUrl}storage/v1/object/public/luminabook-assets/$encodedPath"
    }

    private fun requireConfiguration() {
        require(BuildConfig.SUPABASE_URL.isNotBlank() && apiKey.isNotBlank() && anonKey.isNotBlank()) {
            "Falta configurar SUPABASE_URL y SUPABASE_PUBLISHABLE_KEY en .env"
        }
    }
}

class NetworkMonitor(private val context: Context) {
    fun isCurrentlyOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    val isOnlineFlow: Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        trySend(isCurrentlyOnline())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isCurrentlyOnline())
            }

            override fun onLost(network: Network) {
                trySend(isCurrentlyOnline())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)

        awaitClose {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()
}
