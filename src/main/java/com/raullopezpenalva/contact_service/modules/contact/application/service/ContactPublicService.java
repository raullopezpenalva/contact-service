package com.raullopezpenalva.contact_service.modules.contact.application.service;

import org.springframework.stereotype.Service;

import com.raullopezpenalva.contact_service.modules.contact.api.dto.pub.request.CreateContactMessageRequest;
import com.raullopezpenalva.contact_service.modules.contact.api.dto.pub.response.ContactMessageResponse;
import com.raullopezpenalva.contact_service.modules.contact.application.mapper.ContactMessageCreatedEventMapper;
import com.raullopezpenalva.contact_service.modules.contact.application.mapper.ContactMessagePublicMapper;
import com.raullopezpenalva.contact_service.modules.contact.application.model.ClientContext;
import com.raullopezpenalva.contact_service.modules.contact.domain.model.ContactMessage;
import com.raullopezpenalva.contact_service.modules.contact.infrastructure.repository.ContactMessageRepository;
import com.raullopezpenalva.contact_service.shared.events.EventPublisher;

import jakarta.transaction.Transactional;

@Service
public class ContactPublicService {

    // Repository used to perform CRUD operations on ContactMessage entities
    private final ContactMessageRepository contactMessageRepository;

    private final EventPublisher eventPublisher;

    // Constructor-based dependency injection for the repository and event publisher
    public ContactPublicService(ContactMessageRepository contactMessageRepository, EventPublisher eventPublisher) {
        this.contactMessageRepository = contactMessageRepository;
        this.eventPublisher = eventPublisher;
    }

    // Saves a new contact message and returns the response DTO
    @Transactional
    public ContactMessageResponse saveMessage(CreateContactMessageRequest request, ClientContext clientContext) {
        
        ContactMessage entity = ContactMessagePublicMapper.toEntity(request, clientContext);

        var saved = contactMessageRepository.saveAndFlush(entity);

        eventPublisher.publish(ContactMessageCreatedEventMapper.toEvent(saved));

        return ContactMessagePublicMapper.toResponse(saved);
    }

}