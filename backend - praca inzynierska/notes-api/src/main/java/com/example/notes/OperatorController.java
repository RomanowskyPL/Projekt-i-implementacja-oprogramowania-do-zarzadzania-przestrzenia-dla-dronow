package com.example.notes;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/operator")
public class OperatorController {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public OperatorController(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    // Lista operatorów
    @GetMapping
    public List<Map<String, Object>> all() {
        try {
            return jdbc.queryForList("""
                SELECT * FROM public."operator"
                ORDER BY id_operatora
            """);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DB error: " + e.getMessage(), e);
        }
    }

    // Szczegóły operatora
    @GetMapping("/{id}")
    public Map<String, Object> one(@PathVariable int id) {
        try {
            return jdbc.queryForMap("""
                SELECT * FROM public."operator"
                WHERE id_operatora = ?
            """, id);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Operator not found");
        }
    }

    // CCertyfikaty operatora
    @GetMapping("/{id}/certyfikaty")
    public List<Map<String, Object>> certs(@PathVariable int id) {
        try {
            return jdbc.queryForList("""
                SELECT id_certyfikatu,
                       nazwa,
                       wystawca,
                       data_wydania,
                       data_wygasniecia,
                       dokument_url,
                       uwagi
                FROM public."certyfikaty"
                WHERE id_operatora = ?
                ORDER BY COALESCE(data_wydania, DATE '1900-01-01') DESC, nazwa
            """, id);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DB error: " + e.getMessage(), e);
        }
    }

    @GetMapping("/columns")
    public List<Map<String, Object>> columns() {
        try {
            List<Map<String, Object>> cols = jdbc.queryForList("""
                SELECT column_name, data_type, is_nullable, column_default, is_identity
                FROM information_schema.columns
                WHERE table_schema='public' AND table_name='operator'
                ORDER BY ordinal_position
            """);
            for (Map<String, Object> c : cols) {
                boolean auto = "YES".equals(String.valueOf(c.get("is_identity")));
                Object def = c.get("column_default");
                if (!auto && def instanceof String s && s.startsWith("nextval(")) auto = true;
                c.put("auto", auto);
            }
            return cols;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Columns error: " + e.getMessage(), e);
        }
    }

    public record OperatorCreateDto(
            String imie,
            String nazwisko,
            String data_urodzenia,
            String obywatelstwo,
            String e_mail,
            String status
    ) {}

    @PostMapping
    public Map<String, Object> create(@RequestBody OperatorCreateDto dto) {
        try {
            MapSqlParameterSource p = new MapSqlParameterSource()
                    .addValue("imie", dto.imie())
                    .addValue("nazwisko", dto.nazwisko())
                    .addValue("obywatelstwo", dto.obywatelstwo())
                    .addValue("e_mail", dto.e_mail())
                    .addValue("status", dto.status());

            if (dto.data_urodzenia() != null && !dto.data_urodzenia().isBlank()) {
                LocalDate d = LocalDate.parse(dto.data_urodzenia());
                p.addValue("data_urodzenia", Date.valueOf(d));
            } else {
                p.addValue("data_urodzenia", null);
            }

            String sql = """
                INSERT INTO public."operator"
                  (imie, nazwisko, data_urodzenia, obywatelstwo, e_mail, status, utworzono, zaktualizowano)
                VALUES (:imie, :nazwisko, :data_urodzenia, :obywatelstwo, :e_mail, :status, now(), now())
                RETURNING *;
            """;

            return namedJdbc.queryForMap(sql, p);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Insert error: " + e.getMessage(), e);
        }
    }

    // Adres operatora
    @GetMapping("/{id}/adres")
    public Map<String, Object> address(@PathVariable int id) {
        try {
            var list = jdbc.queryForList("""
            SELECT ulica, numer_bloku, numer_mieszkania, miasto, kod_pocztowy, panstwo, numer_telefonu
            FROM public."adres_operatora"
            WHERE id_operatora = ?
            ORDER BY id_adres DESC
            LIMIT 1
        """, id);
            return list.isEmpty() ? java.util.Collections.emptyMap() : list.get(0);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DB error: " + e.getMessage(), e);
        }
    }
}
