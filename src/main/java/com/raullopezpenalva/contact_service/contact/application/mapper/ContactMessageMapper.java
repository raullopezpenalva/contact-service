package com.raullopezpenalva.contact_service.contact.application.mapper;

import com.raullopezpenalva.contact_service.contact.api.dto.request.CreateContactMessageRequest;
import com.raullopezpenalva.contact_service.contact.api.dto.response.ContactMessageResponse;
import com.raullopezpenalva.contact_service.contact.application.model.ClientContext;
import com.raullopezpenalva.contact_service.contact.domain.model.ContactMessage;
import com.raullopezpenalva.contact_service.contact.infrastructure.security.crypto.HashUtils;


public class ContactMessageMapper {

    // Maps CreateContactMessageRequest DTO and ClientContext to ContactMessage entity
    public static ContactMessage toEntity(CreateContactMessageRequest request, ClientContext clientContext) {
        
        ContactMessage entity = new ContactMessage();

        var norm = request.getEmail().toLowerCase().trim();
        entity.setEmail(norm);
        entity.setSubject(request.getSubject());
        entity.setContent(request.getContent());

        entity.setContentHash(
            HashUtils.generateContentHash(entity.getEmail(), entity.getSubject(), entity.getContent())
        );

        entity.setSourceIP(clientContext.getSourceIP());
        entity.setUserAgent(clientContext.getUserAgent());

        return entity;
    }

    // Maps ContactMessage entity to ContactMessageResponse DTO
    public static ContactMessageResponse toResponse(ContactMessage entity) {

        return new ContactMessageResponse(
            entity.getId(),
            entity.getCreatedAt()
        );
    }
}
