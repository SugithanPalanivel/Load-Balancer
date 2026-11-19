package com.freightfox.dispatch.strategy;

import com.freightfox.dispatch.model.DeliveryOrder;
import com.freightfox.dispatch.model.Vehicle;

import java.util.List;

/** Strategy pattern: swap in a different allocation algorithm without touching the service. */
public interface DispatchStrategy {
    DispatchResult dispatch(List<DeliveryOrder> orders, List<Vehicle> vehicles);
}
