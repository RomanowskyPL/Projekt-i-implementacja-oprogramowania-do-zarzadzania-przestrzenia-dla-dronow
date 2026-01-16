// src/main/java/com/example/notes/FlightTypeController.java
package com.example.notes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class FlightTypeController {

    private final JdbcTemplate jdbc;

    public FlightTypeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Typy lotów (tylko testowy)
    @GetMapping("/typ_lotu")
    public List<Map<String, Object>> listFlightTypes() {
        return jdbc.queryForList(
                "SELECT id_typ AS id_typ, nazwa FROM typ_lotu ORDER BY nazwa"
        );
    }

    @GetMapping("/typ_lotu/{id}")
    public Map<String, Object> getFlightType(@PathVariable int id) {
        return jdbc.queryForMap(
                "SELECT id_typ AS id_typ, nazwa FROM typ_lotu WHERE id_typ = ?",
                id
        );
    }
}
