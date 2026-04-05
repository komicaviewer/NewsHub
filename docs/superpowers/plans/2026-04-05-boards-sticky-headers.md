# BoardsScreen Sticky Source Headers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Source name headers in BoardsScreen stick below the TopAppBar when scrolling, instead of scrolling off screen.

**Architecture:** Replace `item` with `stickyHeader` for the source header slot in the LazyColumn. No new state, no ViewModel changes.

**Tech Stack:** Jetpack Compose — `LazyColumn.stickyHeader` (foundation-lazy)

---

### Task 1: Replace `item` with `stickyHeader` in BoardsScreen

**Files:**
- Modify: `app/src/main/java/tw/kevinzhang/newshub/ui/boards/BoardsScreen.kt:81`

- [ ] **Step 1: Open the file and locate the source header slot**

In `BoardsScreen.kt`, inside the `LazyColumn` block, find:

```kotlin
item(key = source.id) {
```

- [ ] **Step 2: Replace `item` with `stickyHeader`**

Change that one line to:

```kotlin
stickyHeader(key = source.id) {
```

No other changes needed. The body of the block stays identical.

- [ ] **Step 3: Verify the import is present**

`stickyHeader` is part of `androidx.compose.foundation.lazy` — the same package already imported for `LazyColumn` and `items`. No new import needed.

- [ ] **Step 4: Build and manually verify**

Run the app, navigate to the Boards tab, scroll down past the first source's boards. The source name row should stick below the TopAppBar until the next source header pushes it away.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tw/kevinzhang/newshub/ui/boards/BoardsScreen.kt
git commit -m "feat(boards): sticky source headers when scrolling"
```
