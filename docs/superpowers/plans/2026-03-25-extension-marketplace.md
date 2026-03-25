# NewsHub Extension Marketplace — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign NewsHub from a hardcoded forum reader into an extensible platform with a `Source` interface, APK-based Extensions, user-defined Collections, and a GitHub marketplace.

**Architecture:** Five new/refactored Gradle modules (`extension-api`, `extensions-builtin`, `extension-loader`, `collection`, `marketplace`) built sequentially. Built-in sources (Gamer, Sora, _2cat) wrap existing `gamer-api`/`komica-api` parsers behind the `Source` interface; installed APK sources are loaded via `PathClassLoader`. The app gains a three-tab bottom nav (Collections · Extensions · Settings).

**Tech Stack:** Kotlin 1.7, Jetpack Compose 1.2, Room 2.4, Hilt 2.42, Paging 3, OkHttp 4, Coroutines 1.6, JUnit 4

**Spec:** `docs/superpowers/specs/2026-03-25-extension-marketplace-design.md`

---

## Phase Overview

| Phase | Modules touched | Ends with |
|-------|----------------|-----------|
| 1 | `:extension-api` (new) | `Source` interface + models, unit-tested |
| 2 | `:extensions-builtin` (new) | GamerSource + SoraSource + _2catSource, app still builds |
| 3 | `:extension-loader` + `:collection` (new) | Hilt-wired loader + Room DB |
| 4 | `:app` | 3-tab nav, Collection timeline, Thread screen |
| 5 | `:marketplace` (new) | Index fetch + APK install UI |

Each phase ends with `./gradlew assembleDebug` passing.

---

## Phase 1 — `:extension-api`

Pure Kotlin JVM library. No Android deps. Contains `Source` interface + all shared data models. `Paragraph` is copied here standalone (no imports from `gamer-api`/`komica-api`).

**Files:**
- Create: `extension-api/build.gradle`
- Create: `extension-api/src/main/java/tw/kevinzhang/extension_api/Source.kt`
- Create: `extension-api/src/main/java/tw/kevinzhang/extension_api/model/Board.kt`
- Create: `extension-api/src/main/java/tw/kevinzhang/extension_api/model/ThreadSummary.kt`
- Create: `extension-api/src/main/java/tw/kevinzhang/extension_api/model/Thread.kt`
- Create: `extension-api/src/main/java/tw/kevinzhang/extension_api/model/Post.kt`
- Create: `extension-api/src/main/java/tw/kevinzhang/extension_api/model/Comment.kt`
- Create: `extension-api/src/main/java/tw/kevinzhang/extension_api/model/Paragraph.kt`
- Test: `extension-api/src/test/java/tw/kevinzhang/extension_api/SourceContractTest.kt`
- Modify: `settings.gradle` (add `include ':extension-api'`)

### Task 1.1 — Create module and `Paragraph`

- [ ] Add `include ':extension-api'` to `settings.gradle`

- [ ] Create `extension-api/build.gradle`:

```groovy
plugins {
    id 'org.jetbrains.kotlin.jvm' version '1.7.0'
}

dependencies {
    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] Create `extension-api/src/main/java/tw/kevinzhang/extension_api/model/Paragraph.kt`:

```kotlin
package tw.kevinzhang.extension_api.model

sealed class Paragraph {
    class ImageInfo(val thumb: String? = null, val raw: String) : Paragraph()
    class VideoInfo(val url: String) : Paragraph()
    class Text(val content: String) : Paragraph()
    class Quote(val content: String) : Paragraph()
    class ReplyTo(val id: String) : Paragraph()
    class Link(val content: String) : Paragraph()
}

fun List<Paragraph>.rawImages() =
    filterIsInstance<Paragraph.ImageInfo>().map { it.raw }
```

- [ ] Write failing test — `extension-api/src/test/java/tw/kevinzhang/extension_api/ParagraphTest.kt`:

```kotlin
class ParagraphTest {
    @Test fun `rawImages returns only image paragraphs`() {
        val paragraphs = listOf(
            Paragraph.Text("hello"),
            Paragraph.ImageInfo(thumb = "t.jpg", raw = "r.jpg"),
            Paragraph.Quote("q"),
            Paragraph.ImageInfo(raw = "r2.jpg"),
        )
        assertEquals(listOf("r.jpg", "r2.jpg"), paragraphs.rawImages())
    }
}
```

- [ ] Run test: `./gradlew :extension-api:test`
  Expected: PASS

- [ ] Commit:
```bash
git add extension-api/ settings.gradle
git commit -m "feat(extension-api): create module with Paragraph model"
```

### Task 1.2 — Data models

- [ ] Create `Board.kt`:

```kotlin
package tw.kevinzhang.extension_api.model

data class Board(
    val sourceId: String,
    val url: String,
    val name: String,
    val description: String? = null,
)
```

- [ ] Create `ThreadSummary.kt`:

```kotlin
package tw.kevinzhang.extension_api.model

data class ThreadSummary(
    val sourceId: String,
    val boardUrl: String,
    val id: String,
    val title: String?,
    val author: String?,
    val createdAt: Long?,
    val replyCount: Int?,
    val thumbnail: String?,
    val previewContent: List<Paragraph>,
)
```

- [ ] Create `Post.kt`:

```kotlin
package tw.kevinzhang.extension_api.model

data class Post(
    val id: String,
    val author: String?,
    val createdAt: Long?,
    val thumbnail: String?,
    val content: List<Paragraph>,
    val comments: List<Comment>,
)
```

- [ ] Create `Comment.kt`:

```kotlin
package tw.kevinzhang.extension_api.model

