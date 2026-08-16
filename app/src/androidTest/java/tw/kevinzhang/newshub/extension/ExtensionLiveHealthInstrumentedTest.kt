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
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import tw.kevinzhang.newshub.extension.health.ExtensionHealthJson
import tw.kevinzhang.newshub.extension.health.ExtensionHealthOutputDirectory
import tw.kevinzhang.newshub.extension.health.ExtensionHealthProfile
import tw.kevinzhang.newshub.extension.health.ExtensionHealthProfileSelection
import tw.kevinzhang.newshub.extension.health.ExtensionHealthReport
import tw.kevinzhang.newshub.extension.health.ExtensionHealthRunner
import tw.kevinzhang.newshub.extension.health.HealthFailureClass
import tw.kevinzhang.newshub.extension.health.HealthStepResult
import tw.kevinzhang.newshub.extension.health.HostOwnedSessionSnapshot
import tw.kevinzhang.newshub.extension.health.HealthStatus
import tw.kevinzhang.newshub.extension.health.SourceHealthProfile
import tw.kevinzhang.newshub.extension.health.SourceHealthResult
import tw.kevinzhang.newshub.extension.health.failureFingerprint
import tw.kevinzhang.extension_api.SourceIdentity
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
        val selection = ExtensionHealthProfileSelection.selectionFor(
            InstrumentationRegistry.getArguments().getString(PROFILE_ARGUMENT),
        )
        val profileJson = instrumentation.context.assets
            .open(selection.assetPath)
            .bufferedReader()
            .use { it.readText() }
        val profile = selection.select(ExtensionHealthJson.decodeProfile(profileJson))
        val stagingDirectory = requireNotNull(context.getExternalFilesDir(REPORT_DIRECTORY))
        val outputArgument = InstrumentationRegistry.getArguments().getString(OUTPUT_ROOT_ARGUMENT)
        val exportDirectory = ExtensionHealthOutputDirectory.resolve(outputArgument, stagingDirectory)
        ExtensionHealthOutputDirectory.requireNonOverlapping(stagingDirectory, exportDirectory)
        val reportName = InstrumentationRegistry.getArguments().getString(REPORT_NAME_ARGUMENT)
            ?.takeIf { it.matches(SAFE_REPORT_NAME) }
            ?: DEFAULT_REPORT_NAME
        File(stagingDirectory, reportName).delete()
        val screenshotDirectory = File(stagingDirectory, "screenshots").apply { mkdirs() }
        val startedAt = System.currentTimeMillis()
        var bootstrapOperation = SESSION_CLEANUP_OPERATION
        val report = try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                ExtensionManagementEntryPoint::class.java,
            )

            // Candidate and public FTL profiles never receive or parse raw sessions. A trusted,
            // self-hosted full-profile run consumes the fixed external file and deletes it before
            // any extension package is admitted or bound.
            val authenticatedSessionSourceIds = if (
                selection.profileId == ExtensionHealthProfileSelection.FULL_PROFILE
            ) {
                val signerReader = ExtensionIsolationE2ETest()
                val expectedSigners = profile.sources.filter(SourceHealthProfile::requireAuthenticatedSession)
                    .associate { source ->
                        @Suppress("DEPRECATION")
                        val packageInfo = context.packageManager.getPackageInfo(
                            source.packageName,
                            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES,
                        )
                        source.packageName to signerReader.installedSignerSha256(packageInfo)
                    }
                readAndDeleteSessionSnapshot(instrumentation, expectedSigners)
                    ?.sessions.orEmpty().mapTo(linkedSetOf()) { session ->
                    entryPoint.sessionManager().importHostOwnedSession(
                        SourceIdentity(session.packageName, session.signerSha256, session.sourceId),
                        session.cookies,
                    )
                    session.sourceId
                }
            } else {
                deleteSessionSnapshotWithoutReading(instrumentation)
                emptySet()
            }

            // This Host-owned test fixture binds exact installed package bytes to the signer observed
            // on that installed package. External CI separately pins the reviewed commit and APK SHA;
            // this dynamic fixture is not a replacement for production TUF metadata.
            bootstrapOperation = TRUST_FIXTURE_OPERATION
            entryPoint.trustProvider().clear()
            entryPoint.trustProvider().installVerifiedSnapshot(
                ExtensionIsolationE2ETest().snapshot(
                    context = context,
                    targetsVersion = 1,
                    validPins = true,
                    validContent = true,
                    sourceIds = profile.sources.mapTo(linkedSetOf()) { it.sourceId },
                    pinInstalledSigner = true,
                ),
            )
            bootstrapOperation = EXTENSION_REFRESH_OPERATION
            entryPoint.manager().refreshAllExtensionsAndAwait()
            val expectedSourceIds = profile.sources.mapTo(linkedSetOf(), SourceHealthProfile::sourceId)
            withTimeoutOrNull(SOURCE_SETTLE_TIMEOUT_MS) {
                while (entryPoint.loader().sourcesFlow.value.mapTo(linkedSetOf()) { it.id } != expectedSourceIds) {
                    delay(SOURCE_SETTLE_POLL_MS)
                }
            }
            val sources = entryPoint.loader().sourcesFlow.value
            check(sources.mapTo(linkedSetOf()) { it.id }.all { it in expectedSourceIds }) {
                "Unexpected Source escaped the health trust snapshot"
            }
            bootstrapOperation = HEALTH_RUNNER_OPERATION
            ExtensionHealthRunner().run(
                profile = profile,
                sources = sources,
                authenticatedSessionSourceIds = authenticatedSessionSourceIds,
                captureEvidence = { sourceId ->
                    captureScreenshot(
                        bitmap = instrumentation.uiAutomation.takeScreenshot(),
                        directory = screenshotDirectory,
                        sourceId = sourceId,
                    )
                },
            )
        } catch (_: Exception) {
            sanitizedHarnessFailureReport(
                profile = profile,
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = System.currentTimeMillis(),
                operation = bootstrapOperation,
            )
        }

        File(stagingDirectory, reportName).writeText(ExtensionHealthJson.encodeReport(report))
        if (stagingDirectory.canonicalFile != exportDirectory.canonicalFile) {
            exportArtifacts(instrumentation, stagingDirectory, exportDirectory, reportName)
        }

        val acceptedStatuses = if (selection.allowAuthPending) {
            setOf(HealthStatus.PASS, HealthStatus.PARTIAL_AUTH_PENDING)
        } else {
            setOf(HealthStatus.PASS)
        }
        check(report.status in acceptedStatuses) {
            "Extension health report was written with structural failures"
        }
    }

    private fun sanitizedHarnessFailureReport(
        profile: ExtensionHealthProfile,
        startedAtEpochMs: Long,
        finishedAtEpochMs: Long,
        operation: String,
    ) = ExtensionHealthReport(
        profileId = profile.profileId,
        startedAtEpochMs = startedAtEpochMs,
        finishedAtEpochMs = finishedAtEpochMs,
        status = HealthStatus.FAIL,
        requestCount = 0,
        results = profile.sources.map { source ->
            SourceHealthResult(
                sourceId = source.sourceId,
                packageName = source.packageName,
                status = HealthStatus.FAIL,
                durationMs = 0,
                steps = listOf(
                    HealthStepResult(
                        operation = operation,
                        status = HealthStatus.FAIL,
                        durationMs = 0,
                        failureClass = HealthFailureClass.HOST_RUNTIME,
                        failureFingerprint = failureFingerprint(
                            sourceId = source.sourceId,
                            operation = operation,
                            failureClass = HealthFailureClass.HOST_RUNTIME,
                            packageName = source.packageName,
                        ),
                    ),
                ),
            )
        },
    )

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
        runShellCommand(instrumentation, command, MAX_SHELL_OUTPUT_CHARS)

    private fun runShellCommand(
        instrumentation: android.app.Instrumentation,
        command: String,
        maxOutputChars: Int,
    ): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(SHELL_OUTPUT_BUFFER_CHARS)
                var reachedEof = false
                while (output.length < maxOutputChars) {
                    val remaining = minOf(buffer.size, maxOutputChars - output.length)
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

    private fun readAndDeleteSessionSnapshot(
        instrumentation: android.app.Instrumentation,
        expectedSignerByPackage: Map<String, String>,
    ): HostOwnedSessionSnapshot? {
        val output = runShellCommand(
            instrumentation,
            "if test -f $SESSION_SNAPSHOT_PATH; then " +
                "printf '$SESSION_PRESENT_MARKER\\n'; " +
                "trap 'rm -f $SESSION_SNAPSHOT_PATH' EXIT; " +
                "cat $SESSION_SNAPSHOT_PATH; fi",
            HostOwnedSessionSnapshot.MAX_BYTES + SESSION_PRESENT_MARKER.length + 2,
        )
        if (output.isEmpty()) return null
        check(output.startsWith("$SESSION_PRESENT_MARKER\n")) { "Invalid Host session transport" }
        return HostOwnedSessionSnapshot.decode(
            output.substringAfter('\n'),
            expectedSignerByPackage = expectedSignerByPackage,
        )
    }

    private fun deleteSessionSnapshotWithoutReading(instrumentation: android.app.Instrumentation) {
        check(runShellCommand(instrumentation, "rm -f $SESSION_SNAPSHOT_PATH").isBlank()) {
            "Unable to clear forbidden Host session input"
        }
    }

    private companion object {
        const val REPORT_DIRECTORY = "extension-health"
        const val PROFILE_ARGUMENT = "extensionHealthProfile"
        const val OUTPUT_ROOT_ARGUMENT = "extensionHealthOutputRoot"
        const val REPORT_NAME_ARGUMENT = "extensionHealthReportName"
        const val DEFAULT_REPORT_NAME = "health-report.json"
        const val SESSION_CLEANUP_OPERATION = "harness_session_cleanup"
        const val TRUST_FIXTURE_OPERATION = "harness_trust_fixture"
        const val EXTENSION_REFRESH_OPERATION = "harness_extension_refresh"
        const val HEALTH_RUNNER_OPERATION = "harness_health_runner"
        const val SOURCE_SETTLE_TIMEOUT_MS = 2_000L
        const val SOURCE_SETTLE_POLL_MS = 25L
        const val MAX_SHELL_OUTPUT_CHARS = 16 * 1024
        const val SHELL_OUTPUT_BUFFER_CHARS = 1024
        const val SESSION_SNAPSHOT_PATH = "/sdcard/Download/newshub-private/session-snapshot.json"
        const val SESSION_PRESENT_MARKER = "NEWSHUB_SESSION_V1"
        val SAFE_REPORT_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}\\.json")
    }
}
