# Rich Post WebView Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When `PostParser` produces an empty `content` list (rich posts with `<font>`-wrapped images), show a "使用WebView引擎渲染" button; tapping it replaces the PostCard content area with an inline WebView that renders the raw HTML.

**Architecture:** `PostParser` always extracts the raw innerHTML of `div.c-article__content` into `GPost.rawHtml`. This propagates through `Post.rawHtml` (extension-api) to the UI. `ThreadDetailViewModel` tracks which post IDs have WebView enabled in a `Set<String>` StateFlow; `ExtPostCard` renders a button or a WebView accordingly.

**Tech Stack:** Kotlin, Jsoup, Jetpack Compose, Android WebView (`AndroidView`), Hilt, Kotlin Coroutines / StateFlow

---

## File Map

| File | Action |
|------|--------|
| `gamer-api/src/main/java/tw/kevinzhang/gamer_api/model/GPost.kt` | Add `rawHtml: String` to `GPost` data class and `GPostBuilder` |
| `gamer-api/src/main/java/tw/kevinzhang/gamer_api/parser/PostParser.kt` | Add `setRawHtml()` private method, call it from `parse()` |
| `gamer-api/src/test/kotlin/tw/kevinzhang/gamer_api/parser/PostParserTest.kt` | Add test: `parse()` produces non-blank `rawHtml` |
| `extension-api/src/main/java/tw/kevinzhang/extension_api/model/Post.kt` | Add `rawHtml: String? = null` |
| `extensions-builtin/src/main/java/tw/kevinzhang/extensions_builtin/gamer/GamerSource.kt` | Map `rawHtml = gPost.rawHtml` in `fetchThread()` |
| `app/src/main/java/tw/kevinzhang/newshub/ui/thread/ThreadDetailViewModel.kt` | Add `_useWebViewPosts` StateFlow + `enableWebViewForPost()` |
| `app/src/main/java/tw/kevinzhang/newshub/ui/thread/ThreadDetailScreen.kt` | Collect state; add `useWebView`/`onEnableWebView` to `ExtPostCard`; add WebView rendering |

---

## Task 1: Add `rawHtml` to `GPost` and `GPostBuilder`

**Files:**
- Modify: `gamer-api/src/main/java/tw/kevinzhang/gamer_api/model/GPost.kt`

- [ ] **Step 1: Add `rawHtml` to `GPost` data class**

Open `gamer-api/src/main/java/tw/kevinzhang/gamer_api/model/GPost.kt`. The `GPost` data class currently ends with `val content: List<GParagraph>`. Add `rawHtml` as the last parameter:

```kotlin
data class GPost (
    val id: String,
    val url: String,
    val title: String,
    val createdAt: Long,
    val posterName: String,
    val posterId: String,
    val like: Int,
    val unlike: Int,
    var replies: Int,
    val comments: Int,
    val commentsUrl: String,
    val readAt: Int,
    val page: Int,
    val content: List<GParagraph>,
    val rawHtml: String,
)
```

- [ ] **Step 2: Add `rawHtml` to `GPostBuilder`**

In the same file, inside `GPostBuilder`:

Add the backing field after `private var content`:
```kotlin
private var rawHtml: String = ""
```

Add the setter method after `fun setPostId()`:
```kotlin
fun setRawHtml(html: String): GPostBuilder {
    this.rawHtml = html
    return this
}
```

Update `build()` to pass `rawHtml`:
```kotlin
fun build() =
    GPost(
        id,
        url,
        title,
        createdAt,
        posterName,
        posterId,
        like,
        unlike,
        replies,
        comments,
        commentsUrl,
        readAt,
        page,
        content,
        rawHtml,
    )
```

- [ ] **Step 3: Verify the build compiles**

Run:
```bash
./gradlew :gamer-api:compileKotlin
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add gamer-api/src/main/java/tw/kevinzhang/gamer_api/model/GPost.kt
git commit -m "feat(gamer-api): add rawHtml field to GPost and GPostBuilder"
```

---

## Task 2: Implement `setRawHtml()` in `PostParser` (TDD)

**Files:**
- Modify: `gamer-api/src/test/kotlin/tw/kevinzhang/gamer_api/parser/PostParserTest.kt`
- Modify: `gamer-api/src/main/java/tw/kevinzhang/gamer_api/parser/PostParser.kt`

- [ ] **Step 1: Write the failing test**

Open `gamer-api/src/test/kotlin/tw/kevinzhang/gamer_api/parser/PostParserTest.kt`. Add this test inside `PostParserTest`, after the existing `Test PostParser expect successful` test:

