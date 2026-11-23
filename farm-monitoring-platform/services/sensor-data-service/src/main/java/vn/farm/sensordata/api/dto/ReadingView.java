package vn.farm.sensordata.api.dto;

import vn.farm.sensordata.domain.ReadingValueKind;

import java.time.Instant;
import java.util.UUID;

public record ReadingView(long id, String nodeId, String sensorType, double sensorValue,
                          String unit, ReadingValueKind valueKind, Integer adcResolutionBits,
                          Instant measuredAt, Instant collectedAt, Instant receivedAt,
                          double latitude, double longitude, String validationStatus,
                          UUID farmId, UUID zoneId, UUID missionId, UUID executionId) {}
