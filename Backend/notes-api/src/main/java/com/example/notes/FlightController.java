package com.example.notes;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lot")
@CrossOrigin(origins = "*")
public class FlightController {

    private final JdbcTemplate jdbc;

    public FlightController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static class StartFlightRequest {
        public Integer id_operatora;
        public Integer id_drona;
        public Integer id_trasy;
        public Integer id_typ;
    }

    // Telemetria - zapis punktów
    public static class TelemetryCreateRequest {
        public Double lat;
        public Double lon;
        public Double wysokosc_m;
        public Long czas_ms;

        public Double predkosc_m_s;
        public Double bateria_pro;
        public String sila_sygnalu;
    }

    // Tworzenie nowego rekordu przy nowej misji
    @PostMapping("/start")
    public Map<String, Object> startFlight(@RequestBody StartFlightRequest req) {

        if (req == null
                || req.id_operatora == null
                || req.id_drona == null
                || req.id_trasy == null
                || req.id_typ == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Brak wymaganych pól: id_operatora, id_drona, id_trasy, id_typ"
            );
        }

        String sql = """
            INSERT INTO public.lot (
                id_operatora, id_drona, id_trasy, czas_startu, status, id_typ
            )
            VALUES (?, ?, ?, now()::timestamp, ?, ?)
            RETURNING id_lotu, czas_startu, status, id_operatora, id_drona, id_trasy, id_typ
        """;