```kotlin
@Test
fun `Test PostParser rawHtml is not blank`() {
    val post = parser.parse(
        loadFile("./src/test/html/Post.html")!!.toResponseBody(),
        RequestBuilderImpl().setUrl("https://forum.gamer.com.tw/C.php?bsn=60076&snA=4166175&sn=46104650".toHttpUrl()).build(),
    )
    assertTrue(post.rawHtml.isNotBlank())
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
./gradlew :gamer-api:test --tests "tw.kevinzhang.gamer_api.parser.PostParserTest.Test PostParser rawHtml is not blank"
```
Expected: `BUILD SUCCESSFUL` but test FAILS (assertion error — `rawHtml` is blank because `setRawHtml()` is not called yet and defaults to `""`).

- [ ] **Step 3: Implement `setRawHtml()` in `PostParser`**

Open `gamer-api/src/main/java/tw/kevinzhang/gamer_api/parser/PostParser.kt`.

Add the private method after `setContent()`:
```kotlin
private fun setRawHtml(source: Element) {
    val html = source.selectFirst("div.c-article__content")?.html() ?: ""
    builder.setRawHtml(html)
}
```

Call it from `parse()`, after `setContent(source)`:
```kotlin
override fun parse(body: ResponseBody, req: Request): GPost {
    val source = Jsoup.parse(body.string())
    val postId = urlParser.parseSn(req.url)!!
    val bsn = urlParser.parseBsn(req.url)!!
    setTitle(source)
    setCreatedAt(source)
    setPosterName(source)
    setPosterId(source)
    setLike(source)
    setUnlike(source)
    setComments(source, postId)
    setCommentsUrl(bsn, postId)
    setContent(source)
    setRawHtml(source)
    builder.setUrl(req.url.toString())
    builder.setPostId(postId)
    builder.setPage(urlParser.parsePage(req.url))
    val post = builder.build()
    builder = GPostBuilder()
    return post
}
```

- [ ] **Step 4: Run all gamer-api tests to confirm they pass**

```bash
./gradlew :gamer-api:test
```
Expected: `BUILD SUCCESSFUL`, all tests pass including the new one.

- [ ] **Step 5: Commit**

```bash
git add gamer-api/src/main/java/tw/kevinzhang/gamer_api/parser/PostParser.kt
git add gamer-api/src/test/kotlin/tw/kevinzhang/gamer_api/parser/PostParserTest.kt
git commit -m "feat(gamer-api): extract rawHtml in PostParser"
```

---

## Task 3: Add `rawHtml` to `Post` (extension-api)

**Files:**
- Modify: `extension-api/src/main/java/tw/kevinzhang/extension_api/model/Post.kt`

- [ ] **Step 1: Add the field**

Open `extension-api/src/main/java/tw/kevinzhang/extension_api/model/Post.kt`. Add `rawHtml` as the last field with a default value so existing usages compile without changes:

```kotlin
data class Post(
    val id: String,
    val author: String?,
    val createdAt: Long?,
    val thumbnail: String?,
    val content: List<Paragraph>,
    val comments: List<Comment>,
    val rawHtml: String? = null,
)
```

- [ ] **Step 2: Verify the build compiles**

```bash
./gradlew :extension-api:compileKotlin
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add extension-api/src/main/java/tw/kevinzhang/extension_api/model/Post.kt
git commit -m "feat(extension-api): add rawHtml field to Post"
```

---

## Task 4: Map `rawHtml` in `GamerSource`

**Files:**
- Modify: `extensions-builtin/src/main/java/tw/kevinzhang/extensions_builtin/gamer/GamerSource.kt`

- [ ] **Step 1: Add `rawHtml` to the `Post(...)` constructor call in `fetchThread()`**

Open `extensions-builtin/src/main/java/tw/kevinzhang/extensions_builtin/gamer/GamerSource.kt`.

Find the `Post(` constructor call inside `fetchThread()` (around line 126). Add `rawHtml = gPost.rawHtml` as the last argument:

```kotlin
Post(
    id = gPost.id,
    author = gPost.posterName,
    createdAt = gPost.createdAt,
    thumbnail = gPost.content.filterIsInstance<GImageInfo>().firstOrNull()?.thumb,
    content = gPost.content.map { it.toExtParagraph() },
    comments = comments,
    rawHtml = gPost.rawHtml,
)
```

- [ ] **Step 2: Verify the build compiles**

```bash
./gradlew :extensions-builtin:compileKotlin
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add extensions-builtin/src/main/java/tw/kevinzhang/extensions_builtin/gamer/GamerSource.kt
git commit -m "feat(gamer): map rawHtml from GPost to Post in GamerSource"
```

---

## Task 5: Add WebView state to `ThreadDetailViewModel`

**Files:**
- Modify: `app/src/main/java/tw/kevinzhang/newshub/ui/thread/ThreadDetailViewModel.kt`

