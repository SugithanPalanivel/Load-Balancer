package com.freightfox.dispatch.dto;

import com.freightfox.dispatch.model.DeliveryOrder;
import com.freightfox.dispatch.model.Priority;

public record OrderResponse(String orderId, double latitude, double longitude, String address,
                            double packageWeight, Priority priority) {
    public static OrderResponse from(DeliveryOrder o) {
        return new OrderResponse(o.getOrderId(), o.getLatitude(), o.getLongitude(), o.getAddress(),
                o.getPackageWeight(), o.getPriority());
    }
}
