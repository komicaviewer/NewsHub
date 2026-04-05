# Rich Post WebView Fallback Design

**Date:** 2026-04-05
**Branch:** v2

## Problem

`PostParser` uses a `flatDiv()` approach that only flattens `<div>` wrapper elements. Rich posts on gamer.com.tw (e.g. 心得 posts) use `<font>` tags extensively to wrap images and text:

- **ThreadPage.html** (simple): `div > a.photoswipe-image` → `flatDiv()` surfaces the image correctly
- **RichThreadPage.html** (rich): `div > font > a.photoswipe-image` → `flatDiv()` stops at `font`, image is lost

Result: `post.content` is an empty list for rich posts. The UI shows nothing.

## Solution: Hybrid — structured parse with user-triggered WebView fallback

When structured parsing produces an empty content list, the UI shows a button. The user taps it, and the PostCard's content area is replaced by a WebView rendering the raw HTML.

`rawHtml` is always extracted and stored so the fallback is available without a second network request.

## Architecture

### Data flow

```
PostParser
  → GPost.rawHtml (always populated, innerHTML of div.c-article__content)
  → GPost.content (List<GParagraph>, may be empty for rich posts)

GamerSource.fetchThread()
  → Post.rawHtml (mapped from GPost.rawHtml)
  → Post.content (mapped from GPost.content)

ThreadDetailViewModel
  → useWebViewPosts: StateFlow<Set<String>>

ThreadDetailScreen / ExtPostCard
  → renders content or WebView based on state
```

### Data model changes

**`GPost` (gamer-api):**
```kotlin
data class GPost(
    // existing fields unchanged
    val rawHtml: String,  // always populated
)
```

**`Post` (extension-api):**
```kotlin
data class Post(
    // existing fields unchanged
    val rawHtml: String? = null,  // nullable; other Sources may not provide this
)
```

### Parser changes (`PostParser`)

Add `setRawHtml()` called from `parse()`, independent of `setContent()`:

```kotlin
private fun setRawHtml(source: Element) {
    val html = source.selectFirst("div.c-article__content")?.html() ?: ""
    builder.setRawHtml(html)
}
```

`setContent()` is unchanged. Both run every time.

### Mapping (`GamerSource`)

```kotlin
Post(
    // existing fields unchanged
    rawHtml = gPost.rawHtml,
)
```

### ViewModel (`ThreadDetailViewModel`)

```kotlin
private val _useWebViewPosts = MutableStateFlow<Set<String>>(emptySet())
val useWebViewPosts = _useWebViewPosts.asStateFlow()

fun enableWebViewForPost(postId: String) {
    _useWebViewPosts.update { it + postId }
}
```

State is ephemeral (in-memory only). Once a user enables WebView for a post it stays enabled for the lifetime of the ViewModel, but resets on re-entry. This is intentional — no persistence needed.

### UI (`ThreadDetailScreen` / `ExtPostCard`)

**`ThreadDetailScreen`** collects `useWebViewPosts` and passes per-post values down:

```kotlin
val useWebViewPosts by viewModel.useWebViewPosts.collectAsStateWithLifecycle()

// inside LazyColumn items:
ExtPostCard(
    post = post,
    useWebView = post.id in useWebViewPosts,
    onEnableWebView = { viewModel.enableWebViewForPost(post.id) },
    // existing params unchanged
)
```

**`ExtPostCard`** content area logic:

```
when {
    useWebView && post.rawHtml != null →
        AndroidView(::WebView) with loadDataWithBaseURL(
            baseUrl = "https://forum.gamer.com.tw",
            data = post.rawHtml,
            mimeType = "text/html",
            encoding = "UTF-8",
            historyUrl = null,
        )

    post.content.isEmpty() && post.rawHtml != null →
        TextButton("使用WebView引擎渲染", onClick = onEnableWebView)

    else →
        existing paragraph forEach rendering (unchanged)
}
```

The `baseUrl` ensures relative image paths and same-origin resources load correctly.

## Scope

- No changes to `flatDiv()` or existing structured parsing logic
- No persistence of `useWebViewPosts` across sessions
- No new screen or navigation change — replacement is inline within PostCard
- Only `GamerSource` maps `rawHtml`; other Sources are unaffected (field defaults to `null`)

## Files to modify

| File | Change |
|------|--------|
| `gamer-api/.../model/GPost.kt` | Add `rawHtml: String` field |
| `gamer-api/.../model/GPostBuilder.kt` | Add `setRawHtml()` |
| `gamer-api/.../parser/PostParser.kt` | Add `setRawHtml()` call in `parse()` |
| `extension-api/.../model/Post.kt` | Add `rawHtml: String? = null` |
| `extensions-builtin/.../GamerSource.kt` | Map `rawHtml = gPost.rawHtml` |
| `app/.../ThreadDetailViewModel.kt` | Add `useWebViewPosts` state and `enableWebViewForPost()` |
| `app/.../ThreadDetailScreen.kt` | Collect state, pass to `ExtPostCard`, add WebView rendering |
