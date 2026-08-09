import React, { useState, useEffect } from 'react';

export default function FolderPicker({ device, groupId, onComplete, onCancel }) {
  const [currentPath, setCurrentPath] = useState(device ? device.drivePath : '');
  const [folders, setFolders] = useState([]);
  const [selectedFolderPath, setSelectedFolderPath] = useState('');
  const [deviceLabel, setDeviceLabel] = useState(device ? device.driveLabel || 'Removable Drive' : '');
  const [sourceOnly, setSourceOnly] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const fetchSubfolders = async (targetPath) => {
    try {
      setErrorMsg('');
      const res = await fetch(`/api/browse?path=${encodeURIComponent(targetPath)}`);
      if (res.ok) {
        const data = await res.json();
        setFolders(data);
        setCurrentPath(targetPath);
        setSelectedFolderPath(targetPath);
      } else {
        setErrorMsg('Failed to browse folder');
      }
    } catch (err) {
      setErrorMsg('Error browsing path');
    }
  };

  const chooseFolderNative = async () => {
    try {
      setErrorMsg('');
      const res = await fetch('/api/browse-dialog');
      if (res.status === 204) {
        // User cancelled the dialog — no change
        return;
      }
      if (res.ok) {
        const chosenPath = await res.json();
        setSelectedFolderPath(chosenPath);
        setCurrentPath(chosenPath);
        // Also load subfolders at that path for the tree view
        fetchSubfolders(chosenPath);
      } else {
        const text = await res.text();
        setErrorMsg(`Native dialog error: ${text}`);
      }
    } catch (err) {
      setErrorMsg('Error opening native folder picker');
    }
  };

  useEffect(() => {
    if (device && device.drivePath) {
      fetchSubfolders(device.drivePath);
    }
  }, [device]);

  const handleAssign = async (e) => {
    e.preventDefault();
    if (!selectedFolderPath) return;

    // Generate deviceId if new
    const devId = device.deviceId || `device-${Date.now()}`;

    try {
      const res = await fetch(`/api/devices/${devId}/folder`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          folderPath: selectedFolderPath,
          syncGroupId: groupId,
          deviceLabel: deviceLabel,
          sourceOnly: sourceOnly,
        }),
      });

      if (res.ok) {
        onComplete();
      } else {
        const text = await res.text();
        setErrorMsg(`Failed to assign: ${text}`);
      }
    } catch (err) {
      setErrorMsg('Error assigning folder');
    }
  };

  return (
    <div className="clay-card-blue">
      <h2>📂 Assign Folder for Drive {device?.drivePath}</h2>
      
      <div style={{ marginBottom: '14px', fontSize: '14px' }}>
        <strong>Current Path: </strong> <code>{currentPath}</code>
      </div>

      <div style={{
        maxHeight: '200px', overflowY: 'auto', borderRadius: '16px',
        boxShadow: 'var(--clay-shadow-input)', background: '#ebf0f5', padding: '12px', marginBottom: '16px'
      }}>
        <p style={{ margin: '0 0 8px 0', fontSize: '13px', color: 'var(--text-muted)', fontWeight: '600' }}>Sub-folders:</p>
        {folders.length === 0 ? (
          <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>No subdirectories found (you can assign current root path).</p>
        ) : (
          folders.map((f) => (
            <div
              key={f.path}
              onClick={() => fetchSubfolders(f.path)}
              style={{
                padding: '8px 12px', cursor: 'pointer', borderRadius: '12px',
                marginBottom: '4px', background: '#f5f8fc', transition: 'background 0.2s ease',
                display: 'flex', alignItems: 'center', gap: '8px'
              }}
              onMouseEnter={(e) => e.currentTarget.style.background = '#e2ecf7'}
              onMouseLeave={(e) => e.currentTarget.style.background = '#f5f8fc'}
            >
              <span>📁 {f.name}</span>
              <span style={{ color: 'var(--text-muted)', fontSize: '12px' }}>({f.path})</span>
            </div>
          ))
        )}
      </div>

      <form onSubmit={handleAssign}>
        <div style={{ marginBottom: '14px' }}>
          <label style={{ display: 'block', marginBottom: '6px', fontWeight: '600', fontSize: '14px' }}>Chosen Folder Path:</label>
          <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
            <input
              type="text"
              value={selectedFolderPath}
              onChange={(e) => setSelectedFolderPath(e.target.value)}
              className="clay-input"
              style={{ flex: 1 }}
            />
            <button
              type="button"
              onClick={chooseFolderNative}
              title="Open a native OS folder picker dialog"
              className="clay-btn"
              style={{ whiteSpace: 'nowrap' }}
            >
              📂 Choose Folder
            </button>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '20px', marginBottom: '16px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <label style={{ fontWeight: '600', fontSize: '14px' }}>Device Label:</label>
            <input
              type="text"
              value={deviceLabel}
              onChange={(e) => setDeviceLabel(e.target.value)}
              className="clay-input"
              style={{ padding: '8px 14px' }}
            />
          </div>

          <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontWeight: '600', fontSize: '14px' }}>
            <input
              type="checkbox"
              checked={sourceOnly}
              onChange={(e) => setSourceOnly(e.target.checked)}
              className="clay-checkbox"
            />
            Source Only (never write to this device)
          </label>
        </div>

        {errorMsg && <p style={{ color: '#7f1d1d', margin: '8px 0', fontWeight: '600', fontSize: '14px' }}>{errorMsg}</p>}

        <div style={{ display: 'flex', gap: '12px', marginTop: '20px' }}>
          <button type="submit" className="clay-btn clay-btn-mint">
            Confirm & Write Signature
          </button>
          <button type="button" onClick={onCancel} className="clay-btn clay-btn-gray">
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
