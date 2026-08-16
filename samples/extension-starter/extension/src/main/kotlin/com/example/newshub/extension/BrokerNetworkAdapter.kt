package com.example.newshub.extension

import tw.kevinzhang.extension_api.NetworkOperations
import tw.kevinzhang.extension_api.SourceNetwork
import tw.kevinzhang.extension_api.SourceNetworkRequest
import tw.kevinzhang.extension_api.SourceNetworkResponse

/** Keeps request construction testable while the Host owns sockets and credentials. */
class BrokerNetworkAdapter(private val network: SourceNetwork) {
    suspend fun get(url: String, credentialed: Boolean = false): SourceNetworkResponse {
        val request = buildGet(url, credentialed)
        return network.execute(request).also { response ->
            check(response.code in 200..299) { "HTTP ${response.code}" }
        }
    }

    fun buildGet(url: String, credentialed: Boolean = false) = SourceNetworkRequest(
        operation = NetworkOperations.SOURCE_READ,
        method = "GET",
        url = url,
        headers = if (credentialed) mapOf("Accept" to "application/json") else emptyMap(),
    )
}
