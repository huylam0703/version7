package vn.farm.sensordata.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.farm.sensordata.domain.ReadingValueKind;

import java.time.Instant;
import java.util.List;

public final class ReadingResults {
    private ReadingResults() {}

    public enum WriteStatus { VALID, INVALID, DUPLICATE }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Written(WriteStatus status, String message, Long id) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BatchItem(int index, WriteStatus status, Long id) {}

    public record Batch(int total, int validCount, int invalidCount, int duplicateCount,
                        List<BatchItem> items) {}

    public record Page(List<ReadingView> items, int page, int pageSize, long total) {}

    public record Statistics(String sensorType, String unit, ReadingValueKind valueKind,
                             Integer adcResolutionBits, double min, double max, double avg, long count) {}

    public record TimePoint(Instant bucketStart, String sensorType, String unit, ReadingValueKind valueKind,
                            Integer adcResolutionBits, double min, double max, double avg, long count) {}

    public record Quality(long total, long validCount, long invalidCount,
                          long missingMetadataCount, double invalidPercent) {}
}
