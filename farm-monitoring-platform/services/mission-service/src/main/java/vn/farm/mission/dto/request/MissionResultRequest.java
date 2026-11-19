package vn.farm.mission.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionResultRequest {
    @NotEmpty
    List<@NotNull @Valid MissionNodeResultRequest> results;

    @NotNull
    Boolean completed;

    @NotNull
    UUID executionId;
    private UUID instanceId;
    private Long recoveryGeneration;
}
