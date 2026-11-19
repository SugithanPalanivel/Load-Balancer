package com.freightfox.dispatch.strategy;

import com.freightfox.dispatch.model.DeliveryOrder;
import com.freightfox.dispatch.model.Priority;
import com.freightfox.dispatch.model.Vehicle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GreedyInsertionDispatchStrategyTest {

    private final DispatchStrategy strategy = new GreedyInsertionDispatchStrategy();

    private static DeliveryOrder order(String id, double lat, double lon, double weight, Priority p) {
        return new DeliveryOrder(id, lat, lon, "addr-" + id, weight, p);
    }

    private static Vehicle vehicle(String id, double cap, double lat, double lon) {
        return new Vehicle(id, cap, lat, lon, "depot-" + id);
    }

    private static List<String> idsOf(VehicleRoute route) {
        return route.getStops().stream().map(DeliveryOrder::getOrderId).toList();
    }

    @Test
    void assignsOrderToNearestVehicle() {
        DispatchResult r = strategy.dispatch(
                List.of(order("O1", 12.97, 77.59, 10, Priority.HIGH)),
                List.of(vehicle("BLR", 100, 12.97, 77.64), vehicle("CHN", 100, 13.07, 80.24)));

        assertThat(idsOf(route(r, "BLR"))).containsExactly("O1");
        assertThat(route(r, "CHN").getStops()).isEmpty();
        assertThat(r.unassignedOrders()).isEmpty();
    }

    @Test
    void neverExceedsCapacity() {
        List<DeliveryOrder> orders = List.of(
                order("O1", 12.9, 77.6, 60, Priority.HIGH),
                order("O2", 12.9, 77.6, 60, Priority.HIGH),
                order("O3", 12.9, 77.6, 30, Priority.MEDIUM));
        DispatchResult r = strategy.dispatch(orders, List.of(vehicle("V1", 100, 12.9, 77.6)));

        assertThat(route(r, "V1").getLoad()).isLessThanOrEqualTo(100);
        assertThat(r.unassignedOrders()).hasSize(1);
    }

    @Test
    void highPriorityIsAssignedBeforeLowWhenCapacityIsScarce() {
        List<DeliveryOrder> orders = List.of(
                order("LOW", 12.9, 77.6, 60, Priority.LOW),
                order("HIGH", 12.9, 77.6, 60, Priority.HIGH));
        DispatchResult r = strategy.dispatch(orders, List.of(vehicle("V1", 100, 12.9, 77.6)));

        assertThat(idsOf(route(r, "V1"))).containsExactly("HIGH");
        assertThat(r.unassignedOrders()).extracting(u -> u.order().getOrderId()).containsExactly("LOW");
    }

    @Test
    void priorityOrderIsHighThenMediumThenLow() {
        List<DeliveryOrder> orders = List.of(
                order("L", 12.9, 77.6, 40, Priority.LOW),
                order("M", 12.9, 77.6, 40, Priority.MEDIUM),
                order("H", 12.9, 77.6, 40, Priority.HIGH));
        DispatchResult r = strategy.dispatch(orders, List.of(vehicle("V1", 80, 12.9, 77.6)));

        assertThat(idsOf(route(r, "V1"))).containsExactlyInAnyOrder("H", "M");
        assertThat(r.unassignedOrders()).extracting(u -> u.order().getOrderId()).containsExactly("L");
    }

    @Test
    void orderHeavierThanEveryVehicleIsUnassignedWithReason() {
        DispatchResult r = strategy.dispatch(
                List.of(order("BIG", 12.9, 77.6, 500, Priority.HIGH)),
                List.of(vehicle("V1", 100, 12.9, 77.6), vehicle("V2", 150, 12.9, 77.6)));

        assertThat(r.unassignedOrders()).hasSize(1);
        assertThat(r.unassignedOrders().get(0).reason()).contains("exceeds the capacity of every vehicle");
    }

    @Test
    void orderThatFitsExactlyIsAssigned() {
        DispatchResult r = strategy.dispatch(
                List.of(order("O1", 12.9, 77.6, 100, Priority.LOW)),
                List.of(vehicle("V1", 100, 12.9, 77.6)));

        assertThat(r.unassignedOrders()).isEmpty();
        assertThat(route(r, "V1").getLoad()).isEqualTo(100);
    }

    @Test
    void spillsToAnotherVehicleWhenNearestIsFull() {
        List<DeliveryOrder> orders = List.of(
                order("O1", 12.90, 77.60, 80, Priority.HIGH),
                order("O2", 12.91, 77.61, 80, Priority.HIGH));
        DispatchResult r = strategy.dispatch(orders,
                List.of(vehicle("NEAR", 100, 12.90, 77.60), vehicle("FAR", 100, 13.50, 78.00)));

        assertThat(route(r, "NEAR").getStops()).hasSize(1);
        assertThat(route(r, "FAR").getStops()).hasSize(1);
        assertThat(r.unassignedOrders()).isEmpty();
    }

    @Test
    void routeStopsAreOrderedToAvoidBacktracking() {
        // Vehicle at lon 0; stops on a line at lon 3, 1, 2 -> best visiting order is 1, 2, 3.
        List<DeliveryOrder> orders = List.of(
                order("A", 0, 3, 10, Priority.HIGH),
                order("B", 0, 1, 10, Priority.HIGH),
                order("C", 0, 2, 10, Priority.HIGH));
        DispatchResult r = strategy.dispatch(orders, List.of(vehicle("V1", 100, 0, 0)));

        assertThat(idsOf(route(r, "V1"))).containsExactly("B", "C", "A");
        assertThat(route(r, "V1").distanceKm()).isEqualTo(3 * 111.19, org.assertj.core.data.Offset.offset(0.2));
    }

    @Test
    void emptyOrderListGivesEmptyRoutes() {
        DispatchResult r = strategy.dispatch(List.of(), List.of(vehicle("V1", 100, 0, 0)));
        assertThat(r.routes()).hasSize(1);
        assertThat(r.routes().get(0).distanceKm()).isZero();
        assertThat(r.unassignedOrders()).isEmpty();
    }

    @Test
    void everyOrderAppearsExactlyOnce_andNoVehicleIsOverloaded_onLargeDataset() {
        Random rnd = new Random(42);
        List<DeliveryOrder> orders = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            orders.add(order("O" + i, 28.4 + rnd.nextDouble() * 0.4, 77.0 + rnd.nextDouble() * 0.5,
                    5 + rnd.nextInt(20), Priority.values()[rnd.nextInt(3)]));
        }
        List<Vehicle> vehicles = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            vehicles.add(vehicle("V" + i, 500, 28.4 + rnd.nextDouble() * 0.4, 77.0 + rnd.nextDouble() * 0.5));
        }

        DispatchResult r = strategy.dispatch(orders, vehicles);

        List<String> assigned = r.routes().stream().flatMap(rt -> idsOf(rt).stream()).toList();
        Set<String> unique = assigned.stream().collect(Collectors.toSet());
        assertThat(assigned).hasSize(unique.size());
        assertThat(assigned.size() + r.unassignedOrders().size()).isEqualTo(orders.size());
        r.routes().forEach(rt -> assertThat(rt.getLoad()).isLessThanOrEqualTo(rt.getVehicle().getCapacity()));
    }

    private static VehicleRoute route(DispatchResult r, String vehicleId) {
        return r.routes().stream().filter(x -> x.getVehicle().getVehicleId().equals(vehicleId)).findFirst().orElseThrow();
    }
}
