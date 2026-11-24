package vn.farm.sensordata.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum SensorType {
    AIR_TEMP("°C", -40, 85, "TEMPERATURE"),
    AIR_HUMIDITY("%", 0, 100, "HUMIDITY"),
    LIGHT("lux", 0, 100_000),
    SOIL_MOISTURE("%", 0, 100),
    WATER_LEVEL("cm", 0, 500),
    PH("pH", 0, 14);

    private final String physicalUnit;
    private final double min;
    private final double max;
    private final List<String> codes;
    private final String legacyCode;

    SensorType(String physicalUnit, double min, double max, String legacyCode) {
        this.physicalUnit = physicalUnit;
        this.min = min;
        this.max = max;
        this.legacyCode = legacyCode;
        this.codes = legacyCode == null ? List.of(name()) : List.of(name(), legacyCode);
    }

    SensorType(String physicalUnit, double min, double max) {
        this(physicalUnit, min, max, null);
    }

    public static Optional<SensorType> findByCode(String code) {
        return Arrays.stream(values()).filter(type -> type.codes.contains(code)).findFirst();
    }

    public List<String> codes() {
        return codes;
    }

    public boolean isValid(String code, double value, String unit, ReadingValueKind kind,
                           Integer adcResolutionBits) {
        if (unit == null && kind == null && adcResolutionBits == null) {
            // Only unambiguous legacy payloads keep the previous validation policy.
            return (this == PH || code.equals(legacyCode)) && isInPhysicalRange(value);
        }

        if (kind == ReadingValueKind.ADC_RAW) {
            return "ADC_COUNT".equals(unit) && adcResolutionBits != null
                    && adcResolutionBits >= 1 && adcResolutionBits <= 31
                    && value >= 0 && value < (1L << adcResolutionBits) && value == Math.rint(value);
        }

        return kind != null && adcResolutionBits == null && physicalUnit.equals(unit)
                && isInPhysicalRange(value);
    }

    private boolean isInPhysicalRange(double value) {
        return value >= min && value <= max;
    }
}
