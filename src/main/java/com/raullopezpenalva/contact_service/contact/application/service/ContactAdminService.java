package com.raullopezpenalva.contact_service.contact.application.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.raullopezpenalva.contact_service.contact.domain.model.ContactMessage;
import com.raullopezpenalva.contact_service.contact.domain.model.ContactMessageStatus;
import com.raullopezpenalva.contact_service.contact.infrastructure.repository.ContactMessageRepository;
import com.raullopezpenalva.contact_service.contact.api.dto.admin.response.ExtendedContactMessageAdminResponse;
import com.raullopezpenalva.contact_service.contact.api.dto.admin.response.SimpleContactMessageAdminResponse;
import com.raullopezpenalva.contact_service.contact.application.exception.ResourceNotFoundException;
import com.raullopezpenalva.contact_service.contact.application.mapper.ContactMessageAdminMapper;


@Service
public class ContactAdminService {

    // Repository used to perform CRUD operations on ContactMessage entities
    private final ContactMessageRepository contactMessageRepository;

    // Constructor-based dependency injection for the repository
    public ContactAdminService(ContactMessageRepository contactMessageRepository) {
        this.contactMessageRepository = contactMessageRepository;
    }

    public Page<SimpleContactMessageAdminResponse> getContactMessages(ContactMessageStatus status, Pageable pageable) {
        
        Pageable sorted = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<ContactMessage> page = (status == null)
            ? contactMessageRepository.findAll(sorted)
            : contactMessageRepository.findByStatus(status, sorted);
            
        return page.map(ContactMessageAdminMapper::toSimpleAdminResponse);

    }

    public ExtendedContactMessageAdminResponse getContactMessageById(UUID id) {
        ContactMessage message = contactMessageRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Contact message not found with id: " + id
            ));
        return ContactMessageAdminMapper.toExtendedAdminResponse(message);
    }


    
}
