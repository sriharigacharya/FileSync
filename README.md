# ⚡ FileSync — Offline-First Multi-Device USB Sync Orchestrator

> **FileSync** is a local-network-free file synchronisation tool designed for photographers, travellers, and anyone who needs to keep files in sync across multiple USB drives and a PC — **without any cloud or internet dependency.**

---

## Table of Contents

1. [What It Does](#what-it-does)
2. [How It Works — Architecture Overview](#how-it-works--architecture-overview)
3. [Core Concepts](#core-concepts)
4. [Tech Stack](#tech-stack)
5. [Prerequisites](#prerequisites)
6. [Running the Application](#running-the-application)
7. [Step-by-Step Usage Guide](#step-by-step-usage-guide)
8. [Understanding Device Statuses](#understanding-device-statuses)
9. [The sourceOnly Flag](#the-sourceonly-flag)
10. [Conflict Detection and Resolution](#conflict-detection-and-resolution)
11. [Signature Files](#signature-files-sync-metajson)
12. [REST API Reference](#rest-api-reference)
13. [Database](#database)
14. [Project Structure](#project-structure)
15. [Troubleshooting](#troubleshooting)

---

## What It Does

FileSync solves the problem of keeping files in sync across **multiple USB drives (SD cards, pendrives, external HDDs)** and a **PC folder** — entirely offline, with no cloud service required.

**Primary use case:** A photographer on a trip carries multiple memory cards and a backup drive. At the end of each day, they plug each device into their laptop and FileSync automatically figures out which photos are missing from which device and copies them — preserving directory structure, skipping already-identical files, and flagging genuine conflicts.

**Key capabilities:**

- 🗂 **Sync Group management** — Organise devices into named groups (e.g., *"Trip Photos 2026"*)
- 🔌 **Auto-recognition** — Plugging a drive in again is instantly detected; no re-configuration needed
- 🔍 **SHA-256 diff engine** — Every file is compared by content hash, not just name or timestamp
- ✅ **User-approved sync plan** — See exactly which files will be copied where before anything is written
- 📊 **Live progress tracking** — Real-time per-file and byte-level progress bar during sync
- ⚠️ **Conflict detection** — Same filename, different content across devices → flagged, not silently overwritten
- 🚫 **Source-only devices** — Mark a device as read-only; FileSync will read from it but never write to it
- 💾 **Chunked I/O** — All file copies use an 8 KB streaming buffer; large video files never load fully into RAM

---

## How It Works — Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Browser UI (React)                       │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐ │
│  │SyncGroupSetup│ │  DeviceList  │ │      SyncPlanTable       │ │
│  │  - Groups    │ │  - Scan      │ │  - Generate Diff         │ │
│  │  - PC Folder │ │  - Assign    │ │  - Review & Select Files │ │
│  └──────────────┘ └──────────────┘ │  - Execute Sync          │ │
│                                    │  - Live Progress Bar     │ │
│                                    └──────────────────────────┘ │
└───────────────────────────────┬─────────────────────────────────┘
                                │ HTTP / REST (port 8080)
┌───────────────────────────────▼─────────────────────────────────┐
│              Spring Boot Backend (Java 21)                      │
│                                                                 │
│  DeviceController ──► DriveDetector ──► SignatureScanner        │
│       │                                      │                  │
│       │                               reads sync-meta.json      │
│       │                               from drive roots          │
│       │                                                         │
│  GroupController ──► SyncGroup / Device / PcState (JPA)         │
│                                                                 │
│  DiffController  ──► DiffEngine ──► FileIndexer (SHA-256 scan)  │
│                                                                 │
│  SyncController  ──► SyncExecutor ──► chunked file copy         │
│                       │                                         │
│                       └──► SyncProgressTracker (polling)        │
│                                                                 │
│  SignatureFileManager — writes/reads .filesync/sync-meta.json   │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │   SQLite Database     │
                    │   (filesync.db)       │
                    │                       │
                    │  - SyncGroup          │
                    │  - Device             │
                    │  - FileEntry          │
                    │  - PcState            │
                    └───────────────────────┘
```

**Data flow for a sync session:**

1. User plugs in USB drives and clicks **Scan Drives** → backend enumerates file system volumes via `DriveDetector`
2. Each drive root is shallow-scanned (max 3 levels deep) by `SignatureScanner` looking for `.filesync/sync-meta.json`
3. Recognised drives are matched to `Device` records in SQLite; unrecognised drives show as **NEW**
4. User clicks **Generate Sync Plan** → `DiffEngine` walks each device's sync folder, hashes every file with SHA-256, and computes which files are present/missing on which device
5. The resulting `SyncPlan` is displayed: per-file checkboxes, present-on badges, missing-on badges, conflict warnings
6. User reviews, deselects any files they don't want copied, and clicks **Execute Sync**
7. `SyncExecutor` copies files device-to-device using 8 KB chunked I/O, updates `PcState` version counter, and writes updated `sync-meta.json` back to each drive

---

## Core Concepts

| Concept | Description |
|---|---|
| **Sync Group** | A named collection of devices that should all share the same files. Each group has its own isolated file index. |
| **Device** | One registered drive (USB stick, SD card, external HDD, or the PC folder). Stored in DB with a stable UUID. |
| **PC Device** | A special Device labelled `"PC"` that represents a folder on the local PC. Set via "Set PC Folder". |
| **PcState Version** | A monotonically-increasing integer. Bumped every time the PC receives new files. Used to detect which drives are stale. |
| **Sync Root Path** | The specific folder on a device that FileSync watches. Files outside this folder are ignored. |
| **Signature File** | A `.filesync/sync-meta.json` written inside a device's sync root folder. Enables auto-recognition on re-plug. |
| **Diff / Sync Plan** | The computed set of files that need to be copied to bring all devices into agreement. |
| **sourceOnly** | A flag on a Device that prevents FileSync from ever writing to it. It contributes files but is never a copy target. |

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java 21, Spring Boot 3.3.1, Spring Data JPA |
| **Database** | SQLite (via `sqlite-jdbc 3.46.0` + Hibernate Community Dialects) |
| **Frontend** | React 19, Vite 8, Vanilla CSS |
| **API Docs** | SpringDoc OpenAPI / Swagger UI (auto-generated) |
| **Build** | Maven (backend), npm (frontend) |

---

## Prerequisites

| Tool | Minimum Version | Notes |
|---|---|---|
| **Java JDK** | 21 | Must be on your `PATH` |
| **Maven** | 3.9+ | Or use the `mvnw` wrapper if present |
| **Node.js** | 18+ | Required for the frontend build step |
| **npm** | 9+ | Comes with Node.js |

---

## Running the Application

### Development Mode (Recommended for Dev)

Run backend and frontend independently so both hot-reload on changes.

**Terminal 1 — Backend:**
```bash
cd filesync/backend
mvn spring-boot:run
```
Backend starts on **http://localhost:8080**

**Terminal 2 — Frontend:**
```bash
cd filesync/frontend
npm install        # first time only
npm run dev
```
Frontend starts on **http://localhost:3000**
All `/api/*` requests are proxied to `localhost:8080` automatically (configured in `vite.config.js`)

Open **http://localhost:3000** in your browser.

---

### Production Build (Single JAR)

The Maven build compiles the React app and bundles it inside the Spring Boot JAR as static resources.

```bash
cd filesync/backend
mvn clean package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

Open **http://localhost:8080** in your browser.

> **Note:** The first `mvn package` automatically runs `npm run build` inside the `frontend/` directory, so Node.js and npm must be available during the build.

---

## Step-by-Step Usage Guide

### Step 1 — Create or Select a Sync Group

When you open the app you will see the **Sync Group & PC Setup** panel at the top.

- **First time:** Type a name (e.g. `Trip Photos 2026`) in the **Create New Group** field and click **Create**
- **Returning user:** Select your group from the **Select Existing Group** dropdown

A sync group isolates a set of devices. Files from one group are never mixed with another.

---

### Step 2 — Set the PC Folder

After selecting a group, the **Set PC Folder** form appears.

- Enter the **absolute path** to the folder on your PC (e.g. `C:\Users\YourName\TripPhotos`)
- Click **Set PC Folder**

This creates a special `PC` device record and writes a `.filesync/sync-meta.json` signature file into that folder so the PC is tracked like any other device.

---

### Step 3 — Plug In Devices & Scan Drives

- Plug your USB drives / SD cards into the PC
- In the **Connected Devices & Recognition** panel, click **Scan Drives**

The backend enumerates all mounted volumes and scans each drive root up to 3 levels deep for `.filesync/sync-meta.json` signature files.

**What you will see:**

| Status | Meaning |
|---|---|
| Recognised | Previously registered; auto-matched to their DB record |
| NEW | Drive with no signature file; needs to be assigned |
| Offline (greyed) | Registered in DB but not currently plugged in |

> If a non-source-only device shows as Offline, diff generation and sync will be blocked until it is connected.

---

### Step 4 — Assign Folders to Devices

For any drive showing status **NEW**, click **Assign Folder**.

The **Folder Picker** panel opens showing the drive's directory tree. You can:

- Click through subdirectories to navigate
- Click **Choose Folder** to open a native OS folder picker dialog (Java Swing `JFileChooser`)
- Manually type a path in the text field

Configure the device:

| Field | Description |
|---|---|
| **Chosen Folder Path** | The folder on this drive that FileSync will watch. All sync operations are relative to this root. |
| **Device Label** | A friendly name (e.g. `SD Card A`, `Backup Drive`). Shown in the sync plan. |
| **Source Only** | Tick this if the drive should only contribute files (read), never receive files (write). |

Click **Confirm & Write Signature** to save. This:
1. Creates or updates a `Device` record in SQLite
2. Writes `.filesync/sync-meta.json` into the chosen folder on the drive

For already-registered drives, click **Re-assign** to update the folder path or label.

---

### Step 5 — Generate the Sync Plan

In the **Sync Plan & Execution** panel, click **Generate Sync Plan**.

> **Requirement:** All non-source-only devices registered to the group must be connected. If any are offline, an error banner is shown.

The backend `DiffEngine` will:
1. Walk each device's sync root folder and compute a SHA-256 hash for every file (streaming 8 KB buffer)
2. Compare hashes across all devices to find which files are missing where
3. Detect conflicts (same relative path, different hash on different devices)

The results appear as a table:

| Column | Description |
|---|---|
| Checkbox | Deselect files you do not want copied in this session |
| Relative Path | File path relative to each device's sync root |
| Present On | Green badges — devices that already have this file |
| Missing On | Orange badges — devices missing this file |
| SHA-256 Hash | First 16 characters of the content hash |

Conflicts are shown in a separate red section above the table and excluded from the sync plan.

---

### Step 6 — Review & Execute the Sync

1. Review the file list; uncheck any files you want to skip
2. Click **Execute Sync**

A **progress bar** appears showing:
- Percentage complete
- Current file being copied (`Copying file N of M: filename`)
- Bytes copied vs. total bytes

When complete, a green summary shows:
- **Copied** — files actually written
- **Skipped (Identical)** — files already present with the same hash
- **Group State Version** — the new PcState version after this sync

**Under the hood:**
1. For each approved file, `SyncExecutor` picks the best source device (prefers PC)
2. Copies the file to all target devices missing it, using 8 KB chunked I/O with live progress
3. If a target already has a file with a different hash, it is written as `filename (from DeviceLabel).ext`
4. Updates `lastSyncedPcStateVersion` on every device and rewrites their `sync-meta.json` files

---

## Understanding Device Statuses

| Badge | Meaning |
|---|---|
| Up to date | Device's `lastSyncedPcStateVersion` matches the current group version |
| Stale | Device was synced before but the PC has received newer files since |
| Source only | Device is configured as read-only |
| NEW | Drive detected but not yet registered to any sync group |
| Offline | Device is registered in the DB but not currently plugged in |

---

## The sourceOnly Flag

A device marked **Source Only** participates in sync in a special way:

- Its files **are** indexed and included in the group's file pool (other devices receive them)
- It **never** appears in the `missingOn` list — FileSync will not try to write to it
- The sync executor will skip it as a copy destination

**Use case:** A camera memory card you want to import into all other drives without FileSync ever modifying its contents.

The `sourceOnly` flag is persisted in two places:
1. The `Device` record in SQLite (authoritative)
2. The `.filesync/sync-meta.json` signature file on the drive (survives re-plug before DB confirmation)

---

## Conflict Detection and Resolution

A conflict is raised when two or more devices have a file at the **same relative path** but with **different SHA-256 hashes**.

**At diff time:** Conflicted files appear in the red Conflicts section. They are excluded from the normal sync plan and must be resolved manually.

**At executor time (safety net):** If a target file already exists with a different hash when the copy runs, the incoming file is renamed:
```
original_filename (from SourceDeviceLabel).ext
```
This prevents any data from being silently overwritten.

---

## Signature Files (`sync-meta.json`)

Every sync-root folder gets a hidden signature directory:
```
<sync-root>/
└── .filesync/
    └── sync-meta.json
```

**Example contents:**
```json
{
  "syncGroupId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "syncGroupLabel": "Trip Photos 2026",
  "deviceId": "yyyyyyyy-yyyy-yyyy-yyyy-yyyyyyyyyyyy",
  "deviceLabel": "SD Card A",
  "syncRootPath": "D:\\Photos",
  "createdAt": "2026-08-09T10:00:00Z",
  "lastSyncedPcStateVersion": 3,
  "sourceOnly": false
}
```

This file enables **offline auto-recognition**. When a drive is plugged in, FileSync scans for this file without any network communication. As long as the signature file exists, the drive is automatically matched to its group and device record.

> Deleting a group from the app does **not** delete signature files from drives. Physical files on drives are never touched by a group deletion — only database records are removed.

---

## REST API Reference

Swagger UI is available at **http://localhost:8080/swagger-ui.html**.

### Sync Groups

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/groups` | List all sync groups |
| `POST` | `/api/groups` | Create a new sync group `{ "label": "..." }` |
| `DELETE` | `/api/groups/{groupId}` | Delete a group and all its DB records |
| `POST` | `/api/groups/{groupId}/pc-folder` | Set the PC sync folder `{ "path": "..." }` |

### Devices & Drives

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/devices?groupId={id}` | List all devices for a group (includes offline devices) |
| `GET` | `/api/drives` | List all currently mounted drives/volumes on the PC |
| `POST` | `/api/devices/{deviceId}/folder` | Assign a sync folder to a device |
| `GET` | `/api/browse?path={path}` | List immediate sub-directories of a path |
| `GET` | `/api/browse-dialog` | Open a native OS folder picker dialog; returns chosen path |

### Diff & Sync

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/diff?groupId={id}` | Compute sync plan `{ "deviceIds": [...] }` |
| `POST` | `/api/sync?groupId={id}` | Execute sync `{ "approvedHashes": [...], "deviceIds": [...] }` |
| `GET` | `/api/sync/progress?groupId={id}` | Poll live sync progress |

---

## Database

FileSync uses an embedded **SQLite** database stored at:
```
filesync/backend/filesync.db
```

No setup or external database server is required. The database is created automatically on first launch.

**Tables:**

| Table | Purpose |
|---|---|
| `sync_group` | Named sync groups with UUIDs and labels |
| `device` | Registered devices with sync root paths and state versions |
| `file_entry` | Index of files the PC has received, with hashes and source info |
| `pc_state` | Current monotonic version counter per sync group |

---

## Project Structure

```
TripSync/
└── filesync/
    ├── CONTRACT.md              <- API contracts & behavioural invariants
    ├── backend/
    │   ├── pom.xml              <- Maven build (also triggers frontend build)
    │   ├── filesync.db          <- SQLite database (auto-created)
    │   └── src/main/java/com/filesync/
    │       ├── FilesyncApplication.java
    │       ├── controller/
    │       │   ├── DeviceController.java    <- /api/devices, /api/drives, /api/browse
    │       │   ├── GroupController.java     <- /api/groups
    │       │   ├── DiffController.java      <- /api/diff
    │       │   ├── SyncController.java      <- /api/sync
    │       │   └── dto/                     <- Request/Response DTOs
    │       ├── drive/
    │       │   ├── DriveDetector.java       <- Enumerates mounted volumes
    │       │   ├── FolderBrowser.java       <- Folder tree browsing
    │       │   └── SignatureScanner.java    <- Finds sync-meta.json on drives
    │       ├── indexer/
    │       │   ├── FileIndexer.java         <- SHA-256 file scanning
    │       │   ├── DiffEngine.java          <- Computes sync plan
    │       │   ├── SyncExecutor.java        <- Executes file copies
    │       │   └── SyncProgressTracker.java <- Live progress state
    │       ├── model/                       <- JPA entity classes
    │       ├── repository/                  <- Spring Data JPA repositories
    │       └── signature/
    │           └── SignatureFileManager.java <- Reads/writes sync-meta.json
    └── frontend/
        ├── package.json
        ├── vite.config.js               <- Dev server + /api proxy to :8080
        └── src/
            ├── App.jsx                  <- Root component, state orchestration
            ├── components/
            │   ├── SyncGroupSetup.jsx   <- Group management, PC folder setup
            │   ├── DeviceList.jsx       <- Drive list, status badges, scan
            │   ├── FolderPicker.jsx     <- Folder assignment, native dialog
            │   └── SyncPlanTable.jsx    <- Diff display, file selection, sync
            └── index.css / App.css      <- Clay-style design system
```

---

## Troubleshooting

**"No drives detected"**
- Click **Scan Drives** after plugging in your devices
- Ensure the drives are fully mounted and accessible in File Explorer

**"Cannot generate diff: device(s) not connected"**
- All non-source-only devices registered to the group must be physically plugged in
- Plug in the missing drive and click **Scan Drives** again

**"Error setting PC folder"**
- Ensure the path you entered exists and is a directory
- Use the full absolute path (e.g. `C:\Users\YourName\Photos`)

**Signature file not found after re-plugging a drive**
- The `.filesync` folder is hidden — enable "Show hidden files" in your file explorer
- Ensure you assigned the same folder on the drive (not the drive root if the sync root was a subfolder)

**Sync blocked by a conflict**
- Conflicts must be resolved manually. Decide which version is correct, delete or rename the unwanted version on the drive, then re-run the sync plan generation.

---

*FileSync is a self-contained local tool — all data stays on your machine and your drives. No accounts, no cloud, no internet required.*
