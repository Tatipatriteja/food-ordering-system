package com.example.demo.service;

import com.example.demo.dto.OrderRequest;
import com.example.demo.dto.OrderResponse;
import com.example.demo.entity.FoodItem;
import com.example.demo.entity.FoodOrder;
import com.example.demo.entity.OrderStatus;
import com.example.demo.exception.InvalidStatusTransitionException;
import com.example.demo.exception.ItemUnavailableException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.FoodOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    FoodOrderRepository orders;
    @Mock
    FoodService foods;

    OrderService service;

    @BeforeEach
    void setUp() {
        BillingCalculator billing =
                new BillingCalculator(new BigDecimal("0.05"), new BigDecimal("500"), new BigDecimal("0.10"));
        service = new OrderService(orders, foods, billing, FIXED_CLOCK);
    }

    private static FoodItem food(long id, String name, String price, boolean available) {
        FoodItem item = new FoodItem(name, "Test", null, new BigDecimal(price));
        item.setId(id);
        item.setAvailable(available);
        return item;
    }

    private static OrderRequest.Line line(long foodId, int quantity) {
        return new OrderRequest.Line(foodId, quantity);
    }

    private void saveReturnsArgument() {
        when(orders.save(any(FoodOrder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void placeOrderBuildsItemisedBillAndSnapshotsPrices() {
        when(foods.getEntity(1L)).thenReturn(food(1, "Pizza", "249.00", true));
        saveReturnsArgument();

        OrderResponse order = service.place(new OrderRequest("  Teja ", null, List.of(line(1, 2))));

        assertThat(order.customerName()).isEqualTo("Teja");
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).foodName()).isEqualTo("Pizza");
        assertThat(order.items().get(0).lineTotal()).isEqualByComparingTo("498.00");
        assertThat(order.subtotal()).isEqualByComparingTo("498.00");
        assertThat(order.tax()).isEqualByComparingTo("24.90");
        assertThat(order.discount()).isEqualByComparingTo("0.00");
        assertThat(order.total()).isEqualByComparingTo("522.90");
        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.orderTime()).isEqualTo(LocalDateTime.of(2026, 10, 9, 10, 0));
    }

    @Test
    void discountIsAppliedFromTheThresholdUpwards() {
        when(foods.getEntity(1L)).thenReturn(food(1, "Biryani", "250.00", true));
        saveReturnsArgument();

        OrderResponse order = service.place(new OrderRequest("A", null, List.of(line(1, 2))));

        assertThat(order.subtotal()).isEqualByComparingTo("500.00");
        assertThat(order.discount()).isEqualByComparingTo("50.00");
        assertThat(order.total()).isEqualByComparingTo("475.00");
    }

    @Test
    void repeatedDishesAreMergedIntoOneLine() {
        when(foods.getEntity(1L)).thenReturn(food(1, "Fries", "89.00", true));
        saveReturnsArgument();

        OrderResponse order = service.place(new OrderRequest("A", null, List.of(line(1, 2), line(1, 1))));

        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).quantity()).isEqualTo(3);
    }

    @Test
    void mergedQuantityAbove50IsRejected() {
        assertThatThrownBy(() -> service.place(new OrderRequest("A", null, List.of(line(1, 30), line(1, 30)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
        verify(orders, never()).save(any());
    }

    @Test
    void blankPhoneIsStoredAsNull() {
        when(foods.getEntity(1L)).thenReturn(food(1, "Fries", "89.00", true));
        saveReturnsArgument();

        OrderResponse order = service.place(new OrderRequest("A", "   ", List.of(line(1, 1))));

        assertThat(order.customerPhone()).isNull();
    }

    @Test
    void unavailableDishIsRejectedAndNothingIsSaved() {
        when(foods.getEntity(2L)).thenReturn(food(2, "Brownie", "119.00", false));

        assertThatThrownBy(() -> service.place(new OrderRequest("A", null, List.of(line(2, 1)))))
                .isInstanceOf(ItemUnavailableException.class)
                .hasMessageContaining("Brownie");
        verify(orders, never()).save(any());
    }

    @Test
    void unknownDishPropagatesNotFound() {
        when(foods.getEntity(99L)).thenThrow(new NotFoundException("Food item 99 not found"));

        assertThatThrownBy(() -> service.place(new OrderRequest("A", null, List.of(line(99, 1)))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void validStatusChangeIsSaved() {
        FoodOrder existing = new FoodOrder();
        when(orders.findById(7L)).thenReturn(Optional.of(existing));
        when(orders.save(existing)).thenReturn(existing);

        OrderResponse updated = service.updateStatus(7L, OrderStatus.PREPARING);

        assertThat(updated.status()).isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    void skippingAStatusIsRejected() {
        FoodOrder existing = new FoodOrder();
        when(orders.findById(7L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(7L, OrderStatus.DELIVERED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("PLACED")
                .hasMessageContaining("DELIVERED");
        verify(orders, never()).save(any());
    }

    @Test
    void finishedOrdersCannotBeChanged() {
        FoodOrder existing = new FoodOrder();
        existing.setStatus(OrderStatus.CANCELLED);
        when(orders.findById(7L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(7L, OrderStatus.PLACED))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void unknownOrderThrowsNotFound() {
        when(orders.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listWithoutFilterReturnsEverythingNewestFirst() {
        when(orders.findAllByOrderByOrderTimeDescIdDesc()).thenReturn(List.of(new FoodOrder(), new FoodOrder()));

        assertThat(service.list(null)).hasSize(2);
    }

    @Test
    void listWithStatusUsesTheFilteredQuery() {
        when(orders.findByStatusOrderByOrderTimeDescIdDesc(OrderStatus.READY)).thenReturn(List.of(new FoodOrder()));

        assertThat(service.list(OrderStatus.READY)).hasSize(1);
        verify(orders, never()).findAllByOrderByOrderTimeDescIdDesc();
    }
}
