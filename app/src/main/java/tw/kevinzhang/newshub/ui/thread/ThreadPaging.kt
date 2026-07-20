package tw.kevinzhang.newshub.ui.thread

import tw.kevinzhang.extension_api.model.Post

/**
 * UI-independent state for opaque thread-page tokens. A token returned by a source is
 * remembered immediately, so a source that returns it again cannot make the UI loop.
 */
internal data class ThreadPagingState(
    val nextPageToken: String? = null,
    val seenPageTokens: Set<String> = emptySet(),
    val hasMore: Boolean = false,
    val isAppending: Boolean = false,
    val appendError: String? = null,
) {
    val canAppend: Boolean get() = hasMore && !isAppending && nextPageToken != null
}

/** Returns null for blank, self-referential, or previously returned tokens. */
internal fun validatedNextPageToken(
    returnedToken: String?,
    requestedToken: String? = null,
    seenTokens: Set<String> = emptySet(),
): String? {
    val token = returnedToken?.takeUnless(String::isBlank) ?: return null
    return token.takeUnless { it == requestedToken || it in seenTokens }
}

internal fun ThreadPagingState.forInitialPage(nextPageToken: String?): ThreadPagingState {
    val next = validatedNextPageToken(nextPageToken)
    return ThreadPagingState(
        nextPageToken = next,
        seenPageTokens = next?.let(::setOf).orEmpty(),
        hasMore = next != null,
    )
}

internal fun ThreadPagingState.startAppend(): ThreadPagingState? {
    if (!canAppend) return null
    return copy(isAppending = true, appendError = null)
}

internal fun ThreadPagingState.appendSucceeded(
    requestedToken: String,
    returnedToken: String?,
): ThreadPagingState {
    val next = validatedNextPageToken(returnedToken, requestedToken, seenPageTokens)
    return copy(
        nextPageToken = next,
        seenPageTokens = seenPageTokens + requestedToken + listOfNotNull(next),
        hasMore = next != null,
        isAppending = false,
        appendError = null,
    )
}

internal fun ThreadPagingState.appendFailed(message: String): ThreadPagingState =
    copy(isAppending = false, appendError = message)

/** Keeps the first version of a post, while preserving page order for newly seen IDs. */
internal fun mergePostsById(existing: List<Post>, incoming: List<Post>): List<Post> {
    val seenIds = existing.mapTo(mutableSetOf(), Post::id)
    return existing + incoming.filter { seenIds.add(it.id) }
}
