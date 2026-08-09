import React, { useState, useEffect } from 'react';

export default function SyncGroupSetup({ selectedGroup, onSelectGroup, onPcFolderSet }) {
  const [groups, setGroups] = useState([]);
  const [newGroupLabel, setNewGroupLabel] = useState('');
  const [pcFolderPath, setPcFolderPath] = useState('');
  const [message, setMessage] = useState('');

  const fetchGroups = async () => {
    try {
      const res = await fetch('/api/groups');
      if (res.ok) {
        const data = await res.json();
        setGroups(data);
      }
    } catch (err) {
      console.error('Failed to fetch groups', err);
    }
  };

  useEffect(() => {
    fetchGroups();
  }, []);

  const handleCreateGroup = async (e) => {
    e.preventDefault();
    if (!newGroupLabel.trim()) return;
    try {
      const res = await fetch('/api/groups', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ label: newGroupLabel.trim() }),
      });
      if (res.ok) {
        const created = await res.json();
        setNewGroupLabel('');
        await fetchGroups();
        onSelectGroup(created);
        setMessage(`Created group: ${created.label}`);
      }
    } catch (err) {
      setMessage('Error creating group');
    }
  };

  const handleDeleteGroup = async (group) => {
    const confirmed = window.confirm(
      `Delete group "${group.label}" (${group.id.substring(0, 8)}…)?\n\n` +
      `This removes the group and all its device/file records from the app database. ` +
      `Physical files on drives are NOT touched — signature files on pendrives remain.\n\n` +
      `This cannot be undone.`
    );
    if (!confirmed) return;
    try {
      const res = await fetch(`/api/groups/${group.id}`, { method: 'DELETE' });
      if (res.ok || res.status === 204) {
        setMessage(`Deleted group: ${group.label}`);
        // Deselect if the active group was deleted
        if (selectedGroup && selectedGroup.id === group.id) {
          onSelectGroup(null);
        }
        await fetchGroups();
      } else {
        setMessage(`Failed to delete group (${res.status})`);
      }
    } catch (err) {
      setMessage('Error deleting group');
    }
  };

  const handleSetPcFolder = async (e) => {
    e.preventDefault();
    if (!selectedGroup || !pcFolderPath.trim()) return;
    try {
      const res = await fetch(`/api/groups/${selectedGroup.id}/pc-folder`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path: pcFolderPath.trim() }),
      });
      if (res.ok) {
        setMessage(`PC folder configured: ${pcFolderPath}`);
        if (onPcFolderSet) onPcFolderSet();
      } else {
        const text = await res.text();
        setMessage(`Error setting PC folder: ${text}`);
      }
    } catch (err) {
      setMessage('Failed to set PC folder');
    }
  };

  return (
    <div className="clay-card">
      <h2>1. Sync Group &amp; PC Setup</h2>

      <div style={{ display: 'flex', gap: '28px', flexWrap: 'wrap', marginBottom: '20px' }}>
        <div style={{ flex: '1 1 280px' }}>
          <h3>Select Existing Group</h3>
          <select
            className="clay-select"
            value={selectedGroup ? selectedGroup.id : ''}
            onChange={(e) => {
              const g = groups.find((item) => item.id === e.target.value);
              onSelectGroup(g || null);
            }}
            style={{ width: '100%', maxWidth: '280px' }}
          >
            <option value="">-- Choose Group --</option>
            {groups.map((g) => (
              <option key={g.id} value={g.id}>
                {g.label} ({g.id.substring(0, 8)}...)
              </option>
            ))}
          </select>

          {/* Group list with delete buttons */}
          {groups.length > 0 && (
            <div style={{ marginTop: '16px' }}>
              <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '8px', fontWeight: '600' }}>Manage groups:</p>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                {groups.map((g) => (
                  <div key={g.id} style={{
                    display: 'flex', alignItems: 'center', gap: '10px',
                    padding: '8px 12px', borderRadius: '14px', background: '#f0f4f8',
                    boxShadow: 'var(--clay-shadow-input)'
                  }}>
                    <span style={{
                      flex: 1, fontSize: '13px',
                      fontWeight: selectedGroup && selectedGroup.id === g.id ? '700' : '500',
                      color: selectedGroup && selectedGroup.id === g.id ? '#1c456b' : 'var(--text)'
                    }}>
                      {g.label} <span style={{ color: '#828e9e', fontFamily: 'var(--font-mono)', fontSize: '11px' }}>({g.id.substring(0, 8)}…)</span>
                    </span>
                    <button
                      onClick={() => handleDeleteGroup(g)}
                      title={`Delete group "${g.label}"`}
                      className="clay-btn clay-btn-peach"
                      style={{ padding: '4px 10px', fontSize: '12px', borderRadius: '12px' }}
                    >
                      🗑 Delete
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        <div style={{ flex: '1 1 300px' }}>
          <h3>Or Create New Group</h3>
          <form onSubmit={handleCreateGroup} style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
            <input
              type="text"
              placeholder="Group Label (e.g. Trip Photos)"
              className="clay-input"
              value={newGroupLabel}
              onChange={(e) => setNewGroupLabel(e.target.value)}
              style={{ flex: '1 1 200px' }}
            />
            <button type="submit" className="clay-btn clay-btn-mint">Create</button>
          </form>
        </div>
      </div>

      {selectedGroup && (
        <div style={{ marginTop: '20px', paddingTop: '20px', borderTop: '2px solid rgba(166, 180, 200, 0.2)' }}>
          <h3>Set PC Folder for "{selectedGroup.label}"</h3>
          <form onSubmit={handleSetPcFolder} style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
            <input
              type="text"
              placeholder="Absolute PC path (e.g. C:\Users\...\SyncFolder)"
              className="clay-input"
              value={pcFolderPath}
              onChange={(e) => setPcFolderPath(e.target.value)}
              style={{ flex: '1 1 380px' }}
            />
            <button type="submit" className="clay-btn">Set PC Folder</button>
          </form>
        </div>
      )}

      {message && (
        <div className="clay-badge" style={{ marginTop: '16px', background: '#dce8f5', color: '#1c456b', padding: '8px 16px', fontSize: '13px', borderRadius: '16px' }}>
          ℹ️ {message}
        </div>
      )}
    </div>
  );
}
