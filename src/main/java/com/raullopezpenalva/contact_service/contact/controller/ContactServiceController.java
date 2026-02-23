package com.raullopezpenalva.contact_service.contact.controller;

import com.raullopezpenalva.contact_service.contact.service.*;
import com.raullopezpenalva.contact_service.contact.model.ContactMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;
import java.util.HashMap;


@RestController
@RequestMapping("/api/v1/contact")
public class ContactServiceController {

    @Autowired
    private ContactService contactService;

    @PostMapping("messages")
    public ResponseEntity<?> message(@RequestBody ContactMessage request) {
        var result = contactService.saveMessage(request);

        Map<String, Object> response = new HashMap<>();
        response.put("id", result.getId());
        response.put("createdAt", result.getCreatedAt());        
        return ResponseEntity.created(null).body(response);
    }
    
    
}
