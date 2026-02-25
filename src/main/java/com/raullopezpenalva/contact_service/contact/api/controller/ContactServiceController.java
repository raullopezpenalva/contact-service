package com.raullopezpenalva.contact_service.contact.api.controller;

import com.raullopezpenalva.contact_service.contact.api.dto.request.CreateContactMessageRequest;
import com.raullopezpenalva.contact_service.contact.api.dto.response.ContactMessageResponse;
import com.raullopezpenalva.contact_service.contact.application.model.ClientContext;
import com.raullopezpenalva.contact_service.contact.application.service.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController
@RequestMapping("/api/v1/contact")
public class ContactServiceController {

    @Autowired
    private ContactService contactService;


    // POST request to create a new contact message
    @PostMapping("messages")
    public ResponseEntity<ContactMessageResponse> create(@Valid @RequestBody CreateContactMessageRequest request, HttpServletRequest httpRequest) {
        
        ClientContext clientContext = new ClientContext(
            httpRequest.getHeader("X-Forwarded-For") != null ? httpRequest.getHeader("X-Forwarded-For") : httpRequest.getRemoteAddr(),
            httpRequest.getHeader("User-Agent")
        );
        ContactMessageResponse response = contactService.saveMessage(request, clientContext);

        return ResponseEntity.created(null).body(response);
    }
    
    
}
