package com.raullopezpenalva.contact_service;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.service.NotificationService;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.port.in.PropertyNotificationChannelsResolver;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.port.out.*;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    NotificationDeliveryRepository repository=mock(NotificationDeliveryRepository.class);
    NotificationGateway email=mock(NotificationGateway.class), telegram=mock(NotificationGateway.class);
    NotificationMessage message=new NotificationMessage(UUID.randomUUID(),Instant.EPOCH,"a@example.com","Hello","Message");
    NotificationDelivery delivery(NotificationChannel channel) {
        return NotificationDelivery.createNew(message.getEventId(),"contact.request.created",channel,"{}");
    }
    NotificationService service(boolean enabled) {
        when(email.getChannel()).thenReturn(NotificationChannel.EMAIL);
        when(telegram.getChannel()).thenReturn(NotificationChannel.TELEGRAM);
        return new NotificationService(List.of(email,telegram),repository,enabled);
    }
    @Test void persistsPendingBeforeSendingAndFinalStateAfterSuccess() {
        var service=service(true);var delivery=delivery(NotificationChannel.EMAIL);
        var states=new ArrayList<NotificationStatus>();
        doAnswer(i->{states.add(((NotificationDelivery)i.getArgument(0)).getStatus());return delivery;})
            .when(repository).save(delivery);
        service.sendNotification(message,delivery);
        assertEquals(List.of(NotificationStatus.PENDING,NotificationStatus.SENT),states);
        assertEquals(1,delivery.getAttempts());verify(email).sendNotification(message);
        verify(telegram,never()).sendNotification(any());
    }
    @Test void recordsGatewayFailureWithoutPropagatingIt() {
        var service=service(true);var delivery=delivery(NotificationChannel.TELEGRAM);
        doThrow(new IllegalStateException("Gateway unavailable")).when(telegram).sendNotification(message);
        assertDoesNotThrow(()->service.sendNotification(message,delivery));
        assertEquals(NotificationStatus.FAILED,delivery.getStatus());assertEquals(1,delivery.getAttempts());
        assertEquals("Gateway unavailable",delivery.getLastError());verify(repository,times(2)).save(delivery);
    }
    @Test void disabledNotificationsNeverInvokeGateways() {
        var service=service(false);var delivery=delivery(NotificationChannel.EMAIL);
        service.sendNotification(message,delivery);
        assertEquals(NotificationStatus.FAILED,delivery.getStatus());assertEquals(0,delivery.getAttempts());
        verify(email,never()).sendNotification(any());verify(telegram,never()).sendNotification(any());
    }
    @Test void missingGatewayIsRecordedAsFailure() {
        var delivery=delivery(NotificationChannel.EMAIL);
        new NotificationService(List.of(),repository,true).sendNotification(message,delivery);
        assertEquals(NotificationStatus.FAILED,delivery.getStatus());assertTrue(delivery.getLastError().contains("No gateway"));
    }
    @Test void resolvesChannelsIgnoringCaseWhitespaceAndDuplicates() {
        assertEquals(Set.of(NotificationChannel.EMAIL,NotificationChannel.TELEGRAM),
            new PropertyNotificationChannelsResolver(" email, TELEGRAM,Email ").resolve());
    }
    @Test void rejectsUnknownAndEmptyChannelConfiguration() {
        assertThrows(IllegalArgumentException.class,()->new PropertyNotificationChannelsResolver("unknown"));
        assertThrows(IllegalArgumentException.class,()->new PropertyNotificationChannelsResolver(""));
    }
}
