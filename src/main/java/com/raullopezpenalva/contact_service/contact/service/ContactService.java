package com.raullopezpenalva.contact_service.contact.service;

import com.raullopezpenalva.contact_service.contact.model.ContactMessage;
import com.raullopezpenalva.contact_service.contact.repository.ContactMessageRepository;
import com.raullopezpenalva.contact_service.contact.util.HashUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ContactService {

    @Autowired
    private ContactMessageRepository contactMessageRepository;

    public ContactMessage saveMessage(ContactMessage request) {
        var norm = request.getEmail().toLowerCase().trim();
        request.setEmail(norm);
        request.setContentHash(
            HashUtils.generateContentHash(request.getEmail(), request.getSubject(), request.getContent())
        );
        var saved = contactMessageRepository.save(request);
        return saved;
    }

}