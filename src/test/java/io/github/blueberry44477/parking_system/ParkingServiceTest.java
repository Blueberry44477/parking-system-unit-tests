package io.github.blueberry44477.parking_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test 
    void process_shouldCalculateStandardParkingWithoutDiscounts() {
        when(parkingAvailability.isAvailable()).thenReturn(true);
        ParkingService.Request request = new ParkingService.Request(75, 12, "Центральна", false, false);
        
        ParkingService.Result result = parkingService.process(request);

        assertEquals("Розраховано", result.status());
        assertEquals(1, result.billableHours());
        assertEquals(5000, result.total());
    }
}
