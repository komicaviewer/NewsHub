package tw.kevinzhang.newshub.ui.thread

import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.newshub.replyTo

internal data class ThreadedPost(
    val post: Post,
    /** The complete tree depth, retained for labels and accessibility. */
    val actualDepth: Int,
    /** The depth used by the layout. Keeping it capped prevents a narrow unreadable column. */
    val visualDepth: Int,
    val parentId: String?,
)

/**
 * Converts a chronological post list into a stable depth-first reply tree.
 *
 * A post may quote more than one target. The first target that exists in the current thread is
 * treated as its parent. Cycles and missing targets become roots so malformed extension data never
 * hides a post. Visual indentation is capped while the depth-first ordering is preserved.
 */
internal fun List<Post>.asThreadedPosts(maxDepth: Int = 3): List<ThreadedPost> {
    if (isEmpty()) return emptyList()
    val visualDepthLimit = maxDepth.coerceIn(0, 3)

    val postsById = associateBy(Post::id)
    val parentByPostId = associate { post ->
        post.id to post.replyTo().firstOrNull { targetId ->
            targetId != post.id && targetId in postsById
        }
    }
    val childrenByParent = buildMap<String, MutableList<Post>> {
        this@asThreadedPosts.forEach { post ->
            parentByPostId[post.id]?.let { parentId ->
                getOrPut(parentId) { mutableListOf() }.add(post)
            }
        }
    }
    val result = mutableListOf<ThreadedPost>()
    val visited = mutableSetOf<String>()

    fun append(post: Post, depth: Int) {
        if (!visited.add(post.id)) return
        result += ThreadedPost(
            post = post,
            actualDepth = depth,
            visualDepth = depth.coerceAtMost(visualDepthLimit),
            parentId = parentByPostId[post.id],
        )
        childrenByParent[post.id].orEmpty().forEach { child -> append(child, depth + 1) }
    }

    filter { parentByPostId[it.id] == null }.forEach { root -> append(root, 0) }
    // Cycles have no root. Append any remaining posts in source order instead of dropping them.
    forEach { post -> append(post, 0) }
    return result
}

internal fun visibleFraction(
    itemOffset: Int,
    itemSize: Int,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
): Float {
    if (itemSize <= 0) return 0f
    val visibleStart = maxOf(itemOffset, viewportStartOffset)
    val visibleEnd = minOf(itemOffset + itemSize, viewportEndOffset)
    val viewportSize = (viewportEndOffset - viewportStartOffset).coerceAtLeast(1)
    // A media-heavy post may be taller than the viewport and can never be 50% visible relative to
    // its full height. In that case, measure against the viewport so dwelling on its visible area
    // still counts as reading it.
    val visibilityTargetSize = minOf(itemSize, viewportSize)
    return ((visibleEnd - visibleStart).coerceAtLeast(0)).toFloat() / visibilityTargetSize
}
