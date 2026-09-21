package io.github.blueberry44477.parking_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.blueberry44477.parking_system.ParkingService.ParkingAvailability;
import io.github.blueberry44477.parking_system.ParkingService.ParkingRepository;
import io.github.blueberry44477.parking_system.ParkingService.SubscriptionRegistry;

@ExtendWith(MockitoExtension.class)
public class ParkingServiceTest {
    @Mock
    private ParkingAvailability parkingAvailability;

    @Mock
    private SubscriptionRegistry subscriptionRegistry;

    @Mock
    private ParkingRepository parkingRepository;

    @InjectMocks
    private ParkingService parkingService;

    // 1. State-Based Test: Позитивний сценарій без знижок
    @Test 
    @DisplayName("Calculates standard parking rate when no discounts apply")
    void process_shouldCalculateStandardParkingWithoutDiscounts() {
        when(parkingAvailability.isAvailable()).thenReturn(true);
        ParkingService.Request request = new ParkingService.Request(75, 12, "Центральна", false, false);
        
        ParkingService.Result result = parkingService.process(request);
        
        assertEquals("Розраховано", result.status());
        assertEquals(1, result.billableHours());
        assertEquals(5000, result.total());
    }
    
    @ParameterizedTest
    @CsvSource({
        "1, 0, 0", // Lower 
        "15, 0, 0", // Right on 
        "16, 1, 5000", // Upper
        "75, 1, 5000", 
        "76, 2, 10000",
        "1440, 24, 30000" // Error: 1440 must be valid but 1439 is max in code.
    })
    void process_shouldCalculateCorrectly_forValidDurationBoundaries(int minutes, int expectedHours, long expectedTotalPrice) {
        when(parkingAvailability.isAvailable()).thenReturn(true);
        ParkingService.Request request = new ParkingService.Request(minutes, 12, "Центральна", false, false);
        
        ParkingService.Result result = parkingService.process(request);

        assertEquals("Розраховано", result.status());
        assertEquals(expectedHours, result.billableHours());
        assertEquals(expectedTotalPrice, result.total());
    }

    @ParameterizedTest(name = "Duration {0} should throw validation error")
    @ValueSource(ints = {0, 1441})
    void process_shouldThrowException_forInvalidDurations(int minutes) {
        ParkingService.Request request = new ParkingService.Request(minutes, 12, "Центральна", false, false);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            parkingService.process(request);
        });

        assertEquals("Недопустиме значення: Тривалість", exception.getMessage());
    }
}