- [ ] **Step 1: Add `_useWebViewPosts` state and `enableWebViewForPost()`**

Open `app/src/main/java/tw/kevinzhang/newshub/ui/thread/ThreadDetailViewModel.kt`.

After the `_alwaysUseRawImage` declaration (around line 61), add:

```kotlin
private val _useWebViewPosts = MutableStateFlow<Set<String>>(emptySet())
val useWebViewPosts = _useWebViewPosts.asStateFlow()
```

After the `dismissPreview()` function at the end of the class, add:

```kotlin
fun enableWebViewForPost(postId: String) {
    _useWebViewPosts.update { it + postId }
}
```

- [ ] **Step 2: Verify the build compiles**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tw/kevinzhang/newshub/ui/thread/ThreadDetailViewModel.kt
git commit -m "feat(thread): add useWebViewPosts state to ThreadDetailViewModel"
```

---

## Task 6: Add WebView rendering to `ThreadDetailScreen`

**Files:**
- Modify: `app/src/main/java/tw/kevinzhang/newshub/ui/thread/ThreadDetailScreen.kt`

- [ ] **Step 1: Add new imports**

Open `app/src/main/java/tw/kevinzhang/newshub/ui/thread/ThreadDetailScreen.kt`.

Add these imports (alongside the existing imports):
```kotlin
import android.webkit.WebView
import androidx.compose.ui.viewinterop.AndroidView
```

- [ ] **Step 2: Update `ExtPostCard` signature**

Find the `private fun ExtPostCard(` declaration (line 187). Add two new parameters after `post`:

```kotlin
@Composable
private fun ExtPostCard(
    post: Post,
    useWebView: Boolean,
    onEnableWebView: () -> Unit,
    alwaysUseRawImage: Boolean,
    commentUiState: CommentUiState?,
    onReplyToClick: (String) -> Unit,
    onLoadMoreCommentsClick: () -> Unit,
)
```

- [ ] **Step 3: Replace the content rendering block in `ExtPostCard`**

Find the existing `post.content.forEach { paragraph ->` block (starting around line 204). Replace the entire content rendering section (from `var imageIndex = 0` through the closing `}` of the forEach) with:

```kotlin
when {
    useWebView && post.rawHtml != null -> {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(
                    "https://forum.gamer.com.tw",
                    post.rawHtml,
                    "text/html",
                    "UTF-8",
                    null,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp),
        )
    }
    post.content.isEmpty() && post.rawHtml != null -> {
        TextButton(
            onClick = onEnableWebView,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("使用WebView引擎渲染")
        }
    }
    else -> {
        var imageIndex = 0
        post.content.forEach { paragraph ->
            when (paragraph) {
                is Paragraph.Text -> Text(paragraph.content)
                is Paragraph.Quote -> Text(
                    "> ${paragraph.content}",
                    style = MaterialTheme.typography.bodySmall,
                )
                is Paragraph.ReplyTo -> TextButton(
                    onClick = { onReplyToClick(paragraph.id) },
                    contentPadding = PaddingValues(0.dp),
                ) { Text(">> ${paragraph.id}") }
                is Paragraph.Link -> Text(
                    paragraph.content,
                    color = MaterialTheme.colorScheme.primary,
                )
                is Paragraph.ImageInfo -> {
                    val index = imageIndex++
                    val url = if (alwaysUseRawImage) paragraph.raw else paragraph.thumb
                    url?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { galleryStartIndex = index },
                        )
                    }
                }
                is Paragraph.VideoInfo -> Text("[video: ${paragraph.url}]")
            }
        }
    }
}
```

- [ ] **Step 4: Collect `useWebViewPosts` in `ThreadDetailScreen` and pass to `ExtPostCard`**

In `ThreadDetailScreen`, after the `val alwaysUseRawImage by ...` line (around line 70), add:

```kotlin
val useWebViewPosts by viewModel.useWebViewPosts.collectAsStateWithLifecycle()
```

Find the `ExtPostCard(` call inside the `LazyColumn` (around line 143). Update it to pass the new parameters:

```kotlin
ExtPostCard(
    post = post,
    useWebView = post.id in useWebViewPosts,
    onEnableWebView = { viewModel.enableWebViewForPost(post.id) },
    alwaysUseRawImage = alwaysUseRawImage,
    commentUiState = commentStates[post.id],
    onReplyToClick = onReplyToClick,
    onLoadMoreCommentsClick = { viewModel.loadMoreComments(post.id) },
)
```

- [ ] **Step 5: Verify the full build compiles**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Run all gamer-api tests**

```bash
./gradlew :gamer-api:test
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/tw/kevinzhang/newshub/ui/thread/ThreadDetailScreen.kt
git commit -m "feat(thread): add inline WebView fallback for rich posts in ExtPostCard"
```
