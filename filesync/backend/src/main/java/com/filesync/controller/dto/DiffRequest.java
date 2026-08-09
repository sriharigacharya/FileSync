package com.filesync.controller.dto;

import java.util.List;

public class DiffRequest {
    private List<String> deviceIds;

    public DiffRequest() {}

    public DiffRequest(List<String> deviceIds) {
        this.deviceIds = deviceIds;
    }

    public List<String> getDeviceIds() { return deviceIds; }
    public void setDeviceIds(List<String> deviceIds) { this.deviceIds = deviceIds; }
}
