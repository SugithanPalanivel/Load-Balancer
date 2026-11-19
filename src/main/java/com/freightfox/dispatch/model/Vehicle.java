package com.freightfox.dispatch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    private String vehicleId;
    private double capacity;
    private double currentLatitude;
    private double currentLongitude;
    @Column(nullable = false)
    private String currentAddress;

    protected Vehicle() {
    }

    public Vehicle(String vehicleId, double capacity, double currentLatitude,
                   double currentLongitude, String currentAddress) {
        this.vehicleId = vehicleId;
        this.capacity = capacity;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.currentAddress = currentAddress;
    }

    public String getVehicleId() { return vehicleId; }
    public double getCapacity() { return capacity; }
    public double getCurrentLatitude() { return currentLatitude; }
    public double getCurrentLongitude() { return currentLongitude; }
    public String getCurrentAddress() { return currentAddress; }
}
