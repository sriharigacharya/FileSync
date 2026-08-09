import React, { useState } from 'react';

export default function SyncPlanTable({ groupId, connectedDeviceIds, devices }) {
  const [plan, setPlan] = useState(null);
  const [selectedFiles, setSelectedFiles] = useState({});
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const generateDiff = async () => {
    if (!groupId) return;

    // Block if any non-source-only registered device is offline
    const offlineBlockers = (devices || []).filter(d => !d.connected && !d.sourceOnly);
    if (offlineBlockers.length > 0) {
      const names = offlineBlockers.map(d => d.deviceLabel || d.driveLabel || d.drivePath).join(', ');
      setErrorMsg(`Cannot generate diff: device(s) not connected — ${names}. Please plug in the device and scan drives.`);
      return;
    }

    setLoading(true);
    setErrorMsg('');
    try {
      const res = await fetch(`/api/diff?groupId=${groupId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ deviceIds: connectedDeviceIds || [] }),
      });
      if (res.ok) {
        const data = await res.json();
        setPlan(data);

        // Default all files to selected
        const initialSelected = {};
        if (data.files) {
          data.files.forEach((f) => {
            initialSelected[f.relativePath] = true;
          });
        }
        setSelectedFiles(initialSelected);
      } else {
        const text = await res.text();
        setErrorMsg(`Failed to generate diff: ${text}`);
      }
    } catch (err) {
      setErrorMsg('Error generating diff');
    } finally {
      setLoading(false);
    }
  };

  const toggleFile = (relPath) => {
    setSelectedFiles((prev) => ({
      ...prev,
      [relPath]: !prev[relPath],
    }));
  };

  const toggleAll = (checked) => {
    if (!plan || !plan.files) return;
    const updated = {};
    plan.files.forEach((f) => {
      updated[f.relativePath] = checked;
    });
    setSelectedFiles(updated);
  };

  const [syncing, setSyncing] = useState(false);
  const [syncProgress, setSyncProgress] = useState(null);
  const [syncResult, setSyncResult] = useState(null);

  const executeSync = async () => {
    if (!groupId || !plan || !plan.files) return;

    // Block if any non-source-only registered device is offline
    const offlineBlockers = (devices || []).filter(d => !d.connected && !d.sourceOnly);
    if (offlineBlockers.length > 0) {
      const names = offlineBlockers.map(d => d.deviceLabel || d.driveLabel || d.drivePath).join(', ');
      setErrorMsg(`Cannot sync: device(s) not connected — ${names}. Please plug in the device and scan drives.`);
      return;
    }

    // Collect approved hashes for checked files
    const approvedHashes = plan.files
      .filter((f) => selectedFiles[f.relativePath])
      .map((f) => f.hash);

    if (approvedHashes.length === 0) {
      alert('Please select at least one file to sync.');
      return;
    }

    setSyncing(true);
    setErrorMsg('');
    setSyncResult(null);
    setSyncProgress(null);

    // Start progress polling interval
    const pollInterval = setInterval(async () => {
      try {
        const res = await fetch(`/api/sync/progress?groupId=${groupId}`);
        if (res.ok) {
          const progData = await res.json();
          setSyncProgress(progData);
        }
      } catch (err) {
        // Ignore polling errors
      }
    }, 200);

    try {
      const res = await fetch(`/api/sync?groupId=${groupId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          approvedHashes,
          deviceIds: connectedDeviceIds || [],
        }),
      });

      clearInterval(pollInterval);

      if (res.ok) {
        const resultData = await res.json();
        setSyncResult(resultData);
        // Refresh final progress state
        const finalProg = await fetch(`/api/sync/progress?groupId=${groupId}`);
        if (finalProg.ok) {
          setSyncProgress(await finalProg.json());
        }
      } else if (res.status === 409) {
        const conflictText = await res.text();
        setErrorMsg(`Conflict: ${conflictText}`);
      } else {
        const text = await res.text();
        setErrorMsg(`Sync failed: ${text}`);
      }
    } catch (err) {
      clearInterval(pollInterval);
      setErrorMsg('Error executing sync');
    } finally {
      setSyncing(false);
    }
  };

  const formatBytes = (bytes) => {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i];
  };

  return (
    <div className="clay-card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
        <h2 style={{ margin: 0 }}>3. Sync Plan & Execution</h2>
        <div style={{ display: 'flex', gap: '10px' }}>
          <button
            onClick={generateDiff}
            disabled={!groupId || loading || syncing}
            className="clay-btn"
          >
            {loading ? '🔄 Scanning & Generating...' : '⚡ Generate Sync Plan'}
          </button>
          {plan && plan.files && plan.files.length > 0 && (
            <button
              onClick={executeSync}
              disabled={syncing}
              className="clay-btn clay-btn-mint"
            >
              {syncing ? '⌛ Syncing...' : '🚀 Execute Sync'}
            </button>
          )}
        </div>
      </div>

      {errorMsg && (
        <div className="clay-card-alert" style={{ marginTop: '16px', background: '#fce3e1', color: '#7f1d1d' }}>
          ⚠️ {errorMsg}
        </div>
      )}

      {/* Progress UI */}
      {(syncing || (syncProgress && syncProgress.status === 'IN_PROGRESS')) && (
        <div style={{
          marginTop: '20px', borderRadius: '20px', padding: '16px 20px',
          background: '#e4effa', boxShadow: 'var(--clay-shadow-raised)'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontWeight: '700', color: '#1c456b' }}>
            <span>Syncing Files...</span>
            <span>{(syncProgress?.percent || 0).toFixed(1)}%</span>
          </div>
          <div style={{
            height: '16px', background: '#d5e4f3', borderRadius: '12px',
            boxShadow: 'var(--clay-shadow-input)', overflow: 'hidden', marginBottom: '10px'
          }}>
            <div
              style={{
                height: '100%',
                width: `${syncProgress?.percent || 0}%`,
                background: 'var(--accent-blue)',
                boxShadow: 'var(--clay-shadow-raised)',
                borderRadius: '12px',
                transition: 'width 0.2s ease',
              }}
            />
          </div>
          <div style={{ fontSize: '13px', color: '#2b5b84', display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px' }}>
            <span>
              {syncProgress?.currentFile
                ? `Copying file ${ (syncProgress?.filesCopied || 0) + 1 } of ${ syncProgress?.totalFiles || 0 }: ${syncProgress.currentFile}`
                : `Preparing sync (${syncProgress?.filesCopied || 0} / ${syncProgress?.totalFiles || 0} files completed)...`}
            </span>
            <span>
              {formatBytes(syncProgress?.bytesCopied || 0)} / {formatBytes(syncProgress?.totalBytes || 0)}
            </span>
          </div>
        </div>
      )}

      {/* Execution Summary Result */}
      {syncResult && (
        <div style={{
          marginTop: '20px', borderRadius: '20px', padding: '16px 20px',
          background: '#e2f7ed', boxShadow: 'var(--clay-shadow-raised)', color: '#13583a'
        }}>
          <h4 style={{ color: '#13583a', margin: '0 0 6px 0', fontSize: '16px' }}>✅ Sync Execution Complete!</h4>
          <p style={{ margin: 0, fontSize: '14px', fontWeight: '600' }}>
            <strong>Copied:</strong> {syncResult.copiedCount} file(s) | <strong>Skipped (Identical):</strong> {syncResult.skippedCount} file(s) | <strong>Group State Version:</strong> v{syncResult.pcStateVersion}
          </p>
        </div>
      )}

      {plan && (
        <div style={{ marginTop: '24px' }}>
          {/* Conflicts Section */}
          {plan.conflicts && plan.conflicts.length > 0 && (
            <div className="clay-card-conflict" style={{ marginBottom: '24px' }}>
              <h3 style={{ color: '#7f1d1d', marginTop: 0 }}>⚠️ Conflicts Detected ({plan.conflicts.length})</h3>
              <p style={{ fontSize: '13px', color: '#991b1b', marginBottom: '12px' }}>
                The following files have different contents across devices and require resolution:
              </p>
              <div className="clay-table-container">
                <table className="clay-table">
                  <thead>
                    <tr>
                      <th>Relative Path</th>
                      <th>Device Content Hashes</th>
                    </tr>
                  </thead>
                  <tbody>
                    {plan.conflicts.map((c, idx) => (
                      <tr key={idx}>
                        <td style={{ fontWeight: '700', color: '#991b1b' }}>
                          {c.relativePath}
                        </td>
                        <td>
                          {Object.entries(c.devices || {}).map(([devLabel, hash]) => (
                            <div key={devLabel} style={{ fontSize: '12px', marginBottom: '2px' }}>
                              <strong>{devLabel}:</strong> <code>{hash.substring(0, 16)}...</code>
                            </div>
                          ))}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Files Array Section */}
          <h3>Files to Sync ({plan.files ? plan.files.length : 0})</h3>
          {!plan.files || plan.files.length === 0 ? (
            <p style={{ color: 'var(--text-muted)' }}>No sync candidate files found.</p>
          ) : (
            <div className="clay-table-container">
              <table className="clay-table">
                <thead>
                  <tr>
                    <th style={{ width: '44px', textAlign: 'center' }}>
                      <input
                        type="checkbox"
                        checked={Object.values(selectedFiles).length > 0 && Object.values(selectedFiles).every(Boolean)}
                        onChange={(e) => toggleAll(e.target.checked)}
                        className="clay-checkbox"
                      />
                    </th>
                    <th>Relative Path</th>
                    <th>Present On</th>
                    <th>Missing On</th>
                    <th>SHA-256 Hash</th>
                  </tr>
                </thead>
                <tbody>
                  {plan.files.map((f, idx) => (
                    <tr key={idx} className={selectedFiles[f.relativePath] ? 'row-selected' : ''}>
                      <td style={{ textAlign: 'center' }}>
                        <input
                          type="checkbox"
                          checked={!!selectedFiles[f.relativePath]}
                          onChange={() => toggleFile(f.relativePath)}
                          className="clay-checkbox"
                        />
                      </td>
                      <td style={{ fontWeight: '600' }}>
                        {f.relativePath}
                      </td>
                      <td>
                        {f.presentOn && f.presentOn.length > 0 ? (
                          f.presentOn.map((lbl) => (
                            <span key={lbl} className="clay-badge clay-badge-uptodate" style={{ marginRight: '6px', fontSize: '11px' }}>
                              {lbl}
                            </span>
                          ))
                        ) : (
                          <span style={{ color: 'var(--text-muted)', fontSize: '12px' }}>None</span>
                        )}
                      </td>
                      <td>
                        {f.missingOn && f.missingOn.length > 0 ? (
                          f.missingOn.map((lbl) => (
                            <span key={lbl} className="clay-badge clay-badge-stale" style={{ marginRight: '6px', fontSize: '11px' }}>
                              {lbl}
                            </span>
                          ))
                        ) : (
                          <span className="clay-badge clay-badge-uptodate" style={{ fontSize: '11px' }}>Fully synced</span>
                        )}
                      </td>
                      <td>
                        <code>
                          {f.hash ? f.hash.substring(0, 16) + '...' : '-'}
                        </code>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
