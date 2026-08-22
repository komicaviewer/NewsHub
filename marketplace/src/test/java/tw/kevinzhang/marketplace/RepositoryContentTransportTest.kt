package tw.kevinzhang.marketplace

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Headers.Companion.headersOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryContentTransportTest {
    @Test
    fun `GitHub transport maps virtual path and sends only required auth headers`() = runBlocking {
        val token = "github_pat_secret_value"
        val client = clientResponding { chain ->
            val request = chain.request()
            assertEquals(
                "https://api.github.com/repos/acme/private-repo/contents/metadata/root.json?ref=release%2Fstable",
                request.url.toString(),
            )
            assertEquals("Bearer $token", request.header("Authorization"))
            assertEquals("application/vnd.github.raw+json", request.header("Accept"))
            assertEquals("2026-03-10", request.header("X-GitHub-Api-Version"))
            response(request, 200, ROOT_BYTES)
        }
        val transport = githubTransport(client, RepositoryAccessCredential.githubToken(token))

        assertArrayEquals(
            ROOT_BYTES,
            transport.fetchRequired(
                "https://github.com/acme/private-repo/metadata/root.json".toHttpUrl(),
                1024,
            ),
        )
    }

    @Test
    fun `public HTTPS transport preserves URL and never sends authorization`() = runBlocking {
        val client = clientResponding { chain ->
            val request = chain.request()
            assertEquals("https://repo.example.test/dist/metadata/root.json", request.url.toString())
            assertEquals(null, request.header("Authorization"))
            response(request, 200, ROOT_BYTES)
        }
        val transport = RepositoryContentTransport(
            client,
            "https://repo.example.test/dist",
            RepositoryAccessDescriptor.publicHttps(),
            domainId = "domain-id",
            credentialProvider = { RepositoryAccessCredential.githubToken("must-not-be-used") },
        )

        assertArrayEquals(
            ROOT_BYTES,
            transport.fetchRequired(
                "https://repo.example.test/dist/metadata/root.json".toHttpUrl(),
                1024,
            ),
        )
    }

    @Test
    fun `GitHub transport maps root rotation online metadata and APK paths`() = runBlocking {
        val expectedPaths = listOf(
            "metadata/2.root.json",
            "metadata/timestamp.json",
            "metadata/3.snapshot.json",
            "metadata/4.targets.json",
            "targets/apk/org.example.extension.apk",
        )
        var requestIndex = 0
        val client = clientResponding { chain ->
            val expectedPath = expectedPaths[requestIndex++]
            assertEquals(
                "https://api.github.com/repos/acme/private-repo/contents/$expectedPath?ref=release%2Fstable",
                chain.request().url.toString(),
            )
            response(chain.request(), 200, expectedPath.toByteArray())
        }
        val transport = githubTransport(client, RepositoryAccessCredential.githubToken("token"))

        expectedPaths.forEach { path ->
            assertArrayEquals(
                path.toByteArray(),
                transport.fetchRequired(
                    "https://github.com/acme/private-repo/$path".toHttpUrl(),
                    1024,
                ),
            )
        }
        assertEquals(expectedPaths.size, requestIndex)
    }

    @Test
    fun `missing rejected and inaccessible credentials use typed redacted failures`() {
        val secret = "github_pat_never_disclose"
        val noNetwork = OkHttpClient.Builder().addInterceptor {
            throw AssertionError("Missing credentials must fail before network access")
        }.build()
        val missing = assertThrows(RepositoryAccessRequiredException::class.java) {
            runBlocking {
                githubTransport(noNetwork, null).fetchRequired(VIRTUAL_ROOT, 1024)
            }
        }
        assertEquals(RepositoryAccessFailureReason.MISSING_CREDENTIAL, missing.reason)

        listOf(
            401 to RepositoryAccessFailureReason.CREDENTIAL_REJECTED,
            403 to RepositoryAccessFailureReason.CREDENTIAL_REJECTED,
            404 to RepositoryAccessFailureReason.NOT_FOUND_OR_INACCESSIBLE,
        ).forEach { (code, reason) ->
            val client = clientResponding { chain -> response(chain.request(), code) }
            val error = assertThrows(RepositoryAccessRequiredException::class.java) {
                runBlocking {
                    githubTransport(client, RepositoryAccessCredential.githubToken(secret))
                        .fetchRequired(VIRTUAL_ROOT, 1024)
                }
            }
            assertEquals(reason, error.reason)
            assertFalse(error.toString().contains(secret))
            assertFalse(error.toString().contains("api.github.com"))
        }
    }

    @Test
    fun `optional GitHub root 404 remains absence while required 404 is typed`() = runBlocking {
        val client = clientResponding { chain -> response(chain.request(), 404) }
        val transport = githubTransport(client, RepositoryAccessCredential.githubToken("token"))

        assertEquals(null, transport.fetchOptional(VIRTUAL_ROOT, 1024))
        val error = assertThrows(RepositoryAccessRequiredException::class.java) {
            runBlocking { transport.fetchRequired(VIRTUAL_ROOT, 1024) }
        }
        assertEquals(RepositoryAccessFailureReason.NOT_FOUND_OR_INACCESSIBLE, error.reason)
    }

    @Test
    fun `similar prefix and target path escape are rejected before network`() {
        assertFalse(
            isWithinRepositoryBase(
                "https://github.com/acme/repo/".toHttpUrl(),
                "https://github.com/acme/repository/metadata/root.json".toHttpUrl(),
            ),
        )
        val noNetwork = OkHttpClient.Builder().addInterceptor {
            throw AssertionError("Escaped path must fail before network access")
        }.build()
        val transport = githubTransport(noNetwork, RepositoryAccessCredential.githubToken("token"))
        assertThrows(TrustedMetadataException::class.java) {
            runBlocking {
                transport.fetchRequired(
                    "https://github.com/acme/private-repository/metadata/root.json".toHttpUrl(),
                    1024,
                )
            }
        }
    }

    @Test
    fun `redirects and oversized bodies fail closed`() {
        val redirectClient = clientResponding { chain ->
            response(
                chain.request(),
                302,
                headers = headersOf("Location", "https://objects.example.test/secret"),
            )
        }
        assertThrows(TrustedMetadataException::class.java) {
            runBlocking {
                githubTransport(redirectClient, RepositoryAccessCredential.githubToken("token"))
                    .fetchRequired(VIRTUAL_ROOT, 1024)
            }
        }

        val oversizedClient = clientResponding { chain ->
            response(chain.request(), 200, ByteArray(5))
        }
        assertThrows(TrustedMetadataException::class.java) {
            runBlocking {
                githubTransport(oversizedClient, RepositoryAccessCredential.githubToken("token"))
                    .fetchRequired(VIRTUAL_ROOT, 4)
            }
        }
    }

    @Test
    fun `credential string representation is always redacted`() {
        val secret = "github_pat_secret_value"
        val credential = RepositoryAccessCredential.githubToken(secret)
        assertEquals(secret, credential.withSecret { it })
        assertFalse(credential.toString().contains(secret))
        assertTrue(credential.toString().contains("REDACTED"))
    }

    private fun githubTransport(
        client: OkHttpClient,
        credential: RepositoryAccessCredential?,
    ) = RepositoryContentTransport(
        client,
        "https://github.com/acme/private-repo",
        RepositoryAccessDescriptor.githubContents("release/stable"),
        domainId = "domain-id",
        credentialProvider = { credential },
    )

    private fun clientResponding(
        block: (Interceptor.Chain) -> Response,
    ) = OkHttpClient.Builder().addInterceptor(block).build()

    private fun response(
        request: okhttp3.Request,
        code: Int,
        bytes: ByteArray = ByteArray(0),
        headers: okhttp3.Headers = headersOf(),
    ) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .headers(headers)
        .body(bytes.toResponseBody("application/octet-stream".toMediaType()))
        .build()

    private companion object {
        val ROOT_BYTES = "root".toByteArray()
        val VIRTUAL_ROOT = "https://github.com/acme/private-repo/metadata/root.json".toHttpUrl()
    }
}
