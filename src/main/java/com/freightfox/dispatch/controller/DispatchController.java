package com.freightfox.dispatch.controller;

import com.freightfox.dispatch.dto.ApiResponse;
import com.freightfox.dispatch.dto.DispatchPlanResponse;
import com.freightfox.dispatch.dto.ErrorResponse;
import com.freightfox.dispatch.dto.OrderBatchRequest;
import com.freightfox.dispatch.dto.VehicleBatchRequest;
import com.freightfox.dispatch.service.DispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dispatch")
@Tag(name = "Dispatch", description = "Submit orders and vehicles, then retrieve an optimized dispatch plan")
public class DispatchController {

    private final DispatchService service;

    public DispatchController(DispatchService service) {
        this.service = service;
    }

    @Operation(summary = "Submit delivery orders",
            description = "Upserts orders by orderId (existing ids are updated, new ones inserted).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Orders accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping("/orders")
    public ApiResponse submitOrders(@Valid @RequestBody OrderBatchRequest request) {
        service.saveOrders(request.orders());
        return ApiResponse.success("Delivery orders accepted.");
    }

    @Operation(summary = "Submit fleet details",
            description = "Upserts vehicles by vehicleId (existing ids are updated, new ones inserted).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vehicles accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping("/vehicles")
    public ApiResponse submitVehicles(@Valid @RequestBody VehicleBatchRequest request) {
        service.saveVehicles(request.vehicles());
        return ApiResponse.success("Vehicle details accepted.");
    }

    @Operation(summary = "Get the optimized dispatch plan",
            description = "HIGH priority orders are assigned first; each order goes to the vehicle with enough "
                    + "capacity that adds the least Haversine distance. Orders that cannot be assigned are "
                    + "returned in unassignedOrders with a reason.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dispatch plan"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Orders or vehicles have not been submitted yet",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping("/plan")
    public DispatchPlanResponse getPlan() {
        return service.generatePlan();
    }
}
