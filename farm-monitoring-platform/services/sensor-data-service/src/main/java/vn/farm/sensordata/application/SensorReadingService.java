package vn.farm.sensordata.application;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import vn.farm.sensordata.api.dto.ReadingResults.*;
import vn.farm.sensordata.api.dto.ReadingSubmission;
import vn.farm.sensordata.api.dto.SensorReadingRequest;
import vn.farm.sensordata.domain.ReadingContext;
import vn.farm.sensordata.domain.SensorReading;
import vn.farm.sensordata.domain.SensorType;
import vn.farm.sensordata.persistence.SensorReadingRepository;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

@Service
public class SensorReadingService {

    private static final LocalDateTime LAST_ROUNDABLE_SQL_TIMESTAMP =
            LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_499_999);

    private final SensorReadingRepository repository;

    public SensorReadingService(SensorReadingRepository repository) {
        this.repository = repository;
    }

    public Written saveReading(SensorReadingRequest request) {
        return persist(prepare(request, null));
    }

    public Written saveReading(ReadingSubmission submission) {
        return persist(prepare(submission.reading(), submission.context()));
    }

    public Batch saveBatch(List<ReadingSubmission> submissions) {
        List<PreparedReading> prepared = submissions.stream()
                .map(submission -> prepare(submission.reading(), submission.context()))
                .toList();
        var items = new ArrayList<BatchItem>(prepared.size());
        var counts = new EnumMap<WriteStatus, Integer>(WriteStatus.class);
        for (int index = 0; index < prepared.size(); index++) {
            Written written = persist(prepared.get(index));
            items.add(new BatchItem(index, written.status(), written.id()));
            counts.merge(written.status(), 1, Integer::sum);
        }
        return new Batch(prepared.size(), counts.getOrDefault(WriteStatus.VALID, 0),
                counts.getOrDefault(WriteStatus.INVALID, 0), counts.getOrDefault(WriteStatus.DUPLICATE, 0), items);
    }

    private PreparedReading prepare(SensorReadingRequest request, ReadingContext context) {
        if (!Double.isFinite(request.sensorValue())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sensorValue must be finite");
        }

        LocalDateTime measuredAt = toSqlTimestamp(request.measuredAt());
        LocalDateTime collectedAt = toSqlTimestamp(request.collectedAt());
        String inputCode = request.sensorType().strip().toUpperCase(Locale.ROOT);
        SensorType type = SensorType.findByCode(inputCode).orElse(null);
        List<String> matchingCodes = type == null ? List.of(inputCode) : type.codes();
        String unit = request.unit() == null ? null : request.unit().strip();
        Integer adcResolutionBits = request.adcResolutionBits() == null
                ? null : request.adcResolutionBits().intValueExact();
        boolean valid = type != null && type.isValid(inputCode, request.sensorValue(), unit,
                request.valueKind(), adcResolutionBits);
        WriteStatus status = valid ? WriteStatus.VALID : WriteStatus.INVALID;
        SensorReading reading = new SensorReading();
        reading.setNodeId(request.nodeId());
        reading.setSensorType(type == null ? request.sensorType() : type.name());
        reading.setSensorValue(request.sensorValue());
        reading.setUnit(unit);
        reading.setValueKind(request.valueKind());
        reading.setAdcResolutionBits(adcResolutionBits);
        reading.setMeasuredAt(measuredAt);
        reading.setCollectedAt(collectedAt);
        reading.setLatitude(request.latitude());
        reading.setLongitude(request.longitude());
        reading.setValidationStatus(status.name());
        reading.setContext(context);
        return new PreparedReading(reading, matchingCodes, status);
    }

    private Written persist(PreparedReading prepared) {
        SensorReading reading = prepared.reading();
        if (repository.existsReading(reading.getNodeId(), prepared.matchingCodes(), reading.getMeasuredAt())) {
            return duplicateResult();
        }
        try {
            // The repository transaction rolls back before we return a duplicate response.
            SensorReading saved = repository.saveAndFlush(reading);
            return new Written(prepared.status(), "Sensor reading saved", saved.getId());
        } catch (DataIntegrityViolationException error) {
            if (isDuplicateConstraint(error)) {
                return duplicateResult();
            }
            throw error;
        }
    }

    @Transactional(readOnly = true)
    public List<SensorReading> getLatestReadings(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));

        return repository.findAll(
                PageRequest.of(
                        0,
                        safeLimit,
                        Sort.by(Sort.Direction.DESC, "measuredAt")
                )
        ).getContent();
    }

    private static LocalDateTime toSqlTimestamp(LocalDateTime timestamp) {
        if (timestamp.getYear() < 1 || timestamp.isAfter(LAST_ROUNDABLE_SQL_TIMESTAMP)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Timestamp is outside the SQL Server range");
        }
        return timestamp.plusNanos(500_000).truncatedTo(ChronoUnit.MILLIS);
    }

    private static boolean isDuplicateConstraint(DataIntegrityViolationException error) {
        Throwable cause = error.getMostSpecificCause();
        if (cause instanceof SQLException sql && (sql.getErrorCode() == 2601 || sql.getErrorCode() == 2627)) {
            for (Throwable nested = error.getCause(); nested != null; nested = nested.getCause()) {
                if (nested instanceof ConstraintViolationException violation) {
                    return SensorReading.UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
                }
            }
        }
        return false;
    }

    private static Written duplicateResult() {
        return new Written(WriteStatus.DUPLICATE, "Sensor reading already exists", null);
    }

    private record PreparedReading(SensorReading reading, List<String> matchingCodes, WriteStatus status) {}
}
