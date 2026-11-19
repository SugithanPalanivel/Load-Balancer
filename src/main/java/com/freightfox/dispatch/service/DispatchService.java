package com.freightfox.dispatch.service;

import com.freightfox.dispatch.dto.DispatchPlanResponse;
import com.freightfox.dispatch.dto.OrderRequest;
import com.freightfox.dispatch.dto.OrderResponse;
import com.freightfox.dispatch.dto.UnassignedOrderResponse;
import com.freightfox.dispatch.dto.VehiclePlanResponse;
import com.freightfox.dispatch.dto.VehicleRequest;
import com.freightfox.dispatch.exception.InvalidInputException;
import com.freightfox.dispatch.exception.NoDataException;
import com.freightfox.dispatch.model.DeliveryOrder;
import com.freightfox.dispatch.model.Vehicle;
import com.freightfox.dispatch.repository.OrderRepository;
import com.freightfox.dispatch.repository.VehicleRepository;
import com.freightfox.dispatch.strategy.DispatchResult;
import com.freightfox.dispatch.strategy.DispatchStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

@Service
public class DispatchService {

    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final DispatchStrategy strategy;

    public DispatchService(OrderRepository orderRepository, VehicleRepository vehicleRepository,
                           DispatchStrategy strategy) {
        this.orderRepository = orderRepository;
        this.vehicleRepository = vehicleRepository;
        this.strategy = strategy;
    }

    /** Upsert: an existing orderId is updated, a new one is inserted. One batched saveAll call. */
    @Transactional
    public int saveOrders(List<OrderRequest> requests) {
        rejectDuplicates(requests, OrderRequest::orderId, "orderId");
        List<DeliveryOrder> orders = requests.stream()
                .map(r -> new DeliveryOrder(r.orderId().trim(), r.latitude(), r.longitude(), r.address().trim(),
                        r.packageWeight(), r.priority()))
                .toList();
        orderRepository.saveAll(orders);
        return orders.size();
    }

    /** Upsert: an existing vehicleId is updated, a new one is inserted. */
    @Transactional
    public int saveVehicles(List<VehicleRequest> requests) {
        rejectDuplicates(requests, VehicleRequest::vehicleId, "vehicleId");
        List<Vehicle> vehicles = requests.stream()
                .map(r -> new Vehicle(r.vehicleId().trim(), r.capacity(), r.currentLatitude(),
                        r.currentLongitude(), r.currentAddress().trim()))
                .toList();
        vehicleRepository.saveAll(vehicles);
        return vehicles.size();
    }

    @Transactional(readOnly = true)
    public DispatchPlanResponse generatePlan() {
        List<Vehicle> vehicles = vehicleRepository.findAll();
        List<DeliveryOrder> orders = orderRepository.findAll();
        if (vehicles.isEmpty()) {
            throw new NoDataException("No vehicles available. POST /api/dispatch/vehicles first.");
        }
        if (orders.isEmpty()) {
            throw new NoDataException("No delivery orders available. POST /api/dispatch/orders first.");
        }

        DispatchResult result = strategy.dispatch(orders, vehicles);

        List<VehiclePlanResponse> plan = result.routes().stream()
                .map(route -> new VehiclePlanResponse(
                        route.getVehicle().getVehicleId(),
                        route.getVehicle().getCurrentAddress(),
                        route.getVehicle().getCapacity(),
                        route.getLoad(),
                        String.format(Locale.ROOT, "%.2f km", route.distanceKm()),
                        route.getStops().stream().map(OrderResponse::from).toList()))
                .toList();
        List<UnassignedOrderResponse> unassigned = result.unassignedOrders().stream()
                .map(u -> new UnassignedOrderResponse(OrderResponse.from(u.order()), u.reason()))
                .toList();
        return new DispatchPlanResponse(plan, unassigned);
    }

    private <T> void rejectDuplicates(List<T> items, Function<T, String> idOf, String label) {
        Set<String> seen = new HashSet<>();
        Set<String> dupes = new HashSet<>();
        for (T item : items) {
            String id = idOf.apply(item).trim();
            if (!seen.add(id)) {
                dupes.add(id);
            }
        }
        if (!dupes.isEmpty()) {
            throw new InvalidInputException("Duplicate " + label + " in request: " + String.join(", ", dupes));
        }
    }
}
