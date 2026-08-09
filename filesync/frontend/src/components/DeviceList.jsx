import React, { useEffect } from 'react';

export default function DeviceList({ groupId, devices, onRefreshDevices, onSelectDeviceForAssignment }) {

  useEffect(() => {
    if (groupId) {
      onRefreshDevices();
    }
  }, [groupId]);

  // Badge renderer — encapsulates all status display logic
  function StatusBadge({ d }) {
    if (!d.connected) {
      return (
        <span className="clay-badge clay-badge-offline">
          Offline
        </span>
      );
    }
    if (d.status === 'NEW') {
      return (
        <span className="clay-badge clay-badge-new">
          NEW
        </span>
      );
    }
    if (d.sourceOnly) {
      return (
        <span className="clay-badge clay-badge-source">
          Source only
        </span>
      );
    }
    if ((d.lastSyncedPcStateVersion ?? 0) >= (d.currentPcStateVersion ?? 0)) {
      return (
        <span className="clay-badge clay-badge-uptodate">
          Up to date
        </span>
      );
    }
    return (
      <span className="clay-badge clay-badge-stale">
        Stale
      </span>
    );
  }

  // Count non-source-only offline devices (blocks diff/sync in parent via banner)
  const offlineBlockers = devices.filter(d => !d.connected && !d.sourceOnly);

  return (
    <div className="clay-card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <h2 style={{ margin: 0 }}>2. Connected Devices &amp; Recognition</h2>
        <button onClick={onRefreshDevices} className="clay-btn">
          🔍 Scan Drives
        </button>
      </div>

      {offlineBlockers.length > 0 && (
        <div className="clay-card-alert" style={{ marginTop: '14px', marginBottom: '16px' }}>
          ⚠️ <strong>{offlineBlockers.map(d => d.deviceLabel || d.driveLabel || d.drivePath).join(', ')}</strong>
          {offlineBlockers.length === 1 ? ' is' : ' are'} registered to this group but not connected.
          Diff generation and sync will be blocked until all target devices are plugged in.
        </div>
      )}

      {!groupId ? (
        <p style={{ color: 'var(--text-muted)' }}>Please select a sync group first.</p>
      ) : devices.length === 0 ? (
        <p style={{ color: 'var(--text-muted)' }}>No drives detected.</p>
      ) : (
        <div className="clay-table-container">
          <table className="clay-table">
            <thead>
              <tr>
                <th>Drive</th>
                <th>Label</th>
                <th>Status</th>
                <th>Sync Root Path</th>
                <th>Device ID</th>
                <th>Source Only</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {devices.map((d, index) => (
                <tr key={index} style={!d.connected ? { opacity: 0.65 } : {}}>
                  <td><strong>{d.drivePath}</strong></td>
                  <td>{d.deviceLabel || d.driveLabel || '-'}</td>
                  <td>
                    <StatusBadge d={d} />
                  </td>
                  <td>{d.syncRootPath || 'Unassigned'}</td>
                  <td>
                    <code style={{ fontSize: '11px' }}>
                      {d.deviceId ? d.deviceId.substring(0, 12) + '...' : '-'}
                    </code>
                  </td>
                  <td>
                    {d.sourceOnly ? 'YES' : 'NO'}
                  </td>
                  <td>
                    {d.status === 'NEW' ? (
                      <button
                        onClick={() => onSelectDeviceForAssignment(d)}
                        className="clay-btn clay-btn-mint"
                        style={{ padding: '6px 14px', fontSize: '12px', borderRadius: '14px' }}
                      >
                        Assign Folder
                      </button>
                    ) : d.connected ? (
                      <button
                        onClick={() => onSelectDeviceForAssignment(d)}
                        className="clay-btn clay-btn-gray"
                        style={{ padding: '6px 14px', fontSize: '12px', borderRadius: '14px' }}
                      >
                        Re-assign
                      </button>
                    ) : (
                      <button
                        disabled
                        title="Drive must be connected to re-assign"
                        className="clay-btn"
                        style={{ padding: '6px 14px', fontSize: '12px', borderRadius: '14px' }}
                      >
                        Offline
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
