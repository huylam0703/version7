package vn.farm.sensordata.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.farm.sensordata.api.dto.ReadingFilter;
import vn.farm.sensordata.api.dto.ReadingInterval;
import vn.farm.sensordata.api.dto.ReadingPageRequest;
import vn.farm.sensordata.api.dto.ReadingResults.*;
import vn.farm.sensordata.api.dto.ReadingView;
import vn.farm.sensordata.domain.ReadingValueKind;
import vn.farm.sensordata.domain.SensorType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReadingQueryRepository {
    private static final String CANONICAL_CODE = """
            CASE UPPER(TRIM(r.sensor_type))
                WHEN 'TEMPERATURE' THEN 'AIR_TEMP'
                WHEN 'HUMIDITY' THEN 'AIR_HUMIDITY'
                ELSE UPPER(TRIM(r.sensor_type))
            END
            """;
    private static final String GROUP_COLUMNS = "sensor_code, unit, value_kind, adc_resolution_bits";
    private static final String AGGREGATES = """
            MIN(sensor_value) AS min_value, MAX(sensor_value) AS max_value,
            AVG(sensor_value) AS avg_value, COUNT_BIG(*) AS reading_count
            """;
    private static final RowMapper<ReadingView> MAPPER = (row, index) -> new ReadingView(
            row.getLong("id"), row.getString("node_id"), row.getString("sensor_code"),
            row.getDouble("sensor_value"), row.getString("unit"), valueKind(row),
            row.getObject("adc_resolution_bits", Integer.class), utc(row, "measured_at"),
            utc(row, "collected_at"), utc(row, "received_at"), row.getDouble("latitude"),
            row.getDouble("longitude"), row.getString("validation_status"),
            uuid(row, "farm_id"), uuid(row, "zone_id"), uuid(row, "mission_id"), uuid(row, "execution_id"));

    private final NamedParameterJdbcTemplate jdbc;

    public ReadingQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ReadingView> findById(long id) {
        String sql = "SELECT r.*, " + CANONICAL_CODE + " AS sensor_code FROM dbo.sensor_readings r WHERE r.id = :id";
        return jdbc.query(sql, new MapSqlParameterSource("id", id), MAPPER).stream().findFirst();
    }

    public Page findPage(ReadingFilter filter, ReadingPageRequest page, String orderColumn,
                         boolean ascending, boolean latest) {
        FilteredQuery query = filtered(filter);
        String with = query.withClause();
        String source = "filtered";
        if (latest) {
            with += """
                    , ranked AS (
                        SELECT *, ROW_NUMBER() OVER (
                            PARTITION BY node_id, sensor_code ORDER BY measured_at DESC, id DESC
                        ) AS recency
                        FROM filtered
                    )
                    """;
            source = "ranked WHERE recency = 1";
        }
        long total = jdbc.queryForObject(with + "SELECT COUNT_BIG(*) FROM " + source, query.params(), Long.class);
        if (page.offset() >= total) {
            return new Page(List.of(), page.page(), page.pageSize(), total);
        }

        String direction = ascending ? " ASC" : " DESC";
        String order = " ORDER BY " + orderColumn + direction;
        if (!orderColumn.equals("id")) {
            order += ", id" + direction;
        }
        query.params().addValue("offset", page.offset()).addValue("pageSize", page.pageSize());
        List<ReadingView> rows = jdbc.query(with + "SELECT * FROM " + source + order
                + " OFFSET :offset ROWS FETCH NEXT :pageSize ROWS ONLY", query.params(), MAPPER);
        return new Page(rows, page.page(), page.pageSize(), total);
    }

    public List<Statistics> statistics(ReadingFilter filter) {
        FilteredQuery query = filtered(filter);
        String sql = query.withClause() + "SELECT " + GROUP_COLUMNS + ", " + AGGREGATES
                + " FROM filtered GROUP BY " + GROUP_COLUMNS + " ORDER BY " + GROUP_COLUMNS;
        return jdbc.query(sql, query.params(), (row, index) -> new Statistics(
                row.getString("sensor_code"), row.getString("unit"), valueKind(row),
                row.getObject("adc_resolution_bits", Integer.class), row.getDouble("min_value"),
                row.getDouble("max_value"), row.getDouble("avg_value"), row.getLong("reading_count")));
    }

    public List<TimePoint> timeSeries(ReadingFilter filter, ReadingInterval interval, int limit) {
        FilteredQuery query = filtered(filter);
        String bucket = "DATETRUNC(" + interval.name().toLowerCase(Locale.ROOT) + ", measured_at)";
        String sql = query.withClause()
                + "SELECT TOP (:limit) " + bucket + " AS bucket_start, " + GROUP_COLUMNS + ", " + AGGREGATES
                + " FROM filtered GROUP BY " + bucket + ", " + GROUP_COLUMNS
                + " ORDER BY bucket_start, " + GROUP_COLUMNS;
        query.params().addValue("limit", limit);
        return jdbc.query(sql, query.params(), (row, index) -> new TimePoint(
                utc(row, "bucket_start"), row.getString("sensor_code"), row.getString("unit"),
                valueKind(row), row.getObject("adc_resolution_bits", Integer.class),
                row.getDouble("min_value"), row.getDouble("max_value"), row.getDouble("avg_value"),
                row.getLong("reading_count")));
    }

    public Quality quality(ReadingFilter filter) {
        FilteredQuery query = filtered(filter);
        String sql = query.withClause() + """
                SELECT COUNT_BIG(*) AS total,
                       COUNT_BIG(CASE WHEN validation_status = 'VALID' THEN 1 END) AS valid_count,
                       COUNT_BIG(CASE WHEN validation_status = 'INVALID' THEN 1 END) AS invalid_count,
                       COUNT_BIG(CASE WHEN unit IS NULL OR TRIM(unit) = N'' OR value_kind IS NULL
                                 THEN 1 END) AS missing_metadata
                FROM filtered
                """;
        return jdbc.queryForObject(sql, query.params(), (row, index) -> {
            long total = row.getLong("total");
            long invalid = row.getLong("invalid_count");
            return new Quality(total, row.getLong("valid_count"), invalid,
                    row.getLong("missing_metadata"), total == 0 ? 0 : 100d * invalid / total);
        });
    }

    private static FilteredQuery filtered(ReadingFilter filter) {
        var params = new MapSqlParameterSource();
        var sql = new StringBuilder("WITH filtered AS (SELECT r.*, ")
                .append(CANONICAL_CODE).append(" AS sensor_code FROM dbo.sensor_readings r WHERE 1 = 1");
        add(sql, params, "r.node_id = :nodeId", "nodeId", filter.nodeId());
        if (filter.sensorType() != null) {
            String code = filter.sensorType().strip().toUpperCase(Locale.ROOT);
            List<String> codes = SensorType.findByCode(code).map(SensorType::codes).orElse(List.of(code));
            add(sql, params, "UPPER(TRIM(r.sensor_type)) IN (:sensorCodes)", "sensorCodes", codes);
        }
        add(sql, params, "r.farm_id = :farmId", "farmId", uuidParameter(filter.farmId()));
        add(sql, params, "r.zone_id = :zoneId", "zoneId", uuidParameter(filter.zoneId()));
        add(sql, params, "r.mission_id = :missionId", "missionId", uuidParameter(filter.missionId()));
        add(sql, params, "r.execution_id = :executionId", "executionId", uuidParameter(filter.executionId()));
        add(sql, params, "r.measured_at >= :fromTime", "fromTime", sqlTime(filter.from()));
        add(sql, params, "r.measured_at < :toTime", "toTime", sqlTime(filter.to()));
        add(sql, params, "r.validation_status = :status", "status",
                filter.validationStatus() == null ? null : filter.validationStatus().name());
        add(sql, params, "r.value_kind = :kind", "kind", filter.valueKind() == null ? null : filter.valueKind().name());
        add(sql, params, "r.unit = :unit", "unit", filter.unit());
        return new FilteredQuery(sql.append(") ").toString(), params);
    }

    private static void add(StringBuilder sql, MapSqlParameterSource params, String condition,
                            String parameter, Object value) {
        if (value != null) {
            sql.append(" AND ").append(condition);
            params.addValue(parameter, value);
        }
    }

    private static LocalDateTime sqlTime(Instant value) {
        if (value == null) {
            return null;
        }
        // Ceiling preserves both inclusive lower and exclusive upper bounds on millisecond rows.
        Instant bound = value.plusNanos(999_999).truncatedTo(ChronoUnit.MILLIS);
        return LocalDateTime.ofInstant(bound, ZoneOffset.UTC);
    }

    private static String uuidParameter(UUID value) {
        return value == null ? null : value.toString();
    }

    private static Instant utc(ResultSet row, String column) throws SQLException {
        LocalDateTime value = row.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static UUID uuid(ResultSet row, String column) throws SQLException {
        String value = row.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private static ReadingValueKind valueKind(ResultSet row) throws SQLException {
        String value = row.getString("value_kind");
        return value == null ? null : ReadingValueKind.valueOf(value);
    }

    private record FilteredQuery(String withClause, MapSqlParameterSource params) {}
}
