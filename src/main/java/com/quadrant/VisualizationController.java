package com.quadrant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Provides the REST API for the quadrant-failure visualization.
 *
 * POST /api/data     — replace the in-memory dataset with a new List<FailXY>
 * GET  /api/data     — return the current dataset as JSON
 * GET  /api/data/db  — fetch dataset from a database via JDBC (caches in-memory)
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

    /**
     * Reads failure data from a database via JDBC and returns it.
     * Also caches the result in the in-memory store so subsequent GET /api/data
     * calls (including the dashboard auto-refresh) keep showing the same data.
     *
     * The JDBC implementation belongs here — populate {@code items} from your
     * query result, then leave the cache update below in place.
     */
    @GetMapping("/api/data/db")
    public List<FailXY> loadFromDb() {
        List<FailXY> items = new ArrayList<>();

        // TODO: JDBC query — populate `items` from the database here.

        store.clear();
        store.addAll(items);
        return items;
    }
}
