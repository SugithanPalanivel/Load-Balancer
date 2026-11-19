package com.freightfox.dispatch.dto;

import java.util.List;

/** assignedOrders are listed in the order the vehicle should visit them. */
public record VehiclePlanResponse(String vehicleId, String currentAddress, double capacity,
                                  double totalLoad, String totalDistance,
                                  List<OrderResponse> assignedOrders) {
}
