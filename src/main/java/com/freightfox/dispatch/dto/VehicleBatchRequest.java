package com.freightfox.dispatch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record VehicleBatchRequest(
        @NotEmpty(message = "vehicles must contain at least one vehicle") List<@Valid VehicleRequest> vehicles) {
}
