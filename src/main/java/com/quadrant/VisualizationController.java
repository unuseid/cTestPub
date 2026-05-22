package com.quadrant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Provides the REST API for the quadrant-failure visualization.
 *
 * POST /api/data  — replace the in-memory dataset with a new List<FailXY>
 * GET  /api/data  — return the current dataset as JSON
 */
@RestController
public class VisualizationController {

    private final List<FailXY> store = new CopyOnWriteArrayList<>();

    @GetMapping("/api/data")
    public List<FailXY> getData() {
        return new ArrayList<>(store);
    }

    @PostMapping("/api/data")
    public ResponseEntity<String> setData(@RequestBody List<FailXY> items) {
        store.clear();
        store.addAll(items);
        return ResponseEntity.ok("Loaded " + items.size() + " items");
    }
}
