package com.example.notes;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/drony")
public class DronyController {

    private final JdbcTemplate jdbc;

    public DronyController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Lista modeli drona i egzemplarzy danego modelu
    @GetMapping("/model")
    public List<Map<String, Object>> listModels() {
        try {
            return jdbc.queryForList("""
                SELECT m.id_modelu,
                       m.producent,
                       m.nazwa_modelu,
                       m.klasa_drona,
                       m.masa_g,
                       COALESCE(cnt.cnt, 0) AS liczba_egzemplarzy
                FROM public."model_drona" m
                LEFT JOIN (
                    SELECT id_modelu, COUNT(*) AS cnt
                    FROM public."egzemplarz_drona"
                    GROUP BY id_modelu
                ) cnt ON cnt.id_modelu = m.id_modelu
                ORDER BY m.producent, m.nazwa_modelu
            """);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DB error: " + e.getMessage(), e);
        }
    }

    // Wyświetlenie szczegółów modelu
    @GetMapping("/model/{id}")
    public Map<String, Object> model(@PathVariable int id) {
        try {
            return jdbc.queryForMap("""
                SELECT id_modelu, producent, nazwa_modelu, klasa_drona, masa_g
                FROM public."model_drona"
                WHERE id_modelu = ?
            """, id);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found");
        }
    }

    // Wyświetlenie szczegółów egzemplarzy
    @GetMapping("/model/{id}/egzemplarze")
    public List<Map<String, Object>> instances(@PathVariable int id) {
        try {
            return jdbc.queryForList("""
                SELECT id_drona, status, numer_seryjny, data_zakupu
                FROM public."egzemplarz_drona"
                WHERE id_modelu = ?
                ORDER BY COALESCE(data_zakupu, DATE '1900-01-01') DESC, numer_seryjny
            """, id);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DB error: " + e.getMessage(), e);
        }
    }
}