data class Comment(
    val id: String,
    val author: String?,
    val createdAt: Long?,
    val content: List<Paragraph>,
)
```

- [ ] Create `Thread.kt`:

```kotlin
package tw.kevinzhang.extension_api.model

data class Thread(
    val id: String,
    val title: String?,
    val posts: List<Post>,  // posts[0] is the OP
)
```

- [ ] Run build: `./gradlew :extension-api:assemble`
  Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add extension-api/
git commit -m "feat(extension-api): add Board, ThreadSummary, Thread, Post, Comment models"
```

### Task 1.3 — `Source` interface

- [ ] Create `extension-api/src/main/java/tw/kevinzhang/extension_api/Source.kt`:

```kotlin
package tw.kevinzhang.extension_api

import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

interface Source {
    val id: String
    val name: String
    val language: String
    val version: Int
    val iconUrl: String?

    suspend fun getBoards(): List<Board>
    suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary>
    suspend fun getThread(summary: ThreadSummary): Thread
}
```

- [ ] Write a contract test — `SourceContractTest.kt`:

```kotlin
class SourceContractTest {
    @Test fun `Source id must not be blank`() {
        val source = object : Source {
            override val id = "tw.test.source"
            override val name = "Test"
            override val language = "zh-TW"
            override val version = 1
            override val iconUrl = null
            override suspend fun getBoards() = emptyList<Board>()
            override suspend fun getThreadSummaries(board: Board, page: Int) = emptyList<ThreadSummary>()
            override suspend fun getThread(summary: ThreadSummary) = Thread("", null, emptyList())
        }
        assertTrue(source.id.isNotBlank())
    }
}
```

- [ ] Run: `./gradlew :extension-api:test`
  Expected: PASS

- [ ] Commit:
```bash
git add extension-api/
git commit -m "feat(extension-api): add Source interface"
```

---

## Phase 2 — `:extensions-builtin`

New Android library module. Depends on `:extension-api`, `:gamer-api`, `:komica-api`. Contains `GamerSource`, `SoraSource`, `_2catSource`. The existing `hub-server` is untouched — app still builds normally after this phase.

`Paragraph` mapping extensions (previously in `hub-server/data/Paragraph.kt`) move here.

**Files:**
- Create: `extensions-builtin/build.gradle`
- Create: `extensions-builtin/src/main/AndroidManifest.xml`
- Create: `extensions-builtin/src/main/java/tw/kevinzhang/extensions_builtin/ParagraphMapper.kt`
- Create: `extensions-builtin/src/main/java/tw/kevinzhang/extensions_builtin/gamer/GamerSource.kt`
- Create: `extensions-builtin/src/main/java/tw/kevinzhang/extensions_builtin/sora/SoraSource.kt`
- Create: `extensions-builtin/src/main/java/tw/kevinzhang/extensions_builtin/_2cat/_2catSource.kt`
- Test: `extensions-builtin/src/test/java/tw/kevinzhang/extensions_builtin/ParagraphMapperTest.kt`
- Modify: `settings.gradle`

### Task 2.1 — Create module scaffold

- [ ] Add `include ':extensions-builtin'` to `settings.gradle`

- [ ] Create `extensions-builtin/build.gradle`:

```groovy
plugins {
    id 'com.android.library'
    id 'org.jetbrains.kotlin.android'
    id 'kotlin-kapt'
    id 'dagger.hilt.android.plugin'
}

android {
    compileSdk 33
    defaultConfig { minSdk 21 }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = '1.8' }
}

dependencies {
    implementation project(':extension-api')
    implementation project(':gamer-api')
    implementation project(':komica-api')
    implementation "com.google.dagger:hilt-android:$versions.hilt"
    kapt "com.google.dagger:hilt-android-compiler:$versions.hilt"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:$versions.coroutines"
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4'
}
```

- [ ] Create `extensions-builtin/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="tw.kevinzhang.extensions_builtin" />
```

- [ ] Run: `./gradlew assembleDebug`
  Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add extensions-builtin/ settings.gradle
git commit -m "feat(extensions-builtin): create module scaffold"
```

### Task 2.2 — `ParagraphMapper`

Moves the `KParagraph.toParagraph()` / `GParagraph.toParagraph()` conversions from `hub-server` into this module, now targeting the new `extension_api.model.Paragraph`.

- [ ] Write failing test — `ParagraphMapperTest.kt`:

```kotlin
class ParagraphMapperTest {
    @Test fun `KText maps to Paragraph Text`() {
        val result = KText("hello").toExtParagraph()
        assertTrue(result is ExtParagraph.Text)
        assertEquals("hello", (result as ExtParagraph.Text).content)
    }

    @Test fun `KReplyTo maps to Paragraph ReplyTo`() {
        val result = KReplyTo(">>123").toExtParagraph()
        assertTrue(result is ExtParagraph.ReplyTo)
    }

    @Test fun `GText maps to Paragraph Text`() {
        val result = GText("world").toExtParagraph()
        assertTrue(result is ExtParagraph.Text)
    }
}
```

- [ ] Run: `./gradlew :extensions-builtin:test`
  Expected: FAIL (functions not defined)

- [ ] Create `ParagraphMapper.kt`:

```kotlin
package tw.kevinzhang.extensions_builtin

import tw.kevinzhang.extension_api.model.Paragraph as ExtParagraph
import tw.kevinzhang.gamer_api.model.*
import tw.kevinzhang.komica_api.model.*

