package com.raullopezpenalva.contact_service.contact.application.mapper;


import com.raullopezpenalva.contact_service.contact.api.dto.admin.response.ExtendedContactMessageAdminResponse;
import com.raullopezpenalva.contact_service.contact.api.dto.admin.response.SimpleContactMessageAdminResponse;
import com.raullopezpenalva.contact_service.contact.api.dto.admin.response.UpdateContactMessageStatusResponse;
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

    public static ExtendedContactMessageAdminResponse toExtendedAdminResponse(ContactMessage entity) {
        ExtendedContactMessageAdminResponse response = new ExtendedContactMessageAdminResponse();
        response.setId(entity.getId());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setEmail(entity.getEmail());
        response.setSubject(entity.getSubject());
        response.setContent(entity.getContent());
        response.setStatus(entity.getStatus());
        response.setAdminNote(entity.getAdminNote());
        response.setSourceIp(entity.getSourceIP());
        response.setUserAgent(entity.getUserAgent());
        return response;
    }

    public static UpdateContactMessageStatusResponse toUpdateStatusResponse(ContactMessage entity) {
        UpdateContactMessageStatusResponse response = new UpdateContactMessageStatusResponse();
        response.setId(entity.getId());
        response.setStatus(entity.getStatus());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}