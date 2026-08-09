package com.filesync.controller;

import com.filesync.controller.dto.ScanRequest;
import com.filesync.indexer.FileIndexer;
import com.filesync.indexer.ScannedFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/scan")
public class ScanController {

    @Autowired
    private FileIndexer fileIndexer;

    @PostMapping
    public ResponseEntity<?> scanFolder(@RequestBody ScanRequest request) {
        try {
            Path targetPath = Paths.get(request.getPath());
            List<ScannedFile> files = fileIndexer.scanFolder(targetPath);
            return ResponseEntity.ok(files);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error scanning folder: " + e.getMessage());
        }
    }
}
