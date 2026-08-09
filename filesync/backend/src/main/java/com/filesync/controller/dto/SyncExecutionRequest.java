package com.filesync.controller.dto;

import java.util.List;

public class SyncExecutionRequest {
    private List<String> approvedHashes;
    private List<String> deviceIds;

    public SyncExecutionRequest() {}

    public SyncExecutionRequest(List<String> approvedHashes, List<String> deviceIds) {
        this.approvedHashes = approvedHashes;
        this.deviceIds = deviceIds;
    }

    public List<String> getApprovedHashes() { return approvedHashes; }
    public void setApprovedHashes(List<String> approvedHashes) { this.approvedHashes = approvedHashes; }

    public List<String> getDeviceIds() { return deviceIds; }
    public void setDeviceIds(List<String> deviceIds) { this.deviceIds = deviceIds; }
}
