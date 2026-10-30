package com.raullopezpenalva.contact_service;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import com.raullopezpenalva.contact_service.modules.contact.domain.events.ContactRequestCreatedEvent;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.handler.ContactRequestCreatedEventHandler;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.port.in.NotificationChannelsResolver;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class NotificationHandlerDiagnosticsTest {
    @Test void outerFailureKeepsCorrelationWithoutRawProviderOrPersistenceDetails(CapturedOutput output) {
        var resolver=mock(NotificationChannelsResolver.class);
        String diagnostic="private-contact-payload-and-provider-credential";
        when(resolver.resolve()).thenThrow(new IllegalStateException(diagnostic,new RuntimeException(diagnostic)));
        var event=mock(ContactRequestCreatedEvent.class);
        UUID id=UUID.randomUUID();when(event.eventId()).thenReturn(id);
        var handler=new ContactRequestCreatedEventHandler(null,resolver,null);
        assertDoesNotThrow(()->handler.handle(event));
        assertTrue(output.getAll().contains(id.toString()));
        assertTrue(output.getAll().contains("errorType=IllegalStateException"));
        assertFalse(output.getAll().contains(diagnostic));
        assertFalse(output.getAll().contains("at com."));
    }
}
