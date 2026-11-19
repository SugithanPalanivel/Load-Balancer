package com.freightfox.dispatch.strategy;

import java.util.List;

public record DispatchResult(List<VehicleRoute> routes, List<UnassignedOrder> unassignedOrders) {
}
