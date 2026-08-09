package com.filesync.drive;

import com.filesync.signature.SyncMeta;

/**
 * Represents a drive's recognition status as determined by a signature scan.
 *
 * <p>A drive is <em>recognized</em> if a valid {@code .filesync/sync-meta.json} was
 * found within {@link SignatureScanner#MAX_DEPTH} levels of its root.  A drive is
 * <em>new</em> if no signature was found (it may have a sync group assigned later via
 * {@code POST /api/devices/{deviceId}/folder}).
 */
public class DriveStatus {

    public enum Status { RECOGNIZED, NEW }

    private final DriveInfo drive;
    private final Status    status;
    /** Non-null when status == RECOGNIZED. */
    private final SyncMeta  syncMeta;

    /** Constructor for a recognized drive (signature found). */
    public DriveStatus(DriveInfo drive, SyncMeta syncMeta) {
        this.drive    = drive;
        this.status   = Status.RECOGNIZED;
        this.syncMeta = syncMeta;
    }

    /** Constructor for a new/unknown drive (no signature found). */
    public DriveStatus(DriveInfo drive) {
        this.drive    = drive;
        this.status   = Status.NEW;
        this.syncMeta = null;
    }

    public DriveInfo getDrive()    { return drive; }
    public Status    getStatus()   { return status; }
    public SyncMeta  getSyncMeta() { return syncMeta; }
}
