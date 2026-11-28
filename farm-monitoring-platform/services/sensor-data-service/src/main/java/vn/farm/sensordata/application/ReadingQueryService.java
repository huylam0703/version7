package vn.farm.sensordata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.farm.sensordata.api.dto.ReadingFilter;
import vn.farm.sensordata.api.dto.ReadingFilter.Status;
import vn.farm.sensordata.api.dto.ReadingInterval;
import vn.farm.sensordata.api.dto.ReadingPageRequest;
import vn.farm.sensordata.api.dto.ReadingResults.*;
import vn.farm.sensordata.api.dto.ReadingView;
import vn.farm.sensordata.persistence.ReadingQueryRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ReadingQueryService {
    private static final Instant SQL_MIN = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant SQL_MAX = Instant.parse("9999-12-31T23:59:59.999Z");
    private static final int MAX_BUCKETS = 1_000;
    private static final int MAX_POINTS = 10_000;
    private static final TypeReference<Page> LATEST_RESULT = new TypeReference<>() {};
    private static final TypeReference<List<Statistics>> STATISTICS_RESULT = new TypeReference<>() {};
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "id", "id", "measuredAt", "measured_at", "collectedAt", "collected_at",
            "receivedAt", "received_at", "sensorValue", "sensor_value",
            "nodeId", "node_id", "sensorType", "sensor_code");

    private final ReadingQueryRepository queries;
    private final ReadingRedisCache cache;

    public ReadingQueryService(ReadingQueryRepository queries, ReadingRedisCache cache) {
        this.queries = queries;
        this.cache = cache;
    }

    public ReadingView getReading(long id) {
        if (id < 1) {
            throw badRequest("id must be positive");
        }
        return queries.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Sensor reading not found"));
    }

    public Page list(ReadingFilter filter, ReadingPageRequest page, boolean latest) {
        validateFilter(filter);
        String column = SORT_COLUMNS.get(page.sortBy());
        if (column == null) {
            throw badRequest("Unknown sortBy field");
        }
        boolean ascending = switch (page.sortDirection()) {
            case "asc" -> true;
            case "desc" -> false;
            default -> throw badRequest("sortDirection must be asc or desc");
        };
        if (latest) {
            return cache.getOrLoad("latest", List.of(filter, page), LATEST_RESULT,
                    () -> queries.findPage(filter, page, column, ascending, true));
        }
        return queries.findPage(filter, page, column, ascending, false);
    }

    public Page nodeHistory(String nodeId, ReadingFilter filter, ReadingPageRequest page) {
        if (nodeId.length() > 100 || (filter.nodeId() != null && !filter.nodeId().equals(nodeId))) {
            throw badRequest("The nodeId filter must match the path and contain at most 100 characters");
        }
        return list(filter.forNode(nodeId), page, false);
    }

    public List<Statistics> statistics(ReadingFilter filter) {
        validateFilter(filter);
        ReadingFilter effective = forAggregates(filter);
        return cache.getOrLoad("statistics", effective, STATISTICS_RESULT, () -> queries.statistics(effective));
    }

    public List<TimePoint> timeSeries(ReadingFilter filter, ReadingInterval interval) {
        validateFilter(filter);
        if (filter.from() == null || filter.to() == null) {
            throw badRequest("Time series requires from and to");
        }
        long bucketCount = interval.bucketNumber(filter.to().minusNanos(1))
                - interval.bucketNumber(filter.from()) + 1;
        if (bucketCount > MAX_BUCKETS) {
            throw badRequest("Time series allows at most " + MAX_BUCKETS + " intervals");
        }
        List<TimePoint> points = queries.timeSeries(forAggregates(filter), interval, MAX_POINTS + 1);
        if (points.size() > MAX_POINTS) {
            throw badRequest("Time series allows at most " + MAX_POINTS + " points; narrow the filters");
        }
        return points;
    }

    public Quality quality(ReadingFilter filter) {
        validateFilter(filter);
        return queries.quality(filter);
    }

    private static ReadingFilter forAggregates(ReadingFilter filter) {
        return filter.validationStatus() == null ? filter.withStatus(Status.VALID) : filter;
    }

    private static void validateFilter(ReadingFilter filter) {
        requireSqlTime(filter.from());
        requireSqlTime(filter.to());
        if (filter.from() != null && filter.to() != null && !filter.from().isBefore(filter.to())) {
            throw badRequest("from must precede to");
        }
        if ((filter.nodeId() != null && filter.nodeId().isBlank())
                || (filter.sensorType() != null && filter.sensorType().isBlank())
                || (filter.unit() != null && filter.unit().isBlank())) {
            throw badRequest("nodeId, sensorType and unit filters must contain a value");
        }
    }

    private static void requireSqlTime(Instant time) {
        if (time != null && (time.isBefore(SQL_MIN) || time.isAfter(SQL_MAX))) {
            throw badRequest("Query timestamp is outside the SQL Server range");
        }
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
