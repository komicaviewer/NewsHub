# Design: BoardsScreen Sticky Source Headers

**Date:** 2026-04-05
**Status:** Approved

## Summary

When scrolling up in BoardsScreen, source name headers (e.g. "Gamer", "Komica") should stick below the TopAppBar instead of scrolling off screen — identical to the VS Code file explorer folder-name behaviour.

## Approach

Use Compose LazyColumn's built-in `stickyHeader` DSL.

## Change

**File:** `app/src/main/java/tw/kevinzhang/newshub/ui/boards/BoardsScreen.kt`

Replace:
```kotlin
item(key = source.id) { ... }
```
With:
```kotlin
stickyHeader(key = source.id) { ... }
```

The header composable body (Row with source name Text and optional Login/Logout button) remains unchanged.

## Behaviour

- Source header sticks below the TopAppBar when it would otherwise scroll off screen
- When the next source header reaches the top, it pushes the current one away
- Background is transparent — header appearance is identical in sticky and non-sticky states

## Out of Scope

- No ViewModel changes
- No new state
- No changes to BoardRow
