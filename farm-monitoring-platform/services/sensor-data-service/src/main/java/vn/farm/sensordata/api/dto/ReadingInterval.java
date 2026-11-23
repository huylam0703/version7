package vn.farm.sensordata.api.dto;

import java.time.Instant;

public enum ReadingInterval {
    MINUTE(60),
    HOUR(3_600),
    DAY(86_400);

    private final long seconds;

    ReadingInterval(long seconds) {
        this.seconds = seconds;
    }

    public long bucketNumber(Instant time) {
        return Math.floorDiv(time.getEpochSecond(), seconds);
    }
}
