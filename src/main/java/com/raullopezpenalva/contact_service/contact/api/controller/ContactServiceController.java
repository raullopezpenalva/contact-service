package com.raullopezpenalva.contact_service.contact.api.controller;

import com.raullopezpenalva.contact_service.contact.api.dto.request.CreateContactMessageRequest;
import com.raullopezpenalva.contact_service.contact.api.dto.response.ContactMessageResponse;
import com.raullopezpenalva.contact_service.contact.api.error.ApiError;
import com.raullopezpenalva.contact_service.contact.application.model.ClientContext;
import com.raullopezpenalva.contact_service.contact.application.service.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@Tag(name = "Contact Service API (Public)", description = "API for handling contact messages")   
@RestController
@RequestMapping("/api/v1/contact")
public class ContactServiceController {

    @Autowired
    private ContactService contactService;


    // POST request to create a new contact message
    @Operation(
        summary = "Create Contact Message",
        description = "Saves a new contact message with the provided details and client context.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Contact message details",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CreateContactMessageRequest.class)
            )
        )
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Contact message created successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ContactMessageResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiError.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content
            (
                mediaType = "application/json",
                schema = @Schema(implementation = ApiError.class)
            )
        )
    })
    @PostMapping(
        value = "/messages",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ContactMessageResponse> create(@Valid @RequestBody CreateContactMessageRequest request, HttpServletRequest httpRequest) {
        
        ClientContext clientContext = new ClientContext(
            httpRequest.getHeader("X-Forwarded-For") != null ? httpRequest.getHeader("X-Forwarded-For") : httpRequest.getRemoteAddr(),
            httpRequest.getHeader("User-Agent")
        );
        ContactMessageResponse response = contactService.saveMessage(request, clientContext);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    
}
