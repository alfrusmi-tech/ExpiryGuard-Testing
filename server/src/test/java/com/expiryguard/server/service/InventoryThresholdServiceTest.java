package com.expiryguard.server.service;

import com.expiryguard.server.entity.SystemSettings;
import com.expiryguard.server.repository.SystemSettingsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** EP + BVA + branch coverage for InventoryThresholdService. expiryStatus V(G) = 3. */
@ExtendWith(MockitoExtension.class)
class InventoryThresholdServiceTest {

    @Mock private SystemSettingsRepository settingsRepository;
    @InjectMocks private InventoryThresholdService service;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    @Nested @DisplayName("expiryStatus BVA (30-day window)")
    class Bva {
        @ParameterizedTest(name = "offset {0} -> {1}")
        @CsvSource({"-365,expired", "-1,expired", "0,expiring_soon", "1,expiring_soon", "15,expiring_soon",
                    "29,expiring_soon", "30,expiring_soon", "31,good", "365,good"})
        void status(int offset, String expected) {
            assertEquals(expected, service.expiryStatus(TODAY.plusDays(offset), TODAY, 30));
        }
    }

    @Nested @DisplayName("expiryStatus configurations")
    class Config {
        @Test void zeroWindow() {
            assertEquals("expiring_soon", service.expiryStatus(TODAY, TODAY, 0));
            assertEquals("good", service.expiryStatus(TODAY.plusDays(1), TODAY, 0));
        }
        @ParameterizedTest @CsvSource({"7", "60", "90"})
        void customWindow(int d) {
            assertEquals("expiring_soon", service.expiryStatus(TODAY.plusDays(d), TODAY, d));
            assertEquals("good", service.expiryStatus(TODAY.plusDays(d + 1L), TODAY, d));
        }
        @Test void leapYear() {
            assertEquals("expiring_soon", service.expiryStatus(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 2, 28), 1));
        }
        @Test void yearRollover() {
            assertEquals("expiring_soon", service.expiryStatus(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 31), 1));
        }
        @Test @DisplayName("Possible defect: null expiry date throws NPE")
        void nullDate() {
            assertThrows(NullPointerException.class, () -> service.expiryStatus(null, TODAY, 30));
        }
        @Test @DisplayName("Possible defect: negative warningDays not validated")
        void negativeWindow() {
            assertEquals("good", service.expiryStatus(TODAY, TODAY, -5));
        }
    }

    @Nested class ReorderLevel {
        @Test void requestedUsed() { assertEquals(5, service.effectiveReorderLevel(5, new SystemSettings())); }
        @Test void zeroRespected() { assertEquals(0, service.effectiveReorderLevel(0, new SystemSettings())); }
        @Test void nullDefault() { assertEquals(15, service.effectiveReorderLevel(null, new SystemSettings())); }
        @Test void nullConfigured() {
            SystemSettings s = new SystemSettings(); s.setDefaultLowStockThreshold(40);
            assertEquals(40, service.effectiveReorderLevel(null, s));
        }
    }

    @Nested class CurrentSettings {
        @Test void stored() {
            SystemSettings s = new SystemSettings(); s.setExpiringSoonDays(45);
            when(settingsRepository.findById(1L)).thenReturn(Optional.of(s));
            assertSame(s, service.currentSettings());
        }
        @Test void defaults() {
            when(settingsRepository.findById(1L)).thenReturn(Optional.empty());
            SystemSettings r = service.currentSettings();
            assertEquals(30, r.getExpiringSoonDays());
            assertEquals(7, r.getCriticalExpiryDays());
            assertEquals(15, r.getDefaultLowStockThreshold());
        }
    }
}
