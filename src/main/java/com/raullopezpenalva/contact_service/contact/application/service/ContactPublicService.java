package com.raullopezpenalva.contact_service.contact.application.service;

import com.raullopezpenalva.contact_service.contact.api.dto.pub.request.CreateContactMessageRequest;
import com.raullopezpenalva.contact_service.contact.api.dto.pub.response.ContactMessageResponse;
import com.raullopezpenalva.contact_service.contact.domain.model.ContactMessage;
import com.raullopezpenalva.contact_service.contact.application.model.ClientContext;
import com.raullopezpenalva.contact_service.contact.infrastructure.repository.ContactMessageRepository;
import com.raullopezpenalva.contact_service.contact.application.mapper.ContactMessagePublicMapper;

import org.springframework.stereotype.Service;

@Service
public class ContactPublicService {

    // Repository used to perform CRUD operations on ContactMessage entities
    private final ContactMessageRepository contactMessageRepository;

    // Constructor-based dependency injection for the repository
    public ContactPublicService(ContactMessageRepository contactMessageRepository) {
        this.contactMessageRepository = contactMessageRepository;
    }

    // Saves a new contact message and returns the response DTO
    public ContactMessageResponse saveMessage(CreateContactMessageRequest request, ClientContext clientContext) {
        
        ContactMessage entity = ContactMessagePublicMapper.toEntity(request, clientContext);

        var saved = contactMessageRepository.save(entity);

        return ContactMessagePublicMapper.toResponse(saved);
    }

}