fun KParagraph.toExtParagraph(): ExtParagraph = when (this) {
    is KQuote    -> ExtParagraph.Quote(content)
    is KReplyTo  -> ExtParagraph.ReplyTo(content)
    is KText     -> ExtParagraph.Text(content)
    is KImageInfo -> ExtParagraph.ImageInfo(thumb, raw)
    is KVideoInfo -> ExtParagraph.VideoInfo(url)
    is KLink     -> ExtParagraph.Link(content)
    else         -> throw IllegalArgumentException("Unknown KParagraph: $this")
}

fun GParagraph.toExtParagraph(): ExtParagraph = when (this) {
    is GQuote    -> ExtParagraph.Quote(content)
    is GReplyTo  -> ExtParagraph.ReplyTo(content)
    is GText     -> ExtParagraph.Text(content)
    is GImageInfo -> ExtParagraph.ImageInfo(thumb, raw)
    is GLink     -> ExtParagraph.Link(content)
    else         -> throw IllegalArgumentException("Unknown GParagraph: $this")
}
```

- [ ] Run: `./gradlew :extensions-builtin:test`
  Expected: PASS

- [ ] Commit:
```bash
git add extensions-builtin/
git commit -m "feat(extensions-builtin): add ParagraphMapper for gamer-api and komica-api"
```

### Task 2.3 — `GamerSource`

Wraps the existing `GamerNewsRepositoryImpl` and `GamerThreadRepositoryImpl` from `hub-server`. For this phase, inject the repositories via constructor (Hilt will wire them later in Phase 3).

- [ ] Create `GamerSource.kt`:

```kotlin
package tw.kevinzhang.extensions_builtin.gamer

import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.*
import tw.kevinzhang.extensions_builtin.toExtParagraph
import tw.kevinzhang.hub_server.data.news.gamer.GamerNewsRepositoryImpl
import tw.kevinzhang.hub_server.data.post.gamer.GamerThreadRepositoryImpl
import javax.inject.Inject

class GamerSource @Inject constructor(
    private val newsRepo: GamerNewsRepositoryImpl,
    private val threadRepo: GamerThreadRepositoryImpl,
) : Source {
    override val id = "tw.kevinzhang.gamer"
    override val name = "Gamer 巴哈姆特"
    override val language = "zh-TW"
    override val version = 1
    override val iconUrl = null

    override suspend fun getBoards(): List<Board> =
        newsRepo.getBoards().map { board ->
            Board(
                sourceId = id,
                url = board.url,
                name = board.name,
            )
        }

    override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> =
        newsRepo.getNews(board.url, page).map { news ->
            ThreadSummary(
                sourceId = id,
                boardUrl = board.url,
                id = news.id,
                title = news.title,
                author = news.author,
                createdAt = news.createdAt,
                replyCount = news.replyCount,
                thumbnail = news.thumbnail,
                previewContent = news.content.map { it.toExtParagraph() },
            )
        }

    override suspend fun getThread(summary: ThreadSummary): Thread {
        val thread = threadRepo.getThread(summary.id, summary.boardUrl)
        return Thread(
            id = summary.id,
            title = summary.title,
            posts = thread.posts.map { post ->
                Post(
                    id = post.id,
                    author = post.author,
                    createdAt = post.createdAt,
                    thumbnail = post.thumbnail,
                    content = post.content.map { it.toExtParagraph() },
                    comments = post.comments.map { comment ->
                        Comment(
                            id = comment.id,
                            author = comment.author,
                            createdAt = comment.createdAt,
                            content = comment.content.map { it.toExtParagraph() },
                        )
                    },
                )
            },
        )
    }
}
```

> **Note:** The exact method names (`getBoards()`, `getNews()`, `getThread()`) should be verified against the actual `GamerNewsRepositoryImpl` and `GamerThreadRepositoryImpl` in `hub-server`. Adapt as needed — the mapping pattern is the same regardless of exact method names.

- [ ] Run: `./gradlew :extensions-builtin:assemble`
  Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add extensions-builtin/
git commit -m "feat(extensions-builtin): add GamerSource"
```

### Task 2.4 — `SoraSource` and `_2catSource`

Follow the exact same pattern as `GamerSource`, wrapping `KomicaNewsRepositoryImpl` / `KomicaThreadRepositoryImpl` from `hub-server`. Sora and _2cat share **identical** repository implementations — the two sources are differentiated entirely by the board URLs returned from `getBoards()`. `KomicaNewsRepositoryImpl.getAllNews(board)` accepts a `Board` value and reads `board.url` to build the HTTP request; there is no host enum or constructor parameter to set. `Host.kt` in `hub-server` only declares `enum class Host { KOMICA, GAMER }` with no URL constants.

Concrete example for `getBoards()`:

```kotlin
// SoraSource.kt
override suspend fun getBoards(): List<Board> = listOf(
    Board(sourceId = id, url = "https://sora.komica.org/00b/", name = "/b/ 綜合"),
    Board(sourceId = id, url = "https://sora.komica.org/00c/", name = "/c/ 創作"),
    // … add remaining sora boards
)

// _2catSource.kt
override suspend fun getBoards(): List<Board> = listOf(
    Board(sourceId = id, url = "https://2cat.komica.org/c/", name = "/c/ 創作"),
    // … add remaining 2cat boards
)
```

All other methods (`getThreadSummaries`, `getThread`) delegate to the shared Komica repos exactly as in `GamerSource` — pass the `Board` / `ThreadSummary` received as parameters straight through.

- [ ] Create `SoraSource.kt` (mirrors `GamerSource`, uses Komica repos, `id = "tw.kevinzhang.komica-sora"`, board URLs are `sora.komica.org/*`)

- [ ] Create `_2catSource.kt` (mirrors `GamerSource`, uses Komica repos, `id = "tw.kevinzhang.komica-2cat"`, board URLs are `2cat.komica.org/*`)

