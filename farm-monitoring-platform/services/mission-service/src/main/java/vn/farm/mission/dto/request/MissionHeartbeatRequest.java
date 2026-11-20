package vn.farm.mission.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionHeartbeatRequest {

    @NotNull
    UUID executionId;
    private UUID instanceId;
    private Long recoveryGeneration;
}