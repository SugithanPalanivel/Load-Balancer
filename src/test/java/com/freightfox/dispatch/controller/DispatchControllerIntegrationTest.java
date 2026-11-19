package com.freightfox.dispatch.controller;

import com.freightfox.dispatch.repository.OrderRepository;
import com.freightfox.dispatch.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DispatchControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired OrderRepository orderRepository;
    @Autowired VehicleRepository vehicleRepository;

    @BeforeEach
    void clean() {
        orderRepository.deleteAll();
        vehicleRepository.deleteAll();
    }

    private static String order(String id, double lat, double lon, double weight, String priority) {
        return """
            {"orderId":"%s","latitude":%s,"longitude":%s,"address":"Addr %s","packageWeight":%s,"priority":"%s"}
            """.formatted(id, lat, lon, id, weight, priority);
    }

    private static String vehicle(String id, double capacity, double lat, double lon) {
        return """
            {"vehicleId":"%s","capacity":%s,"currentLatitude":%s,"currentLongitude":%s,"currentAddress":"Depot %s"}
            """.formatted(id, capacity, lat, lon, id);
    }

    private void postOrders(String... orders) throws Exception {
        mvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orders\":[" + String.join(",", orders) + "]}"))
                .andExpect(status().isOk());
    }

    private void postVehicles(String... vehicles) throws Exception {
        mvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicles\":[" + String.join(",", vehicles) + "]}"))
                .andExpect(status().isOk());
    }

    @Test
    void acceptsOrders() throws Exception {
        mvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orders\":[" + order("ORD001", 12.9716, 77.5946, 10, "HIGH") + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Delivery orders accepted."))
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void acceptsVehicles() throws Exception {
        mvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicles\":[" + vehicle("VEH001", 100, 12.97, 77.64) + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Vehicle details accepted."))
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void acceptsLowercasePriority() throws Exception {
        postOrders(order("ORD001", 12.9716, 77.5946, 10, "high"));
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void upsertUpdatesExistingOrderInsteadOfDuplicating() throws Exception {
        postOrders(order("ORD001", 12.9716, 77.5946, 10, "HIGH"));
        postOrders(order("ORD001", 12.9716, 77.5946, 55, "LOW"));

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderRepository.findById("ORD001").orElseThrow().getPackageWeight()).isEqualTo(55);
    }

    @Test
    void rejectsInvalidLatitude() throws Exception {
        mvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orders\":[" + order("ORD001", 123.0, 77.5946, 10, "HIGH") + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.errors", hasItem(containsString("latitude must be between -90 and 90"))));
    }

    @Test
    void rejectsNonPositiveWeight() throws Exception {
        mvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orders\":[" + order("ORD001", 12.9, 77.5, 0, "HIGH") + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("packageWeight must be greater than 0"))));
    }

    @Test
    void rejectsUnknownPriority() throws Exception {
        mvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orders\":[" + order("ORD001", 12.9, 77.5, 10, "URGENT") + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("priority must be HIGH, MEDIUM or LOW")));
    }

    @Test
    void rejectsEmptyOrderList() throws Exception {
        mvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content("{\"orders\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("at least one order"))));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void rejectsDuplicateOrderIdsInOneRequest() throws Exception {
        mvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orders\":[" + order("ORD001", 12.9, 77.5, 10, "HIGH") + ","
                                + order("ORD001", 12.9, 77.5, 20, "LOW") + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Duplicate orderId")));
    }

    @Test
    void rejectsInvalidVehicle() throws Exception {
        mvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicles\":[" + vehicle("VEH001", -5, 12.97, 77.64) + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("capacity must be greater than 0"))));
    }

    @Test
    void planWithoutVehiclesReturnsConflict() throws Exception {
        postOrders(order("ORD001", 12.9, 77.5, 10, "HIGH"));
        mvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("No vehicles")));
    }

    @Test
    void planWithoutOrdersReturnsConflict() throws Exception {
        postVehicles(vehicle("VEH001", 100, 12.97, 77.64));
        mvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("No delivery orders")));
    }

    @Test
    void planAssignsOrdersToNearestVehicleWithLoadAndDistance() throws Exception {
        postOrders(order("ORD001", 12.9716, 77.5946, 10, "HIGH"),
                order("ORD002", 13.0827, 80.2707, 20, "MEDIUM"));
        postVehicles(vehicle("VEH001", 100, 12.9716, 77.6413), vehicle("VEH002", 150, 13.0674, 80.2376));

        mvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchPlan[0].vehicleId").value("VEH001"))
                .andExpect(jsonPath("$.dispatchPlan[0].totalLoad").value(10.0))
                .andExpect(jsonPath("$.dispatchPlan[0].totalDistance").value(containsString(" km")))
                .andExpect(jsonPath("$.dispatchPlan[0].assignedOrders[0].orderId").value("ORD001"))
                .andExpect(jsonPath("$.dispatchPlan[0].assignedOrders[0].address").value("Addr ORD001"))
                .andExpect(jsonPath("$.dispatchPlan[1].vehicleId").value("VEH002"))
                .andExpect(jsonPath("$.dispatchPlan[1].totalLoad").value(20.0))
                .andExpect(jsonPath("$.dispatchPlan[1].assignedOrders[0].orderId").value("ORD002"))
                .andExpect(jsonPath("$.unassignedOrders").isEmpty());
    }

    @Test
    void planReportsUnassignableOrders() throws Exception {
        postOrders(order("HUGE", 12.9, 77.6, 999, "HIGH"), order("OK", 12.9, 77.6, 10, "LOW"));
        postVehicles(vehicle("VEH001", 100, 12.9, 77.6));

        mvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchPlan[0].assignedOrders[0].orderId").value("OK"))
                .andExpect(jsonPath("$.unassignedOrders[0].order.orderId").value("HUGE"))
                .andExpect(jsonPath("$.unassignedOrders[0].reason").value(containsString("exceeds the capacity")));
    }

    @Test
    void swaggerApiDocsAreExposed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/dispatch/plan']").exists());
    }
}
