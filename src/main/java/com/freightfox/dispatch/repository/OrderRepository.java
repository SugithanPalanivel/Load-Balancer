package com.freightfox.dispatch.repository;

import com.freightfox.dispatch.model.DeliveryOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<DeliveryOrder, String> {
}
