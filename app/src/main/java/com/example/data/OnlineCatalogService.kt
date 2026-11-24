package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
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
            ?.ifBlank { "Dominio público" }
            ?: "Dominio público"

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

interface GutendexApi {
    @GET("books/")
    suspend fun searchBooks(
        @Query("search") search: String? = null,
        @Query("languages") languages: String? = null,
        @Query("topic") topic: String? = null,
        @Query("mime_type") mimeType: String = "application/epub"
    ): GutendexResponse
}

class OnlineCatalogService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val api: GutendexApi = Retrofit.Builder()
        .baseUrl("https://gutendex.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GutendexApi::class.java)

    suspend fun fetchBooks(
        query: String?,
        languageCode: String?,
        topic: String?
    ): List<GutendexBook> = withContext(Dispatchers.IO) {
        val response = api.searchBooks(
            search = query?.takeIf { it.isNotBlank() },
            languages = languageCode?.takeIf { it.isNotBlank() },
            topic = topic?.takeIf { it.isNotBlank() }
        )
        response.results.filter { it.epubDownloadUrl != null }
    }

    suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Error HTTP ${resp.code}")
            resp.body?.bytes() ?: throw IllegalStateException("Respuesta vacía")
        }
    }
}

class NetworkMonitor(private val context: Context) {
    fun isCurrentlyOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isCurrentlyOnline())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
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
