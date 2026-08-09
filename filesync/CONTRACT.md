# FileSync API Contract

This document defines the API contracts and invariants for the FileSync tool.

---

## Device Model

### `sourceOnly` flag

A device may be marked with `sourceOnly = true`. The following invariants **must** be
upheld everywhere in the codebase:

> **A device marked `sourceOnly` contributes its files to the sync group's accumulated
> file set but is never a write target — it is excluded from `missingOn` calculations
> and sync execution.**

Concretely:
- **Diff engine**: When computing which devices are missing a given file, a
  `sourceOnly` device **must not** appear in any `missingOn` list. Files discovered on
  a `sourceOnly` device are still added to the group's canonical file set so that
  _other_ (writable) devices can be checked against them.
- **Sync executor**: No copy job may target a `sourceOnly` device as a destination.
  Any job where the destination device has `sourceOnly = true` must be silently
  skipped / rejected before I/O begins.
- **Drive detection & reconnect**: When a `sourceOnly` device reconnects its
  signature file (`sync-meta.json`) carries `"sourceOnly": true`, so the
  `SignatureFileManager` can restore this flag without a DB round-trip.

### Persistence

`sourceOnly` is stored in two places intentionally:

| Location | Purpose |
|---|---|
| `Device` JPA entity (DB) | Authoritative source; used during normal runtime |
| `SyncMeta` JSON signature file | Survives re-plug of the drive before DB record is confirmed |

---

## API Endpoints

### `GET /api/devices`
Returns all `Device` records currently known to the backend (across all sync groups).
Phase 5 will add group-scoped filtering via `?groupId=`.

### `GET /api/drives`
Returns currently mounted drives/removable volumes on the host PC, as enumerated by
`DriveDetector`. Each entry includes the drive path, label (if readable), and total /
free space. This is the discovery mechanism used to identify candidate devices before
they are registered in a sync group.

---

## Hashing

All file hashing in `FileIndexer` uses a streaming 8 KB buffer loop
(`FileInputStream` + `byte[8192]`) — the file is **never** loaded into memory in its
entirety. This is a hard requirement because the tool is designed to handle large
photo and video files.
