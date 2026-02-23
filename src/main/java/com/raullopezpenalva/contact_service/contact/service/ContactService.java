package com.raullopezpenalva.contact_service.contact.service;

import com.raullopezpenalva.contact_service.contact.model.ContactMessage;
import com.raullopezpenalva.contact_service.contact.repository.ContactMessageRepository;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ContactService {

    @Autowired
    private ContactMessageRepository contactMessageRepository;

    public ContactMessage saveMessage(ContactMessage request) {
        ContactMessage message = new ContactMessage();
        message.setEmail(request.getEmail());
        message.setSubject(request.getSubject());
        message.setContent(request.getContent());
        var saved = contactMessageRepository.save(message);
        return saved;
    }

}