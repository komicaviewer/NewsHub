package tw.kevinzhang.extension_api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.util.Locale

/** Stable failure taxonomy shared by the Host and isolated extension processes. */
enum class SourceFailureCode {
    HOST_POLICY,
    AUTH_REQUIRED,
    AUTH_EXPIRED,
    RATE_LIMITED,
    SITE_UNAVAILABLE,
    PARSER_CONTRACT,
    EXTENSION_RUNTIME,
    TIMED_OUT,
    TRUST_INACTIVE,
    INVALID_REQUEST,
    PAYLOAD_TOO_LARGE,
    BACKPRESSURE,
}

/**
 * Bounded, non-secret evidence that may safely cross Binder and enter UI/health reports.
 * Paths, queries, headers, cookies, response bodies, and raw exception messages are excluded.
 */
data class SourceFailure(
    val code: SourceFailureCode,
    val operation: String? = null,
    val observedHost: String? = null,
    val allowedHosts: List<String> = emptyList(),
    val retryable: Boolean = defaultRetryable(code),
) {
    fun sanitized(): SourceFailure = copy(
        operation = operation.safeOperation(),
        observedHost = observedHost?.safeHost(),
        allowedHosts = allowedHosts.asSequence()
            .mapNotNull(String::safeHost)
            .distinct()
            .sorted()
            .take(MAX_HOST_EVIDENCE)
            .toList(),
    )
}

/** Exception whose message is deliberately derived only from the stable failure code. */
class SourceFailureException(
    val failure: SourceFailure,
) : IOException("Source operation failed: ${failure.code.name}")

object SourceFailures {
    fun fromThrowable(error: Throwable, operation: String? = null): SourceFailure {
        if (error is CancellationException && error !is TimeoutCancellationException) throw error
        if (error is SourceFailureException) return error.failure.sanitized()
        return SourceFailure(
            code = when (error) {
                is TimeoutCancellationException -> SourceFailureCode.TIMED_OUT
                is AuthenticationRequiredException -> SourceFailureCode.AUTH_REQUIRED
                is IOException -> SourceFailureCode.SITE_UNAVAILABLE
                is IllegalArgumentException -> SourceFailureCode.PARSER_CONTRACT
                else -> SourceFailureCode.EXTENSION_RUNTIME
            },
            operation = operation,
            retryable = error is TimeoutCancellationException ||
                error is IOException && error !is SourceFailureException,
        ).sanitized()
    }
}

object SourceFailureWire {
    fun encode(failure: SourceFailure): String = ExtensionWireJson.encode(failure.sanitized())

    fun decode(payload: String): SourceFailure = runCatching {
        ExtensionWireJson.decode<SourceFailure>(payload).sanitized()
    }.getOrElse {
        SourceFailure(SourceFailureCode.EXTENSION_RUNTIME)
    }
}

private const val MAX_HOST_EVIDENCE = 32

private fun String?.safeOperation(): String? = this
    ?.takeIf { it.length in 1..64 && it.all { character ->
        character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' ||
            character == '_' || character == '-' || character == '.'
    } }

private fun String.safeHost(): String? = lowercase(Locale.ROOT)
    .trimEnd('.')
    .takeIf { value ->
        value.length in 1..253 &&
            value.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '.' } &&
            value.split('.').all { label ->
                label.length in 1..63 && label.first() != '-' && label.last() != '-'
            }
    }

private fun defaultRetryable(code: SourceFailureCode): Boolean = when (code) {
    SourceFailureCode.RATE_LIMITED,
    SourceFailureCode.SITE_UNAVAILABLE,
    SourceFailureCode.TIMED_OUT,
    SourceFailureCode.BACKPRESSURE,
    -> true
    else -> false
}
