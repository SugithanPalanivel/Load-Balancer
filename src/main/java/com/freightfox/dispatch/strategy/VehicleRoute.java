package com.freightfox.dispatch.strategy;

import com.freightfox.dispatch.model.DeliveryOrder;
import com.freightfox.dispatch.model.Vehicle;
import com.freightfox.dispatch.util.HaversineCalculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ordered, open route: the vehicle starts at its current location and visits each stop in order.
 */
public class VehicleRoute {

    private static final double EPS = 1e-9;

    public record Insertion(int position, double extraKm) {
    }

    private final Vehicle vehicle;
    private final List<DeliveryOrder> stops = new ArrayList<>();
    private double load;

    public VehicleRoute(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public Vehicle getVehicle() { return vehicle; }
    public List<DeliveryOrder> getStops() { return Collections.unmodifiableList(stops); }
    public double getLoad() { return load; }
    public double remainingCapacity() { return vehicle.getCapacity() - load; }

    public boolean canFit(DeliveryOrder order) {
        return order.getPackageWeight() <= remainingCapacity() + EPS;
    }

    /** Cheapest position (smallest extra km) at which the order can be added to this route. */
    public Insertion bestInsertion(DeliveryOrder order) {
        int n = stops.size();
        Insertion best = null;
        for (int i = 0; i <= n; i++) {
            double[] prev = pointBefore(i);
            double toOrder = dist(prev[0], prev[1], order.getLatitude(), order.getLongitude());
            double extra;
            if (i == n) {
                extra = toOrder;
            } else {
                DeliveryOrder next = stops.get(i);
                extra = toOrder
                        + dist(order.getLatitude(), order.getLongitude(), next.getLatitude(), next.getLongitude())
                        - dist(prev[0], prev[1], next.getLatitude(), next.getLongitude());
            }
            if (best == null || extra < best.extraKm() - EPS) {
                best = new Insertion(i, extra);
            }
        }
        return best;
    }

    public void insert(DeliveryOrder order, Insertion insertion) {
        stops.add(insertion.position(), order);
        load += order.getPackageWeight();
    }

    public double distanceKm() {
        double total = 0;
        double[] prev = {vehicle.getCurrentLatitude(), vehicle.getCurrentLongitude()};
        for (DeliveryOrder s : stops) {
            total += dist(prev[0], prev[1], s.getLatitude(), s.getLongitude());
            prev = new double[]{s.getLatitude(), s.getLongitude()};
        }
        return total;
    }

    /** 2-opt local search: reverse sub-sequences while that shortens the route. */
    public void optimize() {
        int n = stops.size();
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 0; i < n - 1; i++) {
                for (int j = i + 1; j < n; j++) {
                    double[] prev = pointBefore(i);
                    DeliveryOrder a = stops.get(i);
                    DeliveryOrder b = stops.get(j);
                    double oldCost = dist(prev[0], prev[1], a.getLatitude(), a.getLongitude());
                    double newCost = dist(prev[0], prev[1], b.getLatitude(), b.getLongitude());
                    if (j < n - 1) {
                        DeliveryOrder next = stops.get(j + 1);
                        oldCost += dist(b.getLatitude(), b.getLongitude(), next.getLatitude(), next.getLongitude());
                        newCost += dist(a.getLatitude(), a.getLongitude(), next.getLatitude(), next.getLongitude());
                    }
                    if (newCost < oldCost - EPS) {
                        Collections.reverse(stops.subList(i, j + 1));
                        improved = true;
                    }
                }
            }
        }
    }

    private double[] pointBefore(int index) {
        if (index == 0) {
            return new double[]{vehicle.getCurrentLatitude(), vehicle.getCurrentLongitude()};
        }
        DeliveryOrder p = stops.get(index - 1);
        return new double[]{p.getLatitude(), p.getLongitude()};
    }

    private static double dist(double lat1, double lon1, double lat2, double lon2) {
        return HaversineCalculator.distanceKm(lat1, lon1, lat2, lon2);
    }
}
