// Proposal sketch for matrix-rust-sdk — NOT compiled in element-x-android.
// Target: crates/matrix-sdk/src/room/mod.rs + bindings/matrix-sdk-ffi/src/room/mod.rs
//
// Reuses RoomEventCacheInner::conclude_backwards_pagination_from_network after parsing JSON.

use matrix_sdk::deserialized_responses::TimelineEvent;
use matrix_sdk_base::event_cache::Event;
use ruma::events::AnyTimelineEvent;
use ruma::serde::Raw;
use serde_json::Value;

/// Result returned to embedders (UniFFI mirror: `ImportBackwardsPaginationResult`).
pub struct ImportBackwardsPaginationResult {
    pub imported_count: usize,
    pub reached_timeline_start: bool,
}

impl matrix_sdk::Room {
    /// Import client-format events from an external slow-sync source (Setka Lite).
    pub async fn import_backwards_pagination_events(
        &self,
        raw_events: Vec<Value>,
        from_token: Option<String>,
        end_token: Option<String>,
    ) -> matrix_sdk::event_cache::Result<ImportBackwardsPaginationResult> {
        let room_event_cache = self
            .client()
            .event_cache()
            .room(self.room_id())
            .await?;

        let raw_timeline: Vec<Raw<AnyTimelineEvent>> = raw_events
            .into_iter()
            .map(|json| Raw::from_json_string(json.to_string()))
            .collect::<Result<_, _>>()?;

        // TODO: reuse the same conversion as `room.messages()` → `Vec<Event>`
        // (decryption, stripped state, thread bundling, etc.)
        let events: Vec<Event> = self
            .hydrate_timeline_events_from_raw(raw_timeline)
            .await?;

        let outcome = room_event_cache
            .import_backwards_pagination_batch(events, from_token, end_token)
            .await?;

        Ok(ImportBackwardsPaginationResult {
            imported_count: outcome.events.len(),
            reached_timeline_start: outcome.reached_start,
        })
    }
}

// In RoomEventCacheInner (event_cache/caches/room/pagination.rs):
//
// pub(in crate::event_cache) async fn import_backwards_pagination_batch(
//     &self,
//     events: Vec<Event>,
//     from_token: Option<String>,
//     end_token: Option<String>,
// ) -> Result<Option<BackPaginationOutcome>> {
//     self.conclude_backwards_pagination_from_network(events, from_token, end_token).await
// }