- [ ] Run: `./gradlew :extensions-builtin:assemble`
  Expected: BUILD SUCCESSFUL

- [ ] Run: `./gradlew assembleDebug`
  Expected: BUILD SUCCESSFUL (existing app untouched)

- [ ] Commit:
```bash
git add extensions-builtin/
git commit -m "feat(extensions-builtin): add SoraSource and _2catSource"
```

---

## Phase 3 — `:extension-loader` + `:collection`

### `:extension-loader`

Android library. Manages the `Source` registry via Hilt multibinding. Handles both built-in injection and runtime APK loading.

**Files:**
- Create: `extension-loader/build.gradle`
- Create: `extension-loader/src/main/AndroidManifest.xml`
- Create: `extension-loader/src/main/java/tw/kevinzhang/extension_loader/ExtensionLoader.kt`
- Create: `extension-loader/src/main/java/tw/kevinzhang/extension_loader/ExtensionLoaderImpl.kt`
- Create: `extension-loader/src/main/java/tw/kevinzhang/extension_loader/di/ExtensionModule.kt`
- Test: `extension-loader/src/test/java/.../ExtensionLoaderTest.kt`
- Modify: `settings.gradle`

### Task 3.1 — `ExtensionLoader` interface + impl

- [ ] Add `include ':extension-loader'` to `settings.gradle`

- [ ] Create `extension-loader/build.gradle`:

```groovy
plugins {
    id 'com.android.library'
    id 'org.jetbrains.kotlin.android'
    id 'kotlin-kapt'
    id 'dagger.hilt.android.plugin'
}
android {
    compileSdk 33
    defaultConfig { minSdk 21 }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = '1.8' }
}
dependencies {
    implementation project(':extension-api')
    implementation project(':extensions-builtin')
    implementation "com.google.dagger:hilt-android:$versions.hilt"
    kapt "com.google.dagger:hilt-android-compiler:$versions.hilt"
    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] Create `ExtensionLoader.kt`:

```kotlin
package tw.kevinzhang.extension_loader

import tw.kevinzhang.extension_api.Source

interface ExtensionLoader {
    fun getAllSources(): List<Source>
    fun getSource(id: String): Source?
}
```

- [ ] Write failing test — `ExtensionLoaderTest.kt`:

```kotlin
class ExtensionLoaderTest {
    private fun makeSource(id: String) = object : Source {
        override val id = id
        override val name = id
        override val language = "zh-TW"
        override val version = 1
        override val iconUrl = null
        override suspend fun getBoards() = emptyList<Board>()
        override suspend fun getThreadSummaries(board: Board, page: Int) = emptyList<ThreadSummary>()
        override suspend fun getThread(summary: ThreadSummary) = Thread("", null, emptyList())
    }

    @Test fun `getSource returns null for unknown id`() {
        val loader = ExtensionLoaderImpl(
            builtInSources = listOf(makeSource("tw.a")),
            context = mockContext(), // see note below
        )
        assertNull(loader.getSource("tw.unknown"))
    }

    @Test fun `getSource returns built-in source by id`() {
        val source = makeSource("tw.a")
        val loader = ExtensionLoaderImpl(builtInSources = listOf(source), context = mockContext())
        assertEquals(source, loader.getSource("tw.a"))
    }
}
```

> **Note:** For unit tests, use `ApplicationProvider.getApplicationContext()` from `androidx.test:core` for `mockContext()`, or mock with Mockito. Add `testImplementation 'androidx.test:core:1.4.0'` to deps if needed.

- [ ] Run: `./gradlew :extension-loader:test`
  Expected: FAIL

- [ ] Create `ExtensionLoaderImpl.kt`:

```kotlin
package tw.kevinzhang.extension_loader

import android.content.Context
import android.content.pm.PackageManager
import dalvik.system.PathClassLoader
import tw.kevinzhang.extension_api.Source
import javax.inject.Inject

private const val EXTENSION_META_KEY = "newshub.extension"
private const val SOURCE_CLASS_KEY = "newshub.extension.source_class"

class ExtensionLoaderImpl @Inject constructor(
    private val builtInSources: List<Source>,
    private val context: Context,
) : ExtensionLoader {

    override fun getAllSources(): List<Source> =
        builtInSources + loadInstalledApkSources()

    override fun getSource(id: String): Source? =
        getAllSources().find { it.id == id }

    private fun loadInstalledApkSources(): List<Source> =
        context.packageManager
            .getInstalledPackages(PackageManager.GET_META_DATA)
            .filter { pkg ->
                pkg.applicationInfo?.metaData?.containsKey(EXTENSION_META_KEY) == true
            }
            .mapNotNull { pkg -> loadSourceFromPackage(pkg) }

    private fun loadSourceFromPackage(pkg: android.content.pm.PackageInfo): Source? = try {
        val appInfo = pkg.applicationInfo ?: return null
        val className = appInfo.metaData?.getString(SOURCE_CLASS_KEY) ?: return null
        val loader = PathClassLoader(appInfo.sourceDir, context.classLoader)
        val clazz = loader.loadClass(className)
        clazz.getDeclaredConstructor().newInstance() as? Source
    } catch (e: Exception) {
        null  // silently skip invalid extensions
    }
}
```

- [ ] Run: `./gradlew :extension-loader:test`
  Expected: PASS

### Task 3.2 — Hilt multibinding for built-in sources

- [ ] Create `di/ExtensionModule.kt` in `:extension-loader`:

```kotlin
package tw.kevinzhang.extension_loader.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_loader.ExtensionLoader
import tw.kevinzhang.extension_loader.ExtensionLoaderImpl
import tw.kevinzhang.extensions_builtin.gamer.GamerSource
import tw.kevinzhang.extensions_builtin.sora.SoraSource
import tw.kevinzhang.extensions_builtin._2cat._2catSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtensionModule {

    @Binds
    @Singleton
    abstract fun bindExtensionLoader(impl: ExtensionLoaderImpl): ExtensionLoader

    companion object {
        @Provides
        @Singleton
        fun provideBuiltInSources(
            gamer: GamerSource,
            sora: SoraSource,
            _2cat: _2catSource,
        ): List<Source> = listOf(gamer, sora, _2cat)
    }
}
```

- [ ] Run: `./gradlew :extension-loader:assemble`
  Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add extension-loader/ settings.gradle
git commit -m "feat(extension-loader): add ExtensionLoader with built-in Hilt multibinding"
```

