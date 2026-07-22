# matrix-rust-sdk: import backfill events for Setka Lite

> **Status:** proposal / upstream issue draft  
> **Target repo:** https://github.com/matrix-org/matrix-rust-sdk  
> **Element X Android consumer:** `libraries/setkalite` (Setka Lite slow-sync path)

## Problem

Setka Lite fetches room history through a custom homeserver endpoint (`/_matrix/client/v3/setka_lite/rooms/{roomId}/events`) while normal `/sync` is paused, to save bandwidth on slow links.

Events are valid Matrix **client events** (same shape as `/messages` backwards pagination). Today the Android client can only:

1. Cache JSON on disk
2. Call `timeline.paginate(BACKWARDS)` (standard `/messages` via SDK)

That does **not** insert lite-fetched events into the **Event Cache** / SQLite store. Encrypted history remains invisible until a full fast sync catches up.

`EventCache::add_initial_events()` existed temporarily but is deprecated/ignored when persistent storage is enabled (default since ~2025).

## Use case

**Setka Matrix** — optional “Слабый интернет” mode on `setka-matrix.ru`:

| Phase | Transport | SDK today |
|-------|-----------|-----------|
| Fast path | Normal `/sync` | ✅ Event cache |
| Lite path | `setka_lite/*` paginated JSON | ❌ not in store |
| Crypto | `setka_lite/crypto/to_device` | ❌ only via `/sync` |

We stop sync during lite backfill, fetch pages slowly (throttled), then restart sync. Without import, lite work is mostly wasted for E2EE rooms.

## Proposed API (Rust)

Expose a public, supported API that reuses the **same code path as backwards `/messages` pagination** (`conclude_backwards_pagination_from_network` in `event_cache/caches/room/pagination.rs`):

```rust
/// Result of importing a backwards pagination batch from an external source.
pub struct ImportBackwardsPaginationResult {
    pub imported_count: usize,
    pub reached_timeline_start: bool,
}

impl Room {
    /// Import client-format timeline events as if returned by
    /// `GET /_matrix/client/v3/rooms/{roomId}/messages?dir=b`.
    ///
    /// - `raw_events`: newest-first order (same as `/messages` backwards chunk)
    /// - `from_token`: gap token being filled (`None` if filling open end)
    /// - `end_token`: `prev_batch` for older history (`None` if start of room)
    ///
    /// Events are deduplicated, decrypted when possible, persisted to Event Cache,
    /// and timeline subscribers receive `UpdateTimelineEvents`.
    pub async fn import_backwards_pagination_events(
        &self,
        raw_events: Vec<serde_json::Value>,
        from_token: Option<String>,
        end_token: Option<String>,
    ) -> Result<ImportBackwardsPaginationResult, EventCacheError>;
}
```

### To-device (crypto)

```rust
impl Client {
    /// Process to-device events as if they arrived in a `/sync` response.
    /// Used when sync was paused during lite backfill.
    pub async fn import_to_device_events(
        &self,
        events: Vec<serde_json::Value>,
    ) -> Result<usize, ClientError>;
}
```

## Implementation notes (for SDK maintainers)

1. **Reuse pagination pipeline** — parse `raw_events` into `Event` the same way as `room.messages()` → feed `RoomEventCacheInner::conclude_backwards_pagination_from_network`.
2. **Do not bypass Event Cache** — must persist to linked chunks + notify `RoomEventCacheUpdate::UpdateTimelineEvents` with `EventsOrigin::Pagination` (or new `EventsOrigin::ExternalImport`).
3. **Encryption** — run existing decryption / UTD handling; lite endpoint returns ciphertext as-is from Synapse.
4. **Ordering** — document that input order matches `/messages` `dir=b` chunk order; SDK reverses internally (see comment at `pagination.rs:399`).
5. **UniFFI** — expose on `Room` + `Client` for Kotlin/Swift bindings.
6. **Tests** — import batch into empty room cache, assert timeline diffs + decrypt after keys arrive.

Reference sketch: [`rust-proposal/import_backwards_events.rs`](rust-proposal/import_backwards_events.rs)

## Android integration (ready)

When FFI lands in `org.matrix.rustcomponents:sdk-android`:

- `MatrixClient.importBackwardsRoomEvents()` → `Room.importBackwardsPaginationEvents()`
- `MatrixClient.importToDeviceEvents()` → `Client.importToDeviceEvents()`
- `SetkaLiteEventImporter` calls SDK first, keeps JSON cache as fallback

Kotlin stubs are already in `MatrixClient` / `RustMatrixClient` (return `MatrixClientImportEventsException` until SDK version bump).

## Suggested GitHub issue title

**Feature: import backwards pagination events + to-device batches for offline/slow sync paths**

## Acceptance criteria

- [ ] Import 50 encrypted events from JSON into joined room timeline (Element X test)
- [ ] Timeline updates without manual `/messages` round-trip
- [ ] Duplicate import is idempotent (dedup)
- [ ] To-device keys processed after import + decrypt retry
- [ ] Documented in SDK changelog + UniFFI

## Related upstream work

- Event Cache storage meta: https://github.com/matrix-org/matrix-rust-sdk/issues/3280
- Back-pagination: https://github.com/matrix-org/matrix-rust-sdk/pull/3195
- Deprecated `add_initial_events`: https://github.com/matrix-org/matrix-rust-sdk/pull/4372

---

Copy the **Proposed API** and **Problem** sections into a new GitHub issue on `matrix-org/matrix-rust-sdk`.

Ready-to-paste issue body: [`GITHUB_ISSUE_matrix-rust-sdk.md`](GITHUB_ISSUE_matrix-rust-sdk.md)

Draft implementation branch (local clone): `feature/setka-lite-event-import` in `matrix-rust-sdk` — compiles with `cargo check -p matrix-sdk-ffi`.
