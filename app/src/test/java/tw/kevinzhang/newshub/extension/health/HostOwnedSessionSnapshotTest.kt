package tw.kevinzhang.newshub.extension.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HostOwnedSessionSnapshotTest {
    private val now = 1_800_000_000_000L

    @Test
    fun strictSnapshotBindsSourcePackageSignerHostTtlUaAndCookieBoundary() {
        val snapshot = decode(validSnapshot())

        assertEquals(1, snapshot.sessions.size)
        val session = snapshot.sessions.single()
        assertEquals("tw.kevinzhang.newshub.extension.gamer", session.sourceId)
        assertEquals("tw.kevinzhang.newshub.extension.gamer", session.packageName)
        assertEquals("forum.gamer.com.tw", session.cookies.single().domain)
        assertTrue(session.cookies.single().secure)
        assertTrue(session.cookies.single().expiresAt <= now + 60_000)
    }

    @Test
    fun domainWideningUnknownFieldsExpiredAndOversizedSnapshotsFailClosed() {
        assertInvalid(validSnapshot().replace("forum.gamer.com.tw", "com"))
        assertInvalid(validSnapshot().replace("\"sessions\":", "\"unexpected\":true,\"sessions\":"))
        assertInvalid(validSnapshot().replace((now + 60_000).toString(), (now - 1).toString()))
        assertThrows(IllegalArgumentException::class.java) {
            decode("x".repeat(HostOwnedSessionSnapshot.MAX_BYTES + 1))
        }
    }

    @Test
    fun rawCookieValueNeverAppearsInObjectOrValidationError() {
        val secret = "cookie-canary-super-secret"
        val snapshot = decode(validSnapshot(secret))
        assertFalse(snapshot.toString().contains(secret))
        assertFalse(snapshot.sessions.single().toString().contains(secret))

        val error = assertThrows(IllegalArgumentException::class.java) {
            decode(validSnapshot(secret).replace("\"secure\": true", "\"secure\": false"))
        }
        assertFalse(error.message.orEmpty().contains(secret))
    }

    @Test
    fun eynySnapshotAcceptsOnlyReviewedRotatingOrigins() {
        val snapshot = decode(eynySnapshot("www52.eyny.com"))

        assertEquals("www52.eyny.com", snapshot.sessions.single().cookies.single().domain)
        assertInvalid(eynySnapshot("www54.eyny.com"))
    }

    private fun assertInvalid(raw: String) {
        assertThrows(IllegalArgumentException::class.java) { decode(raw) }
    }

    private fun decode(raw: String) = HostOwnedSessionSnapshot.decode(
        raw,
        nowEpochMs = now,
        expectedSignerByPackage = mapOf(
            "tw.kevinzhang.newshub.extension.gamer" to
                "bdf003b9cd64a049d4f4e3ebba52ebe804bbd7dab559d82e69fdb659c4c10ad0",
            "tw.kevinzhang.newshub.extension.eyny" to
                "adf003b9cd64a049d4f4e3ebba52ebe804bbd7dab559d82e69fdb659c4c10ad1",
        ),
    )

    private fun eynySnapshot(host: String): String = """
        {
          "schemaVersion": 1,
          "sessions": [{
            "sourceId": "tw.kevinzhang.eyny",
            "packageName": "tw.kevinzhang.newshub.extension.eyny",
            "signerSha256": "adf003b9cd64a049d4f4e3ebba52ebe804bbd7dab559d82e69fdb659c4c10ad1",
            "profileId": "official-live-v1",
            "issuedAtEpochMs": $now,
            "expiresAtEpochMs": ${now + 60_000},
            "userAgentProfileId": "eyny-android14-chrome120-v1",
            "cookies": [{
              "origin": "https://$host",
              "name": "session",
              "value": "session-value",
              "domain": "$host",
              "path": "/",
              "secure": true,
              "httpOnly": true,
              "hostOnly": true,
              "expiresAtEpochMs": ${now + 120_000}
            }]
          }]
        }
    """.trimIndent()

    private fun validSnapshot(value: String = "session-value"): String = """
        {
          "schemaVersion": 1,
          "sessions": [{
            "sourceId": "tw.kevinzhang.newshub.extension.gamer",
            "packageName": "tw.kevinzhang.newshub.extension.gamer",
            "signerSha256": "bdf003b9cd64a049d4f4e3ebba52ebe804bbd7dab559d82e69fdb659c4c10ad0",
            "profileId": "official-live-v1",
            "issuedAtEpochMs": $now,
            "expiresAtEpochMs": ${now + 60_000},
            "userAgentProfileId": "host-default-v1",
            "cookies": [{
              "origin": "https://forum.gamer.com.tw",
              "name": "session",
              "value": "$value",
              "domain": "forum.gamer.com.tw",
              "path": "/",
              "secure": true,
              "httpOnly": true,
              "hostOnly": true,
              "expiresAtEpochMs": ${now + 120_000}
            }]
          }]
        }
    """.trimIndent()
}
