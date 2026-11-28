package vn.farm.mission.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionProgressRequest {

    @NotNull
    UUID executionId;

    @Min(1)
    long sequence;

    @NotNull
    Short waypointSeq;

    @NotNull
    Instant reachedAt;

    @Valid
    @Builder.Default
    List<MissionNodeProgressRequest> nodeResults = List.of();

    private UUID instanceId;
    private Long recoveryGeneration;
}