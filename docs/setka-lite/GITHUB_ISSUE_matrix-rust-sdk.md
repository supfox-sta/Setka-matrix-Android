# GitHub Issue — matrix-org/matrix-rust-sdk

**Create at:** https://github.com/matrix-org/matrix-rust-sdk/issues/new

**Title:** Feature: import backwards pagination events + to-device batches for offline/slow sync paths

---

## Problem

Some Matrix clients fetch room history through **custom endpoints** while normal `/sync` is paused, to save bandwidth on slow links (Setka Lite on `setka-matrix.ru`).

Events are valid Matrix **client events** (same shape as `/messages` backwards pagination). Today embedders can only:

1. Cache JSON on disk
2. Call `timeline.paginate(BACKWARDS)` (standard `/messages` via SDK)

That does **not** insert lite-fetched events into the **Event Cache** / SQLite store. Encrypted history remains invisible until a full fast sync catches up.

`EventCache::add_initial_events()` existed temporarily but is deprecated/ignored when persistent storage is enabled (default since ~2025).

## Use case

| Phase | Transport | SDK today |
|-------|-----------|-----------|
| Fast path | Normal `/sync` | ✅ Event cache |
| Lite path | Custom paginated JSON | ❌ not in store |
| Crypto | Custom to-device batch | ❌ only via `/sync` |

We stop sync during lite backfill, fetch pages slowly (throttled), then restart sync. Without import, lite work is mostly wasted for E2EE rooms.

## Proposed API (Rust)

Reuse the **same code path as backwards `/messages` pagination** (`conclude_backwards_pagination_from_network` in `event_cache/caches/room/pagination.rs`):

```rust
pub struct ImportBackwardsPaginationResult {
    pub imported_count: usize,
    pub reached_timeline_start: bool,
}

impl Room {
    pub async fn import_backwards_pagination_events(
        &self,
        raw_events: Vec<serde_json::Value>,
        from_token: Option<String>,
        end_token: Option<String>,
    ) -> Result<ImportBackwardsPaginationResult, EventCacheError>;
}

impl Client {
    pub async fn import_to_device_events(
        &self,
        events: Vec<serde_json::Value>,
    ) -> Result<usize, ClientError>;
}
```

## Implementation notes

1. **Reuse pagination pipeline** — parse JSON → `Event` like `room.messages()` → `conclude_backwards_pagination_from_network`.
2. **Do not bypass Event Cache** — persist to linked chunks + notify `RoomEventCacheUpdate::UpdateTimelineEvents`.
3. **Encryption** — run existing decryption / UTD handling.
4. **Ordering** — input order matches `/messages` `dir=b` chunk order (newest-first).
5. **UniFFI** — expose on `Room` + `Client` for Kotlin/Swift.
6. **Tests** — import batch into empty room cache, assert timeline diffs + decrypt after keys arrive.

## Draft implementation

A draft PR branch exists locally: `feature/setka-lite-event-import` (Setka Matrix fork). Reference sketch in Element X Android: `docs/setka-lite/rust-proposal/import_backwards_events.rs`.

## Android integration (ready on client side)

When FFI lands in `org.matrix.rustcomponents:sdk-android`:

- `MatrixClient.importBackwardsRoomEvents()` → `Room.importBackwardsPaginationEvents()`
- `MatrixClient.importToDeviceEvents()` → `Client.importToDeviceEvents()`
- `SetkaLiteEventImporter` calls SDK first, keeps JSON cache as fallback

Kotlin stubs already return `MatrixClientImportEventsException` until SDK bump.

## Acceptance criteria

- [ ] Import 50 encrypted events from JSON into joined room timeline (Element X test)
- [ ] Timeline updates without manual `/messages` round-trip
- [ ] Duplicate import is idempotent (dedup)
- [ ] To-device keys processed after import + decrypt retry
- [ ] Documented in SDK changelog + UniFFI

## Related

- Event Cache storage meta: https://github.com/matrix-org/matrix-rust-sdk/issues/3280
- Back-pagination: https://github.com/matrix-org/matrix-rust-sdk/pull/3195
- Deprecated `add_initial_events`: https://github.com/matrix-org/matrix-rust-sdk/pull/4372
