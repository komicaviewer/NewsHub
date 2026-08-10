package tw.kevinzhang.newshub.extension

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import tw.kevinzhang.newshub.extension.health.ExtensionHealthJson
import tw.kevinzhang.newshub.extension.health.ExtensionHealthRunner
import tw.kevinzhang.newshub.extension.health.HealthStatus
import java.io.File
import java.io.FileOutputStream

/**
 * Credential-aware live smoke entry point for private CI.
 *
 * Credentials are provisioned into Host-owned storage before this test starts. This test accepts
 * no credential arguments and emits no crawled content, exception text, cookies, or target URLs.
 */
@RunWith(AndroidJUnit4::class)
class ExtensionLiveHealthInstrumentedTest {
    @Test
    fun officialExtensionsSatisfyLiveStructuralContract() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profileJson = instrumentation.context.assets
            .open(TRUSTED_PROFILE_ASSET)
            .bufferedReader()
            .use { it.readText() }
        val profile = ExtensionHealthJson.decodeProfile(profileJson)
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            ExtensionManagementEntryPoint::class.java,
        )

        // The trust fixture is Host-owned and still validates package bytes and production signer
        // pins. Private CI separately binds the installed candidate to the reviewed commit SHA.
        entryPoint.trustProvider().clear()
        entryPoint.trustProvider().installVerifiedSnapshot(
            ExtensionIsolationE2ETest().snapshot(
                context = context,
                targetsVersion = 1,
                validPins = true,
                validContent = true,
            ),
        )
        entryPoint.manager().refreshAllExtensionsAndAwait()
        withTimeout(30_000) {
            while (entryPoint.loader().sourcesFlow.value.size != profile.sources.size) delay(50)
        }
        assertEquals(
            profile.sources.map { it.sourceId }.sorted(),
            entryPoint.loader().sourcesFlow.value.map { it.id }.sorted(),
        )

        val reportDirectory = requireNotNull(context.getExternalFilesDir(REPORT_DIRECTORY))
        val screenshotDirectory = File(reportDirectory, "screenshots").apply { mkdirs() }
        val report = ExtensionHealthRunner().run(
            profile = profile,
            sources = entryPoint.loader().sourcesFlow.value,
            captureEvidence = { sourceId ->
                captureScreenshot(
                    bitmap = instrumentation.uiAutomation.takeScreenshot(),
                    directory = screenshotDirectory,
                    sourceId = sourceId,
                )
            },
        )

        val reportName = InstrumentationRegistry.getArguments().getString(REPORT_NAME_ARGUMENT)
            ?.takeIf { it.matches(SAFE_REPORT_NAME) }
            ?: DEFAULT_REPORT_NAME
        File(reportDirectory, reportName).writeText(ExtensionHealthJson.encodeReport(report))

        assertEquals("Extension health report was written with failures", HealthStatus.PASS, report.status)
    }

    private fun captureScreenshot(bitmap: Bitmap?, directory: File, sourceId: String): String? {
        bitmap ?: return null
        val safeSourceId = sourceId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val output = File(directory, "$safeSourceId.png")
        return try {
            FileOutputStream(output).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            "screenshots/${output.name}"
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val TRUSTED_PROFILE_ASSET = "extension-health/profile-v1.json"
        const val REPORT_DIRECTORY = "extension-health"
        const val REPORT_NAME_ARGUMENT = "extensionHealthReportName"
        const val DEFAULT_REPORT_NAME = "report.json"
        val SAFE_REPORT_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}\\.json")
    }
}