        return jdbc.queryForMap(
                sql,
                req.id_operatora,
                req.id_drona,
                req.id_trasy,
                "Rozpoczęty",
                req.id_typ
        );
    }

    // Uaktualnienie nowego rekordu po skończeniu misji
    @PostMapping("/{id}/finish")
    public Map<String, Object> finishFlight(@PathVariable int id) {
        String sql = """
            UPDATE public.lot
            SET status = ?, czas_konca = now()::timestamp
            WHERE id_lotu = ? AND czas_konca IS NULL
            RETURNING id_lotu, status, czas_startu, czas_konca
        """;

        try {
            return jdbc.queryForMap(sql, "Zakończony", id);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Lot nie istnieje albo już zakończony: id_lotu=" + id
            );
        }
    }

    // Przerwanie lotu
    @PostMapping("/{id}/abort")
    public Map<String, Object> abortFlight(@PathVariable int id) {
        String sql = """
            UPDATE public.lot
            SET status = ?, czas_konca = now()::timestamp
            WHERE id_lotu = ? AND czas_konca IS NULL
            RETURNING id_lotu, status, czas_startu, czas_konca
        """;

        try {
            return jdbc.queryForMap(sql, "Przerwany", id);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Lot nie istnieje albo już zakończony: id_lotu=" + id
            );
        }
    }

    // Dodanie telemetrii do bazy danych
    @PostMapping("/{id}/telemetria")
    public Map<String, Object> addTelemetryPoint(@PathVariable int id,
                                                 @RequestBody TelemetryCreateRequest req) {
        if (req == null || req.lat == null || req.lon == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak lat/lon");
        }

        if (req.lat < -90 || req.lat > 90 || req.lon < -180 || req.lon > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowe lat/lon");
        }

        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.lot WHERE id_lotu = ?",
                Integer.class,
                id
        );
        if (cnt == null || cnt == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lot nie istnieje: id_lotu=" + id);
        }

        String sql = """
            INSERT INTO public.telemetria (
                id_lotu, czas, wspolrzedne, wysokosc_m, predkosc_m_s, bateria_pro, sila_sygnalu
            )
            VALUES (
                ?,
                COALESCE(
                    (to_timestamp((?::double precision)/1000.0) AT TIME ZONE 'UTC')::timestamp,
                    now()::timestamp
                ),
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                ?,
                ?,
                ?,
                ?
            )
            RETURNING id_telemetrii, id_lotu, czas
        """;

        try {
            return jdbc.queryForMap(
                sql,
                id,
                req.czas_ms,
                req.lon,
                req.lat,
                req.wysokosc_m,
                req.predkosc_m_s,
                req.bateria_pro,
                req.sila_sygnalu
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Błąd zapisu telemetrii: " + e.getMessage()
            );
        }
    }

    // Historia lotów
    @GetMapping
    public List<Map<String, Object>> listFlights() {
        String sql = """
        SELECT l.id_lotu,
               l.id_trasy,
               t.nazwa AS nazwa_trasy,
               l.czas_startu,
               l.czas_konca,
               COALESCE(l.rzeczywisty_czas_s,
                        EXTRACT(EPOCH FROM (l.czas_konca - l.czas_startu)))::int AS czas_trwania_s,
               COALESCE(o.imie || ' ' || o.nazwisko, '') AS operator,
               l.status
        FROM public.lot l
        LEFT JOIN public.trasy    t ON t.id_trasy     = l.id_trasy
        LEFT JOIN public.operator o ON o.id_operatora = l.id_operatora
        ORDER BY l.czas_startu DESC NULLS LAST
        """;
        return jdbc.queryForList(sql);
    }

    // Szczególy lotu
    @GetMapping("/{id}")
    public Map<String, Object> flightDetail(@PathVariable int id) {
        String sql = """
        SELECT
            l.id_lotu, l.id_trasy, l.czas_startu, l.czas_konca, l.status,
            l.rzeczywista_dlugosc_lotu_m,
            COALESCE(l.rzeczywisty_czas_s,
                     EXTRACT(EPOCH FROM (l.czas_konca - l.czas_startu)))::int AS czas_trwania_s,

            t.nazwa  AS nazwa_trasy,
            t.opis   AS opis_trasy,

            o.id_operatora,
            o.imie, o.nazwisko, o.e_mail,
            o.numer_operatora AS uid_operatora,
            ed.id_drona,
            ed.numer_seryjny,
            ed.status AS status_drona,

            md.producent,
            md.nazwa_modelu,
            md.klasa_drona,
            md.masa_g,
            md.zasieg_m,
            md.predkosc_m_s,

            tl.id_typ        AS id_typu_lotu,
            tl.nazwa         AS typ_lotu,
            tl.opis          AS opis_typu,
            tl.metadane      AS metadane_typu
        FROM public.lot l
        LEFT JOIN public.trasy            t  ON t.id_trasy     = l.id_trasy
        LEFT JOIN public.operator         o  ON o.id_operatora = l.id_operatora
        LEFT JOIN public.egzemplarz_drona ed ON ed.id_drona    = l.id_drona
        LEFT JOIN public.model_drona      md ON md.id_modelu   = ed.id_modelu
        LEFT JOIN public.typ_lotu         tl ON tl.id_typ      = l.id_typ
        WHERE l.id_lotu = ?
        """;
        return jdbc.queryForMap(sql, id);
    }

    // Wyświeltenie trasy danego lotu
    @GetMapping("/{id}/route-points")
    public List<Map<String,Object>> flightRoutePoints(@PathVariable int id) {
        String sql = """
        SELECT
            tp.kolejnosc,
            ST_Y(tp.wspolrzedne::geometry) AS lat,
            ST_X(tp.wspolrzedne::geometry) AS lon,
            tp.wysokosc_m,
            COALESCE(tp.opis,'') AS opis
        FROM public.trasy_punkty tp
        JOIN public.lot l ON l.id_trasy = tp.id_trasy
        WHERE l.id_lotu = ?
        ORDER BY tp.kolejnosc
        """;
        return jdbc.queryForList(sql, id);
    }

    // Wyświetlenie telemetrii danego lotu
    @GetMapping("/{id}/telemetria")
    public List<Map<String, Object>> flightTelemetry(@PathVariable int id) {
        String sql = """
        SELECT
            tm.id_telemetrii,
            tm.czas,
            ST_Y(tm.wspolrzedne::geometry) AS lat,
            ST_X(tm.wspolrzedne::geometry) AS lon,
            tm.wysokosc_m,
            tm.predkosc_m_s,
            tm.bateria_pro,
            tm.sila_sygnalu
        FROM public.telemetria tm
        WHERE tm.id_lotu = ?
        ORDER BY tm.czas ASC, tm.id_telemetrii ASC
        """;
        return jdbc.queryForList(sql, id);
    }
}
