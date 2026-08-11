package tw.kevinzhang.newshub.extension

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
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
import tw.kevinzhang.newshub.extension.health.ExtensionHealthOutputDirectory
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

        val stagingDirectory = requireNotNull(context.getExternalFilesDir(REPORT_DIRECTORY))
        val outputArgument = InstrumentationRegistry.getArguments().getString(OUTPUT_ROOT_ARGUMENT)
        val exportDirectory = ExtensionHealthOutputDirectory.resolve(outputArgument, stagingDirectory)
        ExtensionHealthOutputDirectory.requireNonOverlapping(stagingDirectory, exportDirectory)
        val screenshotDirectory = File(stagingDirectory, "screenshots").apply { mkdirs() }
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
        File(stagingDirectory, reportName).writeText(ExtensionHealthJson.encodeReport(report))
        if (stagingDirectory.canonicalFile != exportDirectory.canonicalFile) {
            exportArtifacts(instrumentation, stagingDirectory, exportDirectory, reportName)
        }

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

    private fun exportArtifacts(
        instrumentation: android.app.Instrumentation,
        stagingDirectory: File,
        exportDirectory: File,
        reportName: String,
    ) {
        check(ExtensionHealthOutputDirectory.isShellSafe(stagingDirectory))
        check(ExtensionHealthOutputDirectory.isShellSafe(exportDirectory))
        check(runShellCommand(instrumentation, "mkdir -p ${exportDirectory.path}").isBlank())
        check(
            runShellCommand(
                instrumentation,
                "cp -R ${stagingDirectory.path}/. ${exportDirectory.path}/",
            ).isBlank(),
        )
        val exportedReport = File(exportDirectory, reportName)
        val observedReport = runShellCommand(
            instrumentation,
            "ls -d ${exportedReport.path}",
        ).trim()
        check(observedReport == exportedReport.path) { "Unable to verify extension health artifact export" }
    }

    private fun runShellCommand(instrumentation: android.app.Instrumentation, command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(SHELL_OUTPUT_BUFFER_CHARS)
                var reachedEof = false
                while (output.length < MAX_SHELL_OUTPUT_CHARS) {
                    val remaining = minOf(buffer.size, MAX_SHELL_OUTPUT_CHARS - output.length)
                    val read = reader.read(buffer, 0, remaining)
                    if (read < 0) {
                        reachedEof = true
                        break
                    }
                    output.append(buffer, 0, read)
                }
                if (!reachedEof) {
                    check(reader.read() < 0) { "Extension health export command produced excessive output" }
                }
                output.toString()
            }
        }

    private companion object {
        const val TRUSTED_PROFILE_ASSET = "extension-health/profile-v1.json"
        const val REPORT_DIRECTORY = "extension-health"
        const val OUTPUT_ROOT_ARGUMENT = "extensionHealthOutputRoot"
        const val REPORT_NAME_ARGUMENT = "extensionHealthReportName"
        const val DEFAULT_REPORT_NAME = "health-report.json"
        const val MAX_SHELL_OUTPUT_CHARS = 16 * 1024
        const val SHELL_OUTPUT_BUFFER_CHARS = 1024
        val SAFE_REPORT_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}\\.json")
    }
}
