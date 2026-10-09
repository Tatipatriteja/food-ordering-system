package com.example.demo.repository;

import com.example.demo.entity.FoodOrder;
import com.example.demo.entity.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodOrderRepository extends JpaRepository<FoodOrder, Long> {

    /** Newest first. The entity graph loads the items in the same query (avoids the N+1 problem). */
    @EntityGraph(attributePaths = "items")
    List<FoodOrder> findAllByOrderByOrderTimeDescIdDesc();

    @EntityGraph(attributePaths = "items")
    List<FoodOrder> findByStatusOrderByOrderTimeDescIdDesc(OrderStatus status);
}
