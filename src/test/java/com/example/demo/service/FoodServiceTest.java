package com.example.demo.service;

import com.example.demo.dto.FoodRequest;
import com.example.demo.dto.FoodResponse;
import com.example.demo.entity.FoodItem;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.FoodItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodServiceTest {

    @Mock
    FoodItemRepository repository;

    FoodService service;

    @BeforeEach
    void setUp() {
        service = new FoodService(repository);
    }

    @Test
    void createTrimsTextAndDefaultsToAvailable() {
        when(repository.save(any(FoodItem.class))).thenAnswer(inv -> inv.getArgument(0));

        FoodResponse created = service.create(
                new FoodRequest("  Masala Dosa ", " ", null, new BigDecimal("80.00"), null));

        assertThat(created.name()).isEqualTo("Masala Dosa");
        assertThat(created.category()).isNull();
        assertThat(created.available()).isTrue();
    }

    @Test
    void updateChangesAllFields() {
        FoodItem existing = new FoodItem("Old", "Cat", "Desc", new BigDecimal("10.00"));
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        FoodResponse updated = service.update(5L,
                new FoodRequest("New", "Drinks", "Cold", new BigDecimal("20.50"), false));

        assertThat(updated.name()).isEqualTo("New");
        assertThat(updated.category()).isEqualTo("Drinks");
        assertThat(updated.price()).isEqualByComparingTo("20.50");
        assertThat(updated.available()).isFalse();
    }

    @Test
    void unknownIdThrowsNotFound() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void deleteRemovesTheEntity() {
        FoodItem existing = new FoodItem("Fries", "Sides", null, new BigDecimal("89"));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        ArgumentCaptor<FoodItem> captor = ArgumentCaptor.forClass(FoodItem.class);
        verify(repository).delete(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
    }

    @Test
    void deleteOfUnknownIdThrowsNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(NotFoundException.class);
    }
}
