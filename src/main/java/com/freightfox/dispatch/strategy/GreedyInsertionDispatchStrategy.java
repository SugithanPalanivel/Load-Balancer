package com.freightfox.dispatch.strategy;

import com.freightfox.dispatch.model.DeliveryOrder;
import com.freightfox.dispatch.model.Vehicle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Greedy cheapest-insertion heuristic with 2-opt clean-up.
 *
 * <ol>
 *   <li>Orders are processed HIGH, then MEDIUM, then LOW; within a priority, heavier first
 *       (first-fit-decreasing keeps capacity usage tight).</li>
 *   <li>Each order goes to the vehicle that can carry it and whose route grows the least
 *       (Haversine km). Ties prefer the vehicle with more spare capacity, then vehicleId.</li>
 *   <li>Each finished route is improved with 2-opt.</li>
 * </ol>
 * Complexity is roughly O(orders x total stops) for assignment plus O(stops^2) per route for 2-opt.
 */
@Component
public class GreedyInsertionDispatchStrategy implements DispatchStrategy {

    private static final Comparator<DeliveryOrder> ORDER_SEQUENCE = Comparator
            .comparingInt((DeliveryOrder o) -> o.getPriority().rank())
            .thenComparing(Comparator.comparingDouble(DeliveryOrder::getPackageWeight).reversed())
            .thenComparing(DeliveryOrder::getOrderId);

    @Override
    public DispatchResult dispatch(List<DeliveryOrder> orders, List<Vehicle> vehicles) {
        List<VehicleRoute> routes = vehicles.stream()
                .sorted(Comparator.comparing(Vehicle::getVehicleId))
                .map(VehicleRoute::new)
                .toList();
        double maxCapacity = vehicles.stream().mapToDouble(Vehicle::getCapacity).max().orElse(0);

        List<UnassignedOrder> unassigned = new ArrayList<>();
        List<DeliveryOrder> sequence = new ArrayList<>(orders);
        sequence.sort(ORDER_SEQUENCE);

        for (DeliveryOrder order : sequence) {
            if (order.getPackageWeight() > maxCapacity) {
                unassigned.add(new UnassignedOrder(order, "Package weight " + order.getPackageWeight()
                        + " exceeds the capacity of every vehicle (max " + maxCapacity + ")"));
                continue;
            }
            VehicleRoute bestRoute = null;
            VehicleRoute.Insertion bestInsertion = null;
            for (VehicleRoute route : routes) {
                if (!route.canFit(order)) {
                    continue;
                }
                VehicleRoute.Insertion candidate = route.bestInsertion(order);
                if (bestRoute == null || isBetter(candidate, route, bestInsertion, bestRoute)) {
                    bestRoute = route;
                    bestInsertion = candidate;
                }
            }
            if (bestRoute == null) {
                unassigned.add(new UnassignedOrder(order,
                        "No vehicle has enough remaining capacity for package weight " + order.getPackageWeight()));
            } else {
                bestRoute.insert(order, bestInsertion);
            }
        }

        routes.forEach(VehicleRoute::optimize);
        return new DispatchResult(routes, unassigned);
    }

    private boolean isBetter(VehicleRoute.Insertion candidate, VehicleRoute route,
                             VehicleRoute.Insertion best, VehicleRoute bestRoute) {
        double diff = candidate.extraKm() - best.extraKm();
        if (Math.abs(diff) > 1e-9) {
            return diff < 0;
        }
        return route.remainingCapacity() > bestRoute.remainingCapacity() + 1e-9;
    }
}
