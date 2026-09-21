package io.github.blueberry44477.parking_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.blueberry44477.parking_system.ParkingService.ParkingAvailability;
import io.github.blueberry44477.parking_system.ParkingService.ParkingRepository;
import io.github.blueberry44477.parking_system.ParkingService.SubscriptionRegistry;

@ExtendWith(MockitoExtension.class)
public class ParkingServiceTest {
    // Mock can also be used as a Stub.
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

    @ParameterizedTest
    @CsvSource({
        "0, 4000",
        "2, 4000",
        "5, 4000", // Night entry. But test FAILURE because of >5 in code.

        "6, 5000",
        "15, 5000",
        "21, 5000",

        "22, 4000", // Night entry
        "23, 4000"
    })
    void process_shouldCalculateTotalPriceCorrectly_forVariousEntryHours(int entryHour, long expectedTotalPrice) {
        when(parkingAvailability.isAvailable()).thenReturn(true);
        ParkingService.Request request = new ParkingService.Request(30, entryHour, "Центральна", false, false);

        ParkingService.Result result = parkingService.process(request);

        assertEquals("Розраховано", result.status());
        assertEquals(expectedTotalPrice, result.total());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 24})
    void process_shouldThrowException_forInvalidEntryHours(int entryHour) {
        ParkingService.Request request = new ParkingService.Request(30, entryHour, "Центральна", false, false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            parkingService.process(request);
        });

        assertEquals("Недопустиме значення: Година в’їзду", exception.getMessage());
    }

    // Decision Table
    @ParameterizedTest 
    @CsvSource({
        "false, false, 5000", // Without discount
        "true, false, 4500",
        "false, true, 3500",
        "true, true, 3000" // ERROR. must be 3000 but 3250 because of 35% discount instead of 40% in the code.
    })
    void process_shouldCalculateDiscountCorrectly_forVariousParameters(
            boolean electric, boolean subscriber, long expectedTotalPrice) {
        when(parkingAvailability.isAvailable()).thenReturn(true);
        
        if (subscriber) {
            when(subscriptionRegistry.isValid()).thenReturn(true);
        }

        ParkingService.Request request = new ParkingService.Request(30, 15, "Центральна", electric, subscriber);

        ParkingService.Result result = parkingService.process(request);

        assertEquals("Розраховано", result.status());
        assertEquals(expectedTotalPrice, result.total());
    }

    // Interaction-Based Test: Repository
    @Test
    void process_shouldSaveReceiptWithCorrectArguments() {
        when(parkingAvailability.isAvailable()).thenReturn(true);
        ParkingService.Request request = new ParkingService.Request(15, 12, "Центральна", false, false);

        parkingService.process(request);

        ArgumentCaptor<ParkingService.Request> requestCaptor = ArgumentCaptor.forClass(ParkingService.Request.class);
        ArgumentCaptor<ParkingService.Result> resultCaptor = ArgumentCaptor.forClass(ParkingService.Result.class);

        // Ensure save() is called 1 time only.
        verify(parkingRepository, times(1)).save(requestCaptor.capture(), resultCaptor.capture());
        
        assertEquals(15, requestCaptor.getValue().minutes());
        assertEquals(0, resultCaptor.getValue().total());
        assertEquals("Розраховано", resultCaptor.getValue().status());
    }

    // Interaction-Based Test: Відхилення операції (Stub) та відсутність виклику (Repository)
    @Test
    void process_shouldRejectWhenParkingIsUnavailable() {
        when(parkingAvailability.isAvailable()).thenReturn(false);
        ParkingService.Request request = new ParkingService.Request(60, 12, "Центральна", false, false);

        ParkingService.Result result = parkingService.process(request);

        assertEquals("Оформлення недоступне", result.status());
        assertEquals(0, result.total());
        verifyNoInteractions(parkingRepository);
    }
}
