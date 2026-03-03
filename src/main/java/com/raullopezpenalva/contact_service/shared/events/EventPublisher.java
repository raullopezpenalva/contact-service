package com.raullopezpenalva.contact_service.shared.events;

public interface EventPublisher {
    void publish(DomainEvent event);
}
