import React, { useState, useEffect } from 'react';
import SyncGroupSetup from './components/SyncGroupSetup';
import DeviceList from './components/DeviceList';
import FolderPicker from './components/FolderPicker';
import SyncPlanTable from './components/SyncPlanTable';
import './App.css';

export default function App() {
  const [selectedGroup, setSelectedGroup] = useState(null);
  const [devices, setDevices] = useState([]);
  const [assigningDevice, setAssigningDevice] = useState(null);

  const fetchDevices = async () => {
    if (!selectedGroup) {
      setDevices([]);
      return;
    }
    try {
      const res = await fetch(`/api/devices?groupId=${selectedGroup.id}`);
      if (res.ok) {
        const data = await res.json();
        setDevices(data);
      }
    } catch (err) {
      console.error('Failed to fetch devices', err);
    }
  };

  useEffect(() => {
    fetchDevices();
  }, [selectedGroup]);

  // Extract connected deviceIds (only mounted, recognised devices) for DiffEngine
  const connectedDeviceIds = devices
    .filter((d) => d.deviceId && d.connected !== false)
    .map((d) => d.deviceId);

  return (
    <div style={{ maxWidth: '1000px', margin: '0 auto', padding: '16px 0' }}>
      <header className="clay-card" style={{ marginBottom: '28px', padding: '24px 32px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <h1 style={{ margin: 0, color: '#1c456b', fontSize: '30px' }}>⚡ FileSync</h1>
        <p style={{ margin: 0, color: '#626d7d', fontSize: '15px', fontWeight: '500' }}>
          Offline-first multi-device USB sync orchestrator
        </p>
      </header>

      {/* 1. Sync Group & PC Setup */}
      <SyncGroupSetup
        selectedGroup={selectedGroup}
        onSelectGroup={(group) => {
          setSelectedGroup(group);
          setAssigningDevice(null);
        }}
        onPcFolderSet={() => {
          fetchDevices();
        }}
      />

      {/* 2. Device List & Recognition */}
      <DeviceList
        groupId={selectedGroup ? selectedGroup.id : null}
        devices={devices}
        onRefreshDevices={fetchDevices}
        onSelectDeviceForAssignment={(dev) => setAssigningDevice(dev)}
      />

      {/* Folder Picker Modal / Panel */}
      {assigningDevice && selectedGroup && (
        <FolderPicker
          device={assigningDevice}
          groupId={selectedGroup.id}
          onComplete={() => {
            setAssigningDevice(null);
            fetchDevices();
          }}
          onCancel={() => setAssigningDevice(null)}
        />
      )}

      {/* 3. Sync Plan Diff & Table */}
      {selectedGroup && (
        <SyncPlanTable
          groupId={selectedGroup.id}
          connectedDeviceIds={connectedDeviceIds}
          devices={devices}
        />
      )}
    </div>
  );
}
