package com.example.newshub.extension

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kevinzhang.extension_api.SourceNetwork
import tw.kevinzhang.extension_api.SourceNetworkRequest
import tw.kevinzhang.extension_api.SourceNetworkResponse

class BrokerNetworkAdapterTest {
    @Test
    fun sendsOnlyTheDeclaredBrokerRequest() = runTest {
        var captured: SourceNetworkRequest? = null
        val network = SourceNetwork { request ->
            captured = request
            SourceNetworkResponse(200, body = "{}".toByteArray())
        }

        BrokerNetworkAdapter(network).get("https://api.example.com/v1/boards")

        assertEquals("source_read", captured?.operation)
        assertEquals("GET", captured?.method)
        assertEquals("https://api.example.com/v1/boards", captured?.url)
        assertEquals(null, captured?.body)
    }
}
