package com.example.notes;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/route")
public class RouteController {

    private final JdbcTemplate jdbc;

    public RouteController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Lista tras
    @GetMapping
    public List<Map<String, Object>> list() {
        try {
            return jdbc.queryForList("""
                SELECT
                    id_trasy,
                    nazwa,
                    opis,
                    planowana_dlugosc_m,
                    planowany_czas_min,
                    ST_X(punkt_startu::geometry)  AS start_lon,
                    ST_Y(punkt_startu::geometry)  AS start_lat,
                    ST_X(punkt_koncowy::geometry) AS end_lon,
                    ST_Y(punkt_koncowy::geometry) AS end_lat
                FROM public."trasy"
                ORDER BY id_trasy
            """);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DB error: " + e.getMessage(), e);
        }
    }

    // Szczególy trasy
    @GetMapping("/{id}")
    public Map<String, Object> one(@PathVariable int id) {
        try {
            return jdbc.queryForMap("""
                SELECT
                    id_trasy,
                    nazwa,
                    opis,
                    planowana_dlugosc_m,
                    planowany_czas_min,
                    ST_X(punkt_startu::geometry)  AS start_lon,
                    ST_Y(punkt_startu::geometry)  AS start_lat,
                    ST_X(punkt_koncowy::geometry) AS end_lon,
                    ST_Y(punkt_koncowy::geometry) AS end_lat
                FROM public."trasy"
                WHERE id_trasy = ?
            """, id);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found", e);
        }
    }

    // Współrzędne danej trasy
    @GetMapping("/{id}/points")
    public List<Map<String, Object>> points(@PathVariable int id) {
        try {
            return jdbc.queryForList("""
                SELECT
                    id_punktu,
                    id_trasy,
                    kolejnosc,
                    ST_X(wspolrzedne::geometry) AS lon,
                    ST_Y(wspolrzedne::geometry) AS lat,
                    wysokosc_m,
                    opis
                FROM public."trasy_punkty"
                WHERE id_trasy = ?
                ORDER BY kolejnosc
            """, id);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DB error: " + e.getMessage(), e);
        }
    }
}
