package eu.kanade.tachiyomi.network

import okhttp3.OkHttpClient

expect class NetworkHelper {
    val client: OkHttpClient
    val cloudflareClient: OkHttpClient

    fun defaultUserAgentProvider(): String
}
