# FileSync — Interview Preparation Guide

This document covers likely interview questions about the FileSync project across architecture, design decisions, Java/Spring, React, databases, and system design. Each question includes a structured answer you can adapt.

---

## Table of Contents

1. [Project Overview Questions](#1-project-overview-questions)
2. [Architecture & Design Questions](#2-architecture--design-questions)
3. [Java & Spring Boot Questions](#3-java--spring-boot-questions)
4. [Algorithms & Data Structures](#4-algorithms--data-structures)
5. [Database & Persistence Questions](#5-database--persistence-questions)
6. [Frontend & React Questions](#6-frontend--react-questions)
7. [Concurrency & Performance Questions](#7-concurrency--performance-questions)
8. [System Design Deep Dives](#8-system-design-deep-dives)
9. [Failure Modes & Edge Cases](#9-failure-modes--edge-cases)
10. [Behavioural / STAR Questions](#10-behavioural--star-questions)
11. [Quick-Fire Technical Facts](#11-quick-fire-technical-facts)

---

## 1. Project Overview Questions

### Q: Explain FileSync in one sentence for a non-technical interviewer.

**A:** FileSync is a desktop tool that automatically figures out which files are missing from which USB drive and copies them across — like a smart backup assistant for photographers who carry multiple memory cards on a trip, without needing any internet or cloud service.

---

### Q: What problem does this project solve and why is it non-trivial?

**A:** The core problem is **offline, multi-device file synchronisation** where:

- There is no central server or network — all communication is local I/O
- Devices are intermittently connected (plugged in/out), so state must survive across sessions
- Files can be large (multi-GB video files) so naive in-memory approaches will crash
- The same filename can exist with different content on different devices (conflict), which a simple copy would silently overwrite
- Devices can be reconnected to any PC, so identity must be established without user re-configuration

Each of these constraints drives a specific design decision in the codebase.

---

### Q: Walk me through a typical end-to-end session with FileSync.

**A:**
1. **User creates a Sync Group** — a named container (e.g. "Trip Photos") that groups related devices
2. **User sets a PC folder** — a local directory on the laptop that acts as the master accumulation point
3. **User plugs in USB drives** and clicks Scan Drives — the backend enumerates mounted volumes and does a shallow scan (max 3 directory levels) looking for `.filesync/sync-meta.json` signature files
4. **Auto-recognition** — drives that were previously registered are instantly matched to their database records via their `deviceId`
5. **New drives** show as NEW and the user assigns a folder and label, which writes a signature file to the drive
6. **Generate Sync Plan** — the `DiffEngine` walks every device's sync folder, SHA-256 hashes every file, and produces a plan: which files are present where, which are missing where, and which are conflicted
7. **User reviews and approves** — can deselect individual files before executing
8. **Execute Sync** — the `SyncExecutor` copies files with live progress tracking, then updates version counters on all devices

---

## 2. Architecture & Design Questions

### Q: Why did you choose a client-server architecture instead of a pure desktop app?

**A:** The Spring Boot backend serves two purposes that a pure desktop framework would complicate:

1. **Separation of concerns** — file I/O, diff computation, and progress tracking run on the JVM (good at blocking I/O and threading) while the UI is React (good at reactive state management)
2. **Swagger UI for free** — the REST API is self-documenting, which made development and debugging significantly faster
3. **Testability** — each backend service can be tested independently without a UI. The REST contract is defined explicitly in `CONTRACT.md`

The trade-off is that it requires running two processes in dev mode (though the production JAR bundles them into one).

---

### Q: What is a "Sync Group" and why does it exist as a first-class concept?

**A:** A Sync Group is a named isolation boundary. Without it, if you had two separate projects (e.g. "Trip Photos 2026" and "Work Documents"), their files would mix in the same diff computation and you would end up copying work files to camera cards.

Each Sync Group has its own:
- `PcState` version counter (tracks how many sync cycles have occurred)
- Set of `Device` records (only devices in this group participate in diffs for this group)
- Set of `FileEntry` records (the DB index of what the PC has received from this group)

This also allows multiple independent sync workflows to run using the same application instance.

---

### Q: Explain the signature file mechanism. Why write a JSON file to the drive?

**A:** The `.filesync/sync-meta.json` file is the identity card for a drive. It solves the **stateless reconnection problem**: when a drive is plugged into a PC, we need to know which group it belongs to and what its last sync state was — without any network call and without the user remembering anything.

The `SignatureScanner` does a bounded depth-first walk (max 3 levels) of each drive root looking for this file. This is intentionally shallow so we never block on walking a 500 GB photo archive.

The `sourceOnly` flag is stored here specifically because it must survive the scenario where a drive is plugged in before the DB record is confirmed — the signature file is the source of truth at reconnect time.

---

### Q: How does the `sourceOnly` flag work and why is it enforced in two places?

**A:** `sourceOnly = true` means a device contributes files to the group's pool but is never a write target. The guarantee is enforced at two independent layers:

1. **DiffEngine** — when computing `missingOn` lists, a `sourceOnly` device is explicitly excluded
2. **SyncExecutor** — even if a `missingOn` list were somehow incorrect, the executor has its own guard before any I/O begins

This double-enforcement follows the **defence-in-depth** principle. The `CONTRACT.md` file explicitly documents this as an invariant that must never be broken anywhere in the codebase.

---

### Q: How does PcState versioning work?

**A:** `PcState` is a per-group monotonic counter stored in the database. It increments whenever the PC acquires new files.

Each `Device` record stores two version numbers:
- `currentPcStateVersion` — the latest version of the group (fetched at scan time)
- `lastSyncedPcStateVersion` — the version at which this device was last synced

If `lastSynced < current`, the device shows as **Stale** in the UI. After a successful sync, the executor increments `PcState.currentStateVersion` and writes the new version into every participating device's record — both in the DB and in the `.filesync/sync-meta.json` on each drive.

---

## 3. Java & Spring Boot Questions

### Q: Why Spring Boot 3.3 with Java 21?

**A:**
- **Java 21** is the latest LTS release with virtual threads (Project Loom) and improved records/sealed classes
- **Spring Boot 3.3** gives auto-configured JPA, embedded server, and exec-maven-plugin integration that builds the frontend and bundles it into the JAR in a single `mvn package`
- **Spring Data JPA** eliminates boilerplate — `findBySyncGroupId()` is generated entirely from the method name

---

### Q: Walk me through how `DriveDetector` enumerates drives without native code.

**A:** Uses two pure-Java approaches:

```java
// Primary: FileStore enumeration (all platforms)
for (FileStore store : FileSystems.getDefault().getFileStores()) {
    String path  = resolvePath(store);
    long   total = store.getTotalSpace();
    long   free  = store.getUsableSpace();
}

// Fallback: File.listRoots() (Windows drive letters)
File[] roots = File.listRoots();
```

`FileStore.toString()` returns platform-specific strings like `"C:\ (Local Disk)"` on Windows. The `resolvePath()` helper parses the mount point from the bracketed portion. Zero-capacity pseudo-stores (`/proc`, `devtmpfs`) are filtered out.

---

### Q: How does the native folder picker dialog work from a web app?

**A:** When the user clicks "Choose Folder", a request hits `GET /api/browse-dialog`. The Spring controller calls `SwingUtilities.invokeAndWait()` to show a Java `JFileChooser` on the host PC's desktop. `invokeAndWait` blocks the HTTP thread until the user closes the dialog. The chosen path is returned as JSON. HTTP 204 is returned if the user cancels.

This works because the app runs locally — the "server" and the desktop are the same machine.

---

### Q: How is file hashing implemented and why this specific approach?

**A:** SHA-256 hashing uses a streaming 8 KB buffer:

```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] buffer = new byte[8192];
try (InputStream is = Files.newInputStream(file)) {
    while ((bytesRead = is.read(buffer)) != -1) {
        digest.update(buffer, 0, bytesRead);
    }
}
```

- **Never loads the whole file into memory** — critical for GB-size video files
- **8 KB matches OS page size and filesystem block size** — maximises throughput with minimal GC pressure
- The same chunked pattern is used consistently across `FileIndexer`, `SyncExecutor.copyChunked()`, and `computeSha256()`

---

### Q: Why is `@Transactional` used on `deleteGroup()`?

**A:** The delete cascades across four tables (FileEntry → Device → PcState → SyncGroup). Without `@Transactional`, a failure midway would leave the database inconsistent — e.g. FileEntry deleted but Device records remaining. With it, all four deletes succeed or none do (atomic operation).

---

### Q: How does `SignatureScanner` avoid walking large drive trees?

**A:** Uses `Files.walkFileTree()` with a custom `SimpleFileVisitor` that returns `SKIP_SUBTREE` when:
1. Depth exceeds `MAX_DEPTH = 3`
2. A `.filesync` signature is found (sync folders don't nest)
3. Directory name starts with `$`, or is `System Volume Information`, `Recovery`, or `boot`

`visitFileFailed` silently continues on permission errors rather than throwing.

---

## 4. Algorithms & Data Structures

### Q: Walk me through the DiffEngine algorithm.

**A:**

**Step 1 — Scan all devices:**
For each device, walk its sync root and build `Map<relativePath, ScannedFile>` with SHA-256 hashes.

**Step 2 — Collect all known paths:**
Union of all scanned paths + DB FileEntry paths (covers files only in the DB index).

**Step 3 — Classify each path:**
```
deviceHashMap = { deviceLabel -> hash } for devices that have this file

if distinctHashes > 1  → CONFLICT
else:
    presentOn = devices where hash matches
    missingOn = devices where file is absent (excluding sourceOnly)
    → FileDiff entry
```

**Time complexity:** O(D × F × H) — dominated by disk I/O, not CPU.

---

### Q: How does conflict detection work?

**A:** A conflict is detected when `new HashSet<>(deviceHashMap.values()).size() > 1` — the same relative path has two or more distinct SHA-256 hashes across devices. Conflicted files are shown separately in the UI and excluded from the sync plan; they require manual resolution.

---

### Q: How does the executor choose the copy source device?

**A:** Priority strategy in `resolveSourceDevice()`:
1. Prefer `"PC"` label device (fastest I/O, least likely to be removed)
2. Fall back to first device in `presentOnLabels` whose sync root path actually exists on disk at execution time

---

### Q: What is the executor-level conflict resolution?

**A:** If the executor finds the target already has a file at the destination path with a **different hash**, it renames the incoming file rather than overwriting: `photo.jpg` → `photo (from SD Card A).jpg`. Both versions are preserved; no data is silently destroyed.

---

## 5. Database & Persistence Questions

### Q: Why SQLite instead of PostgreSQL or H2?

**A:** FileSync is a local desktop tool with a single user. SQLite is correct because:
- **Zero configuration** — no database server needed
- **Single file** — `filesync.db` can be deleted to reset state
- **Sufficient performance** — hundreds of records, queries in microseconds
- **Wide tooling support** — DB Browser for SQLite, DBeaver, etc.

H2 was considered but SQLite is more portable for end-user deployment.

---

### Q: Describe the data model.

**A:**
```
SyncGroup (id PK, label, createdAt)
  ├── PcState (groupId PK/FK, currentStateVersion)
  ├── Device (id PK, label, syncRootPath, syncGroupId FK,
  │           lastSyncedPcStateVersion, sourceOnly, lastSeen)
  └── FileEntry (hash + syncGroupId composite PK,
                 relativePath, sizeBytes, sourceDeviceId, createdAt)
```

`FileEntry` uses a composite PK `(hash, syncGroupId)` — the same content hash can appear in multiple groups but only once per group. This prevents duplicate DB entries when the same file is synced multiple times.

---

### Q: How is cascading delete handled?

**A:** Manually cascaded in dependency order within a single `@Transactional` method: FileEntry → Device → PcState → SyncGroup. Physical files on drives are never touched; only DB records are removed.

---

## 6. Frontend & React Questions

### Q: How is state managed across the four main components?

**A:** State is lifted to `App.jsx` (single source of truth): `selectedGroup`, `devices`, `assigningDevice`. Components receive state via props and communicate back via callback props (`onRefreshDevices`, `onSelectGroup`, etc.). Prop drilling is acceptable here because the tree is only 2 levels deep — adding Redux/Zustand would be over-engineering.

---

### Q: How does the Vite dev proxy work?

**A:** `vite.config.js` proxies all `/api/*` requests to `http://localhost:8080`. The frontend uses relative URLs (`/api/groups`) which work identically in dev (proxied) and production (same-origin JAR). No CORS configuration needed.

---

### Q: How does the real-time progress bar work?

**A:** Client-side polling every 200 ms via `setInterval`. The backend `SyncProgressTracker` maintains an in-memory `ConcurrentHashMap<groupId, SyncProgress>` updated on every 8 KB chunk written. The poll interval is cleared when the sync POST returns. Polling is chosen over WebSockets/SSE because sync operations are short-lived and localhost latency is negligible.

---

## 7. Concurrency & Performance Questions

### Q: Is the sync executor thread-safe?

**A:** Not explicitly for concurrent group syncs. The UI prevents this via `syncing === true` state guard. `SyncProgressTracker` uses `ConcurrentHashMap` for group-isolated progress. To harden: add a per-groupId `ReentrantLock` in the executor.

---

### Q: Memory footprint for a large diff (10,000 files, 5 devices)?

**A:** ~20–30 MB heap. Each `ScannedFile` = relativePath string + 64-char hash ≈ 300 bytes. 10,000 × 5 = 50,000 objects ≈ 15 MB. The design never loads file content into memory — only paths and hash strings.

---

### Q: Why 8 KB specifically for the I/O buffer?

**A:** Matches OS page size (4 KB) and common filesystem block sizes (ext4/NTFS/exFAT use 4–8 KB clusters). The buffer is allocated once and reused for the entire file — zero GC overhead regardless of file size. Smaller buffers (512 B) make too many syscalls; larger (1 MB) waste heap with no proportional throughput gain on sequential I/O.

---

## 8. System Design Deep Dives

### Q: How would you scale this to work over WiFi?

**A:** Key changes:
1. **Transport** — replace `Files.copy()` with HTTP/gRPC transfer; each device runs a FileSync agent
2. **Discovery** — use mDNS (`javax.jmdns`) instead of drive letter enumeration
3. **Auth** — shared secret or certificate pairing (physical USB access is the current "auth")
4. **Concurrency** — parallel `CompletableFuture` transfers instead of single-threaded USB I/O

The `DiffEngine` algorithm, `SyncGroup`/`Device` model, signature file concept, and `sourceOnly` semantics all remain unchanged.

---

### Q: How would you handle directories with millions of files?

**A:**
1. **Incremental indexing** — store file index in DB; use `mtime + size` as dirty flag; only re-hash changed files (rsync approach)
2. **Merkle tree** — directory hash = hash of children's hashes; skip unchanged subtrees entirely
3. **OS file watching** — `WatchService` (inotify wrapper) maintains a live dirty set; diff only re-hashes dirty files

---

### Q: How would you add in-app conflict resolution?

**A:**
- **Backend:** `POST /api/diff/{groupId}/resolve-conflict` with `{ relativePath, winnerDeviceId }`; executor copies winner version to all others
- **Frontend:** Radio buttons per conflict row in `SyncPlanTable` showing device label + partial hash
- **Data model:** `ConflictResolution` table with audit trail: `(groupId, relativePath, resolvedAt, winnerDeviceId, winnerHash)`

---

## 9. Failure Modes & Edge Cases

### Q: What happens if a drive is unplugged mid-sync?

**A:** `copyChunked()` uses try-with-resources. `IOException` from a disconnected source is caught per-file; the executor logs it and continues to the next file. The partially-written destination file remains on disk but is not added to the DB index. The next diff run will detect it as incomplete and retry.

**Improvement:** Delete partially-written destination files on `IOException` to keep the filesystem clean.

---

### Q: What if the PC folder path no longer exists?

**A:** `DiffEngine` checks `Files.exists(root)` before scanning. If the PC folder is gone, the PC device scan map is empty, making every file appear "missing on PC". Copy attempts will fail with `IOException`. Better: validate PC device sync root existence before starting the diff and return a descriptive error to the UI.

---

### Q: What if a file changes between diff and execute?

**A:** The executor-level conflict rename handles target changes. But if the **source** file changes, the executor copies the new version without detecting the mismatch. The next diff will surface the discrepancy. **Ideal fix:** Re-hash the source file immediately before copying and abort if the hash differs from the approved hash.

---

## 10. Behavioural / STAR Questions

### Q: Tell me about a technical challenge you faced.

**Situation:** Push to GitHub failed with HTTP 408 timeout. Repo was 1.91 GiB.

**Task:** Reduce size to pushable without losing source code.

**Action:**
1. Used `git ls-files | grep "large|target"` to identify culprits: 1 GB, 500 MB, 300 MB test binary files + Maven `target/` directory all tracked in git
2. Wrote comprehensive `.gitignore` covering `target/`, `*.db`, test data folders, IDE files
3. `git rm --cached` to untrack large files without deleting from disk
4. `git checkout --orphan clean-main` to create a fresh root commit with no blob history
5. `git reflog expire --expire=now --all` + `git gc --prune=now --aggressive` to purge the 1.91 GiB object store
6. Force-pushed the clean history

**Result:** 1.91 GiB → 84.90 KiB. Push succeeded in seconds.

---

### Q: What would you do differently if starting over?

**A:**
1. **`.gitignore` from day one** — binary test files in git created a painful cleanup
2. **Incremental file indexing** — recomputing SHA-256 for every file on every diff is wasteful for repeat runs
3. **Async diff with SSE progress** — current sync blocks the HTTP thread; `CompletableFuture` + Server-Sent Events would be more robust for large libraries
4. **Structured logging** — replace `System.err.println()` with SLF4J + logback
5. **Delete partial writes on failure** — leave filesystem clean after IOExceptions

---

## 11. Quick-Fire Technical Facts

| Question | Answer |
|---|---|
| Backend port | 8080 |
| Frontend dev port | 3000 |
| Database location | `filesync/backend/filesync.db` |
| Hash algorithm | SHA-256 |
| I/O buffer size | 8 KB (8192 bytes) |
| Signature file | `.filesync/sync-meta.json` |
| Max scan depth | 3 directory levels |
| Progress poll interval | 200 ms |
| PC device label | `"PC"` (hardcoded) |
| Conflict rename format | `stem (from DeviceLabel).ext` |
| Spring Boot version | 3.3.1 |
| Java version | 21 |
| React version | 19 |
| SQLite JDBC version | 3.46.0.0 |
| sourceOnly enforced in | DiffEngine AND SyncExecutor |
| PcState counter type | Monotonically increasing long |
| Group delete — physical files? | Never touched — DB records only |
| Native folder dialog | Java Swing JFileChooser via SwingUtilities.invokeAndWait |
| API docs URL | /swagger-ui.html |
| Frontend build tool | Vite 8 |

---

*Focus on the WHY behind each design decision — interviewers value reasoning over memorisation.*