### `:collection`

Android library with Room. Two tables: `CollectionEntity` + `BoardSubscriptionEntity`. Separate DB from `hub-server`'s `AppDatabase`.

**Files:**
- Create: `collection/build.gradle`
- Create: `collection/src/main/AndroidManifest.xml`
- Create: `collection/src/main/java/tw/kevinzhang/collection/data/CollectionEntity.kt`
- Create: `collection/src/main/java/tw/kevinzhang/collection/data/BoardSubscriptionEntity.kt`
- Create: `collection/src/main/java/tw/kevinzhang/collection/data/CollectionDao.kt`
- Create: `collection/src/main/java/tw/kevinzhang/collection/data/CollectionDatabase.kt`
- Create: `collection/src/main/java/tw/kevinzhang/collection/CollectionRepository.kt`
- Create: `collection/src/main/java/tw/kevinzhang/collection/CollectionRepositoryImpl.kt`
- Create: `collection/src/main/java/tw/kevinzhang/collection/di/CollectionModule.kt`
- Modify: `settings.gradle`

### Task 3.3 — Room entities + DAO

- [ ] Add `include ':collection'` to `settings.gradle`

- [ ] Create `collection/build.gradle`:

```groovy
plugins {
    id 'com.android.library'
    id 'org.jetbrains.kotlin.android'
    id 'kotlin-kapt'
    id 'dagger.hilt.android.plugin'
}
android {
    compileSdk 33
    defaultConfig { minSdk 21 }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = '1.8' }
}
dependencies {
    implementation project(':extension-api')
    implementation "androidx.room:room-runtime:$versions.room"
    implementation "androidx.room:room-ktx:$versions.room"
    kapt "androidx.room:room-compiler:$versions.room"
    implementation "com.google.dagger:hilt-android:$versions.hilt"
    kapt "com.google.dagger:hilt-android-compiler:$versions.hilt"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:$versions.coroutines"
    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] Create `CollectionEntity.kt`:

```kotlin
package tw.kevinzhang.collection.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,   // UUID
    val name: String,
    val sortOrder: Int,
)
```

- [ ] Create `BoardSubscriptionEntity.kt`:

```kotlin
package tw.kevinzhang.collection.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "board_subscriptions")
data class BoardSubscriptionEntity(
    @PrimaryKey val id: String,   // UUID
    val collectionId: String,
    val sourceId: String,
    val boardUrl: String,
    val boardName: String,        // cached — avoids loading Source just to show the name
    val sortOrder: Int,
)
```

- [ ] Create `CollectionDao.kt`:

```kotlin
package tw.kevinzhang.collection.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sortOrder")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(entity: CollectionEntity)

    @Delete
    suspend fun deleteCollection(entity: CollectionEntity)

    @Query("SELECT * FROM board_subscriptions WHERE collectionId = :collectionId ORDER BY sortOrder")
    fun observeSubscriptions(collectionId: String): Flow<List<BoardSubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(entity: BoardSubscriptionEntity)

    @Delete
    suspend fun deleteSubscription(entity: BoardSubscriptionEntity)

    @Query("DELETE FROM board_subscriptions WHERE sourceId = :sourceId")
    suspend fun deleteSubscriptionsBySource(sourceId: String)
}
```

- [ ] Create `CollectionDatabase.kt`:

```kotlin
package tw.kevinzhang.collection.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CollectionEntity::class, BoardSubscriptionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CollectionDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao
}
```

- [ ] Run: `./gradlew :collection:assemble`
  Expected: BUILD SUCCESSFUL

### Task 3.4 — Repository + Hilt wiring

- [ ] Create `CollectionRepository.kt`:

```kotlin
package tw.kevinzhang.collection

import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.collection.data.BoardSubscriptionEntity
import tw.kevinzhang.collection.data.CollectionEntity

interface CollectionRepository {
    fun observeCollections(): Flow<List<CollectionEntity>>
    fun observeSubscriptions(collectionId: String): Flow<List<BoardSubscriptionEntity>>
    suspend fun createCollection(name: String)
    suspend fun deleteCollection(id: String)
    suspend fun addBoardSubscription(collectionId: String, sourceId: String, boardUrl: String, boardName: String)
    suspend fun removeBoardSubscription(subscriptionId: String)
    suspend fun removeAllSubscriptionsForSource(sourceId: String)
}
```

- [ ] Create `CollectionRepositoryImpl.kt` (delegates to `CollectionDao`, creates UUIDs with `java.util.UUID.randomUUID().toString()`)

- [ ] Create `di/CollectionModule.kt` — provides `CollectionDatabase` (via `Room.databaseBuilder`), binds `CollectionRepositoryImpl` to `CollectionRepository`

- [ ] Run: `./gradlew assembleDebug`
  Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add collection/ settings.gradle
git commit -m "feat(collection): add Room DB, CollectionRepository, Hilt wiring"
```

