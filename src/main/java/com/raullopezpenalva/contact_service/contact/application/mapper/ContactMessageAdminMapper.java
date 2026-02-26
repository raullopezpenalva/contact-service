package com.raullopezpenalva.contact_service.contact.application.mapper;


import com.raullopezpenalva.contact_service.contact.api.dto.admin.response.SimpleContactMessageAdminResponse;
import com.raullopezpenalva.contact_service.contact.domain.model.ContactMessage;

public class ContactMessageAdminMapper {

    public static SimpleContactMessageAdminResponse toSimpleAdminResponse(ContactMessage entity) {
        SimpleContactMessageAdminResponse response = new SimpleContactMessageAdminResponse();
        response.setId(entity.getId());
        response.setCreatedAt(entity.getCreatedAt());
        response.setEmail(entity.getEmail());
        response.setSubject(entity.getSubject());
        response.setStatus(entity.getStatus());
        response.setSourceIp(entity.getSourceIP());
        return response;
    }
    
}
