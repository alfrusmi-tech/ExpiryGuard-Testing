package com.expiryguard.server.service;

import com.expiryguard.server.entity.Batch;
import com.expiryguard.server.entity.MovementType;
import com.expiryguard.server.entity.StockMovement;
import com.expiryguard.server.repository.BatchRepository;
import com.expiryguard.server.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** White-box tests for StockMovementService.record(). V(G) = 7 -> 7 basis paths. */
@ExtendWith(MockitoExtension.class)
class StockMovementServiceWhiteBoxTest {

    @Mock private BatchRepository batches;
    @Mock private StockMovementRepository movements;
    @InjectMocks private StockMovementService service;
    private Batch batch;

    @BeforeEach
    void setUp() {
        batch = new Batch();
        batch.setQuantity(10);
        batch.setExpiryDate(LocalDate.now().plusDays(60));
    }

    private void batchExists() { when(batches.findByIdForUpdate(1L)).thenReturn(Optional.of(batch)); }
    private void saveEcho() { when(movements.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0)); }
    private HttpStatus status(ResponseStatusException e) { return HttpStatus.valueOf(e.getStatusCode().value()); }

    @Test @DisplayName("P1: zero quantity rejected (400)")
    void p1_zeroQuantity() {
        var e = assertThrows(ResponseStatusException.class, () -> service.record(1L, MovementType.RECEIVED, 0, "x"));
        assertEquals(HttpStatus.BAD_REQUEST, status(e));
        verifyNoInteractions(batches, movements);
    }

    @Test @DisplayName("P1 BVA: negative quantity rejected (400)")
    void p1_negativeQuantity() {
        var e = assertThrows(ResponseStatusException.class, () -> service.record(1L, MovementType.SOLD, -1, "x"));
        assertEquals(HttpStatus.BAD_REQUEST, status(e));
    }

    @Test @DisplayName("P2: unknown batch returns 404")
    void p2_notFound() {
        when(batches.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        var e = assertThrows(ResponseStatusException.class, () -> service.record(99L, MovementType.SOLD, 1, "x"));
        assertEquals(HttpStatus.NOT_FOUND, status(e));
        verifyNoInteractions(movements);
    }

    @Test @DisplayName("P3: expired batch cannot be sold (409)")
    void p3_expiredSale() {
        batch.setExpiryDate(LocalDate.now().minusDays(1));
        batchExists();
        var e = assertThrows(ResponseStatusException.class, () -> service.record(1L, MovementType.SOLD, 1, "x"));
        assertEquals(HttpStatus.CONFLICT, status(e));
        assertEquals(10, batch.getQuantity());
        verify(batches, never()).save(any());
        verifyNoInteractions(movements);
    }

    @Test @DisplayName("P4: receiving increases stock")
    void p4_receive() {
        batchExists(); saveEcho();
        StockMovement m = service.record(1L, MovementType.RECEIVED, 5, "delivery");
        assertEquals(15, batch.getQuantity());
        assertEquals(MovementType.RECEIVED, m.getType());
        assertEquals(5, m.getQuantity());
        verify(batches).save(batch);
    }

    @Test @DisplayName("P5: receive overflow rejected (409)")
    void p5_overflow() {
        batchExists();
        var e = assertThrows(ResponseStatusException.class,
                () -> service.record(1L, MovementType.RECEIVED, Integer.MAX_VALUE - 10 + 1, "x"));
        assertEquals(HttpStatus.CONFLICT, status(e));
        assertEquals(10, batch.getQuantity());
    }

    @Test @DisplayName("P5 BVA: receive exactly up to Integer.MAX_VALUE accepted")
    void p5_exactLimit() {
        batchExists(); saveEcho();
        service.record(1L, MovementType.RECEIVED, Integer.MAX_VALUE - 10, "x");
        assertEquals(Integer.MAX_VALUE, batch.getQuantity());
    }

    @Test @DisplayName("P6: selling more than stock rejected (409)")
    void p6_insufficient() {
        batchExists();
        var e = assertThrows(ResponseStatusException.class, () -> service.record(1L, MovementType.SOLD, 11, "x"));
        assertEquals(HttpStatus.CONFLICT, status(e));
        assertEquals(10, batch.getQuantity());
    }

    @Test @DisplayName("P7: valid sale decreases stock")
    void p7_validSale() {
        batchExists(); saveEcho();
        StockMovement m = service.record(1L, MovementType.SOLD, 4, "sale");
        assertEquals(6, batch.getQuantity());
        assertEquals("sale", m.getNote());
        assertSame(batch, m.getBatch());
    }

    @Test @DisplayName("P7 BVA: selling entire stock leaves zero")
    void p7_sellAll() {
        batchExists(); saveEcho();
        service.record(1L, MovementType.SOLD, 10, "x");
        assertEquals(0, batch.getQuantity());
    }

    @Test @DisplayName("P7: disposing an expired batch is allowed")
    void p7_disposeExpired() {
        batch.setExpiryDate(LocalDate.now().minusDays(5));
        batchExists(); saveEcho();
        service.record(1L, MovementType.DISPOSED, 10, "expired");
        assertEquals(0, batch.getQuantity());
    }

    @Test @DisplayName("BVA: batch expiring today can still be sold")
    void sellOnExpiryDay() {
        batch.setExpiryDate(LocalDate.now());
        batchExists(); saveEcho();
        service.record(1L, MovementType.SOLD, 1, "x");
        assertEquals(9, batch.getQuantity());
    }

    @Test @DisplayName("Else branch: null movement type rejected (400)")
    void invalidType() {
        batchExists();
        var e = assertThrows(ResponseStatusException.class, () -> service.record(1L, null, 1, "x"));
        assertEquals(HttpStatus.BAD_REQUEST, status(e));
        verifyNoInteractions(movements);
    }
}
