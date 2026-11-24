package vn.farm.sensordata.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ReadingValueKind {
    PHYSICAL,
    CONVERTED,
    ADC_RAW;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ReadingValueKind fromCode(String code) {
        return valueOf(code);
    }
}
