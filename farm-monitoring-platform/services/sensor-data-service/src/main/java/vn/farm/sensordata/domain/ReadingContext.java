package vn.farm.sensordata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public record ReadingContext(
        @Column(name = "farm_id") UUID farmId,
        @Column(name = "zone_id") UUID zoneId,
        @Column(name = "mission_id") UUID missionId,
        @Column(name = "execution_id") UUID executionId
) {}
