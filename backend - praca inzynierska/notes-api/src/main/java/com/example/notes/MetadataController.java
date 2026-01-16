package com.example.notes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/metadata")
public class MetadataController {
    private final JdbcTemplate jdbc;
    public MetadataController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/tables")
    public List<Map<String,Object>> tables() {
        return jdbc.queryForList("""
      SELECT relname AS table_name, n_live_tup AS approx_row_count
      FROM pg_stat_user_tables
      ORDER BY relname
    """);
    }
}
