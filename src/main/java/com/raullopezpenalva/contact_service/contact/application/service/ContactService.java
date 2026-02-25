package com.raullopezpenalva.contact_service.contact.application.service;

import com.raullopezpenalva.contact_service.contact.api.dto.request.CreateContactMessageRequest;
import com.raullopezpenalva.contact_service.contact.api.dto.response.ContactMessageResponse;
import com.raullopezpenalva.contact_service.contact.domain.model.ContactMessage;
import com.raullopezpenalva.contact_service.contact.application.model.ClientContext;
import com.raullopezpenalva.contact_service.contact.infrastructure.repository.ContactMessageRepository;
import com.raullopezpenalva.contact_service.contact.application.mapper.ContactMessageMapper;

import org.springframework.stereotype.Service;

@Service
public class ContactService {

    // Repository used to perform CRUD operations on ContactMessage entities
    private final ContactMessageRepository contactMessageRepository;

    // Constructor-based dependency injection for the repository
    public ContactService(ContactMessageRepository contactMessageRepository) {
        this.contactMessageRepository = contactMessageRepository;
    }

    // Saves a new contact message and returns the response DTO
    public ContactMessageResponse saveMessage(CreateContactMessageRequest request, ClientContext clientContext) {
        
        ContactMessage entity = ContactMessageMapper.toEntity(request, clientContext);

        var saved = contactMessageRepository.save(entity);

        return ContactMessageMapper.toResponse(saved);
    }

}