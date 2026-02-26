package com.raullopezpenalva.contact_service.contact.api.controller;

import com.raullopezpenalva.contact_service.contact.api.dto.admin.request.UpdateContactMessageStatusRequest;
import com.raullopezpenalva.contact_service.contact.api.dto.admin.response.ExtendedContactMessageAdminResponse;
import com.raullopezpenalva.contact_service.contact.api.dto.admin.response.SimpleContactMessageAdminResponse;
import com.raullopezpenalva.contact_service.contact.api.dto.admin.response.UpdateContactMessageStatusResponse;
import com.raullopezpenalva.contact_service.contact.api.error.ApiError;
import com.raullopezpenalva.contact_service.contact.application.service.ContactAdminService;
import com.raullopezpenalva.contact_service.contact.domain.model.ContactMessageStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PageableDefault;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;



@Tag(name = "Contact Service API (Admin)", description = "API for handling contact messages (Admin)")   
@RestController
@RequestMapping("/api/v1/admin/contact")
public class ContactAdminController {

    @Autowired
    private ContactAdminService contactAdminService;
    
    
    // Get Contact Messages with optional status filter and pagination
    @Operation(
        summary = "List Contact Messages",
        description = "Retrieves a list of contact messages with optional filtering by status.",
        parameters = {
            @Parameter(
                name = "status",
                description = "Filter messages by status (options: NEW, READ, ARCHIVED, SPAM, NOTIFICATION_FAILED)",
                required = false
            ),
            @Parameter(
                name = "page",
                description = "Page number for pagination (0-based index)",
                required = false
            ),
            @Parameter(
                name = "size",
                description = "Number of records per page for pagination",
                required = false
            )
        }
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Contact messages retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = SimpleContactMessageAdminResponse.class)
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
    @GetMapping(
        value = "/messages",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Page<SimpleContactMessageAdminResponse>> listMessages(
        @Valid @RequestParam(required = false) ContactMessageStatus status,
        @PageableDefault(size = 10) Pageable pageable
        ) {

        Page<SimpleContactMessageAdminResponse> response = contactAdminService.getContactMessages(status, pageable);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    
    @Operation(
        summary = "Get Contact Message by ID",
        description = "Retrieves detailed information about a specific contact message by its unique ID."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Contact message retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ExtendedContactMessageAdminResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Contact message not found with the specified ID",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiError.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiError.class)
            )
        )
    })
    @GetMapping(
        value = "/messages/{id}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ExtendedContactMessageAdminResponse> getMessageById(@PathVariable UUID id) {

        ExtendedContactMessageAdminResponse response = contactAdminService.getContactMessageById(id);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    
    @Operation(
        summary = "Update Contact Message Status",
        description = "Updates the status and admin note of a specific contact message by its unique ID."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Contact message status updated successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UpdateContactMessageStatusResponse.class)
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
            responseCode = "404",
            description = "Contact message not found with the specified ID",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiError.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized access",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiError.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiError.class)
            )
        )
    })
    @PatchMapping(
        value = "/messages/{id}/status",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<UpdateContactMessageStatusResponse> updateMessageStatus(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateContactMessageStatusRequest request
    ) {
        UpdateContactMessageStatusResponse response = contactAdminService.updateContactMessageStatus(id, request);
        return ResponseEntity.status(HttpStatus.OK).body(response);

    }
}
