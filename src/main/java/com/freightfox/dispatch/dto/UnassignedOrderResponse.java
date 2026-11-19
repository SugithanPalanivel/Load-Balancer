package com.freightfox.dispatch.dto;

public record UnassignedOrderResponse(OrderResponse order, String reason) {
}