---

## Phase 4 — `:app` UI

Remove the old `Topic`-based navigation and replace with a three-tab bottom nav. Wire the new modules into the app via Hilt. Build the Collection timeline and update the Thread screen for `Paragraph.ReplyTo`.

**Files to modify:**
- `app/build.gradle` — add deps on `:extension-loader`, `:collection`
- `app/src/main/java/tw/kevinzhang/newshub/di/AppModule.kt` — wire new modules
- `app/src/main/java/tw/kevinzhang/newshub/ui/AppScreen.kt` — replace drawer with bottom nav
- `app/src/main/java/tw/kevinzhang/newshub/ui/navigation/AppNavigation.kt` — add new routes

**Files to create:**
- `app/src/main/java/tw/kevinzhang/newshub/ui/collection/CollectionTimelineScreen.kt`
- `app/src/main/java/tw/kevinzhang/newshub/ui/collection/CollectionTimelineViewModel.kt`
- `app/src/main/java/tw/kevinzhang/newshub/ui/collection/MergedTimelinePagingSource.kt`
- `app/src/main/java/tw/kevinzhang/newshub/ui/extensions/ExtensionsScreen.kt`
- `app/src/main/java/tw/kevinzhang/newshub/ui/extensions/ExtensionsViewModel.kt`
- `app/src/main/java/tw/kevinzhang/newshub/ui/thread/ThreadScreen.kt` (modify existing)

### Task 4.1 — Wire new deps + update AppModule

- [ ] Add to `app/build.gradle` dependencies:

```groovy
implementation project(':extension-loader')
implementation project(':collection')
```

