package vn.farm.sensordata.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReadingBatchRequest(
        @NotNull @Size(min = 1, max = 500) List<@NotNull @Valid ReadingSubmission> readings
) {}
