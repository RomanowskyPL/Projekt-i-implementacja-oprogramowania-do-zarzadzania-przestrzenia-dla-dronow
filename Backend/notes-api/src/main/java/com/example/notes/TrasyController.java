package com.example.notes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trasy")
@CrossOrigin
public class TrasyController {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public TrasyController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    // Lista tras
    @GetMapping
    public List<Map<String, Object>> listTrasy() {
        String sql = """
                SELECT
                    id_trasy,
                    nazwa,
                    opis,
                    planowana_dlugosc_m,
                    planowany_czas_min
                FROM trasy
                ORDER BY id_trasy
                """;

        return jdbcTemplate.queryForList(sql);
    }

    // Szczególy danej trasy
    @GetMapping("/{id}")
    public Map<String, Object> getTrasa(@PathVariable long id) {
        String sql = """
                SELECT
                    id_trasy,
                    nazwa,
                    opis,
                    planowana_dlugosc_m,
                    planowany_czas_min
                FROM trasy
                WHERE id_trasy = ?
                """;

        return jdbcTemplate.queryForMap(sql, id);
    }

    // Współrzędne danej trasy
    @GetMapping("/{id}/punkty")
    public List<Map<String, Object>> getTrasaPunkty(@PathVariable("id") long trasaId) {

        String sql = """
                SELECT
                    id_punktu,
                    id_trasy,
                    kolejnosc,
                    ST_Y(geom) AS lat,   -- szerokość geogr.
                    ST_X(geom) AS lon,   -- długość geogr.
                    wysokosc_m
                FROM trasy_punkty
                WHERE id_trasy = :trasaId
                ORDER BY kolejnosc ASC
                """;

        Map<String, Object> params = Map.of("trasaId", trasaId);

        return namedJdbcTemplate.queryForList(sql, params);
    }
}
