package com.freightfox.dispatch.strategy;

import com.freightfox.dispatch.model.DeliveryOrder;

public record UnassignedOrder(DeliveryOrder order, String reason) {
}