- [ ] In `AppModule.kt`, ensure Hilt can find the `ExtensionModule` and `CollectionModule`. Remove or deprecate the old `TopicUseCase`/`BoardUseCase` Hilt providers (they'll be unused after this phase).

- [ ] Run: `./gradlew assembleDebug`
  Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add app/
git commit -m "feat(app): wire extension-loader and collection modules"
```

### Task 4.2 — `MergedTimelinePagingSource`

- [ ] Create `MergedTimelinePagingSource.kt`:

```kotlin
package tw.kevinzhang.newshub.ui.collection

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tw.kevinzhang.collection.data.BoardSubscriptionEntity
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.ThreadSummary

class MergedTimelinePagingSource(
    private val subscriptions: List<BoardSubscriptionEntity>,
    private val sourceResolver: (String) -> Source?,
) : PagingSource<Int, ThreadSummary>() {

    override fun getRefreshKey(state: PagingState<Int, ThreadSummary>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ThreadSummary> {
        val page = params.key ?: 1
        return try {
            val results = coroutineScope {
                subscriptions
                    .mapNotNull { sub ->
                        val source = sourceResolver(sub.sourceId) ?: return@mapNotNull null
                        val board = Board(sub.sourceId, sub.boardUrl, sub.boardName)
                        async { source.getThreadSummaries(board, page) }
                    }
                    .awaitAll()
                    .flatten()
            }
            // Per-batch sort only — no global ordering across page boundaries (by design)
            val sorted = results.sortedByDescending { it.createdAt }
            LoadResult.Page(
                data = sorted,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (sorted.isEmpty()) null else page + 1,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
```

- [ ] Commit:
```bash
git add app/src/main/java/tw/kevinzhang/newshub/ui/collection/
git commit -m "feat(app): add MergedTimelinePagingSource"
```

### Task 4.3 — `CollectionTimelineViewModel` + Screen

- [ ] Create `CollectionTimelineViewModel.kt`:

```kotlin
@HiltViewModel
class CollectionTimelineViewModel @Inject constructor(
    private val collectionRepo: CollectionRepository,
    private val extensionLoader: ExtensionLoader,
) : ViewModel() {

    fun getTimelinePager(collectionId: String): Flow<PagingData<ThreadSummary>> {
        return collectionRepo.observeSubscriptions(collectionId)
            .flatMapLatest { subs ->
                Pager(PagingConfig(pageSize = 20)) {
                    MergedTimelinePagingSource(
                        subscriptions = subs,
                        sourceResolver = { extensionLoader.getSource(it) },
                    )
                }.flow
            }
            .cachedIn(viewModelScope)
    }
}
```

- [ ] Create `CollectionTimelineScreen.kt` — `LazyColumn` with `collectAsLazyPagingItems()`. Reuse existing `KomicaPostCard` / `GamerPostCard` composables for now, switching on `ThreadSummary.sourceId`. Source badge (from existing label tag in mockup) uses `ThreadSummary.sourceId`.

- [ ] Run: `./gradlew assembleDebug`
  Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add app/src/main/java/tw/kevinzhang/newshub/ui/collection/
git commit -m "feat(app): add CollectionTimelineViewModel and Screen"
```

### Task 4.4 — Bottom nav + Thread screen `ReplyTo` Dialog

- [ ] Update `AppScreen.kt` — replace drawer nav with `NavigationBar` (Compose Material 3) with three items: Collections, Extensions, Settings.

- [ ] Update `AppNavigation.kt` — add routes:
  - `collections` → `CollectionTimelineScreen`
  - `extensions` → `ExtensionsScreen` (stub for now)
  - `settings` → existing settings

- [ ] Update `ThreadScreen.kt` — add `previewPost: Post?` state and `AlertDialog` that renders `previewPost.content` via existing `ParagraphBlock`. Tapping `Paragraph.ReplyTo` calls `viewModel.onReplyToClick(targetId)`.

- [ ] Update `ThreadViewModel.kt`:

```kotlin
val previewPost = MutableStateFlow<Post?>(null)

fun onReplyToClick(targetId: String) {
    previewPost.value = _thread.value?.posts?.find { it.id == targetId }
}

fun dismissPreview() { previewPost.value = null }
```

- [ ] Run: `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`
  Expected: BUILD SUCCESSFUL, app installs

- [ ] Commit:
```bash
git add app/
git commit -m "feat(app): bottom nav, Collection timeline wired, ReplyTo dialog in ThreadScreen"
```

---

## Phase 5 — `:marketplace`

GitHub index fetch, install-state derivation, APK download + system installer trigger.

**Files:**
- Create: `marketplace/build.gradle`
- Create: `marketplace/src/main/AndroidManifest.xml`
- Create: `marketplace/src/main/java/tw/kevinzhang/marketplace/data/ExtensionInfo.kt`
- Create: `marketplace/src/main/java/tw/kevinzhang/marketplace/data/MarketplaceApi.kt`
- Create: `marketplace/src/main/java/tw/kevinzhang/marketplace/MarketplaceRepository.kt`
- Create: `marketplace/src/main/java/tw/kevinzhang/marketplace/MarketplaceRepositoryImpl.kt`
- Create: `marketplace/src/main/java/tw/kevinzhang/marketplace/di/MarketplaceModule.kt`
- Create: `app/src/main/java/tw/kevinzhang/newshub/ui/marketplace/MarketplaceScreen.kt`
- Create: `app/src/main/java/tw/kevinzhang/newshub/ui/marketplace/MarketplaceViewModel.kt`
- Create: `app/src/main/java/tw/kevinzhang/newshub/ui/extensions/ExtensionsScreen.kt`
- Create: `app/src/main/java/tw/kevinzhang/newshub/ui/extensions/ExtensionsViewModel.kt`
- Modify: `settings.gradle`

### Task 5.1 — `ExtensionInfo` + index parsing

- [ ] Add `include ':marketplace'` to `settings.gradle`

- [ ] Create `marketplace/build.gradle`:

```groovy
plugins {
    id 'com.android.library'
    id 'org.jetbrains.kotlin.android'
    id 'kotlin-kapt'
    id 'dagger.hilt.android.plugin'
}
android {
    compileSdk 33
    defaultConfig { minSdk 21 }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = '1.8' }
}
dependencies {
    implementation "com.google.dagger:hilt-android:$versions.hilt"
    kapt "com.google.dagger:hilt-android-compiler:$versions.hilt"
    implementation "com.squareup.okhttp3:okhttp:$versions.okhttp"
    implementation "com.google.code.gson:gson:$versions.gson"
    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] Create `ExtensionInfo.kt`:

```kotlin
package tw.kevinzhang.marketplace.data

data class ExtensionInfo(
    val id: String,
    val name: String,
    val version: Int,
    val versionName: String,
    val language: String,
    val iconUrl: String?,
    val apkUrl: String,
)

// index.json root
data class ExtensionIndex(val extensions: List<ExtensionInfo>)

enum class InstallState { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE }
```

- [ ] Write failing test — `ExtensionInfoTest.kt`:

```kotlin
class ExtensionInfoTest {
    @Test fun `parses index json correctly`() {
        val json = """
            {"extensions":[{"id":"tw.a","name":"A","version":2,"versionName":"1.0","language":"zh-TW","iconUrl":null,"apkUrl":"http://a.apk"}]}
        """.trimIndent()
        val index = Gson().fromJson(json, ExtensionIndex::class.java)
        assertEquals(1, index.extensions.size)
        assertEquals("tw.a", index.extensions[0].id)
        assertEquals(2, index.extensions[0].version)
    }
}
```

- [ ] Run: `./gradlew :marketplace:test`
  Expected: PASS

- [ ] Commit:
```bash
git add marketplace/ settings.gradle
git commit -m "feat(marketplace): create module with ExtensionInfo and index parsing"
```

### Task 5.2 — `MarketplaceRepository`

- [ ] Create `MarketplaceRepository.kt`:

```kotlin
package tw.kevinzhang.marketplace

import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState

interface MarketplaceRepository {
    suspend fun fetchIndex(): List<ExtensionInfo>
    fun getInstallState(info: ExtensionInfo): InstallState
}
```

- [ ] Create `MarketplaceRepositoryImpl.kt`:

```kotlin
class MarketplaceRepositoryImpl @Inject constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    @Named("marketplaceIndexUrl") private val indexUrl: String,
) : MarketplaceRepository {

    override suspend fun fetchIndex(): List<ExtensionInfo> = withContext(Dispatchers.IO) {
        val response = okHttpClient.newCall(Request.Builder().url(indexUrl).build()).execute()
        val body = response.body?.string() ?: return@withContext emptyList()
        gson.fromJson(body, ExtensionIndex::class.java).extensions
    }

    override fun getInstallState(info: ExtensionInfo): InstallState {
        val pkg = try {
            context.packageManager.getPackageInfo(info.id, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return InstallState.NOT_INSTALLED
        }
        val installedVersion = PackageInfoCompat.getLongVersionCode(pkg).toInt()
        return if (installedVersion < info.version) InstallState.UPDATE_AVAILABLE
        else InstallState.INSTALLED
    }
}
```

- [ ] Create `di/MarketplaceModule.kt` — provides `OkHttpClient`, `Gson`, binds `MarketplaceRepositoryImpl`, provides `indexUrl` as `@Named("marketplaceIndexUrl")` string (default to empty, configurable from Settings).

- [ ] Run: `./gradlew :marketplace:assemble`
  Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add marketplace/
git commit -m "feat(marketplace): add MarketplaceRepository with index fetch and install state"
```

### Task 5.3 — Marketplace + Extensions UI

- [ ] Add `:marketplace` to `app/build.gradle` deps.

- [ ] Create `MarketplaceViewModel.kt` — exposes `StateFlow<List<Pair<ExtensionInfo, InstallState>>>`. On init, calls `fetchIndex()` and maps each item with `getInstallState()`.

- [ ] Add a `downloadApk(apkUrl: String): File` suspend function to `MarketplaceRepository` (or a new `ApkDownloader` helper). It downloads the APK to `context.cacheDir` via `OkHttpClient` and returns the `File`:

```kotlin
suspend fun downloadApk(apkUrl: String): File = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(apkUrl).build()
    val response = okHttpClient.newCall(request).execute()
    val destFile = File(context.cacheDir, apkUrl.substringAfterLast('/'))
    response.body!!.byteStream().use { input ->
        destFile.outputStream().use { output -> input.copyTo(output) }
    }
    destFile
}
```

- [ ] Add `FileProvider` to `app/src/main/AndroidManifest.xml` and create `res/xml/file_paths.xml`:

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>

<!-- res/xml/file_paths.xml -->
<paths>
    <cache-path name="apk_cache" path="." />
</paths>
```

- [ ] Create `MarketplaceScreen.kt` — `LazyColumn` listing each extension with name, language, version, and an action button (Install / Update / Installed). Tapping Install/Update calls `downloadApk()` then triggers the system installer:

```kotlin
fun installExtension(apkFile: File) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(
            FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile),
            "application/vnd.android.package-archive"
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}
```

In `MarketplaceViewModel`, expose a `install(info: ExtensionInfo)` function that calls `downloadApk(info.apkUrl)` then passes the result to `installExtension()`.

- [ ] Create `ExtensionsScreen.kt` — lists installed sources from `ExtensionLoader.getAllSources()`. For each source, shows name and list of boards (calls `source.getBoards()`). Each board has an "Add to Collection" action.

- [ ] Create `ExtensionsViewModel.kt` — loads `getAllSources()`, calls `getBoards()` per source. `addBoardToCollection(collectionId, board)` calls `collectionRepo.addBoardSubscription(...)`.

- [ ] Wire `MarketplaceScreen` and `ExtensionsScreen` into `AppNavigation.kt` under the Extensions tab.

- [ ] Run: `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`
  Expected: BUILD SUCCESSFUL, full app installs

- [ ] Smoke test on device:
  - Collections tab loads
  - Extensions tab shows Gamer, Sora, _2cat
  - Tapping a board shows board's boards list
  - Marketplace tab loads (index URL empty = empty list, correct)

- [ ] Commit:
```bash
git add app/ marketplace/
git commit -m "feat(app): add Marketplace and Extensions UI screens"
```

---

## Final Verification

- [ ] `./gradlew assembleDebug` — BUILD SUCCESSFUL
- [ ] `./gradlew :extension-api:test :extensions-builtin:test :collection:test :marketplace:test` — all PASS
- [ ] `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- [ ] Manually verify:
  - [ ] Create a Collection
  - [ ] Browse Extensions tab → pick a board → add to Collection
  - [ ] Open Collection → see merged timeline
  - [ ] Tap a thread → Thread screen loads posts
  - [ ] Tap a `Paragraph.ReplyTo` link → Dialog shows target Post
  - [ ] Marketplace tab loads without crash

---

## File Map Summary

```
extension-api/                          ← NEW (Kotlin JVM library)
  Source.kt
  model/{Board, ThreadSummary, Thread, Post, Comment, Paragraph}.kt

extensions-builtin/                     ← NEW (Android library)
  ParagraphMapper.kt
  gamer/GamerSource.kt
  sora/SoraSource.kt
  _2cat/_2catSource.kt

extension-loader/                       ← NEW (Android library)
  ExtensionLoader.kt
  ExtensionLoaderImpl.kt
  di/ExtensionModule.kt

collection/                             ← NEW (Android library)
  data/{CollectionEntity, BoardSubscriptionEntity, CollectionDao, CollectionDatabase}.kt
  CollectionRepository.kt
  CollectionRepositoryImpl.kt
  di/CollectionModule.kt

marketplace/                            ← NEW (Android library)
  data/{ExtensionInfo, ExtensionIndex, InstallState}.kt
  MarketplaceRepository.kt
  MarketplaceRepositoryImpl.kt
  di/MarketplaceModule.kt

app/src/main/java/tw/kevinzhang/newshub/
  ui/collection/
    CollectionTimelineScreen.kt         ← NEW
    CollectionTimelineViewModel.kt      ← NEW
    MergedTimelinePagingSource.kt       ← NEW
  ui/extensions/
    ExtensionsScreen.kt                 ← NEW
    ExtensionsViewModel.kt              ← NEW
  ui/marketplace/
    MarketplaceScreen.kt                ← NEW
    MarketplaceViewModel.kt             ← NEW
  ui/thread/ThreadScreen.kt             ← MODIFY (ReplyTo dialog)
  ui/thread/ThreadViewModel.kt          ← MODIFY (previewPost state)
  ui/AppScreen.kt                       ← MODIFY (bottom nav)
  ui/navigation/AppNavigation.kt        ← MODIFY (new routes)
  di/AppModule.kt                       ← MODIFY (wire new modules)

settings.gradle                         ← MODIFY (add 5 new modules)
hub-server/                             ← UNTOUCHED (remove in follow-up)
```
