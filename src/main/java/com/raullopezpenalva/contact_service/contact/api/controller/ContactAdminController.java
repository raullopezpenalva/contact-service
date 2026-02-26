package com.raullopezpenalva.contact_service.contact.api.controller;

import com.raullopezpenalva.contact_service.contact.api.dto.admin.response.SimpleContactMessageAdminResponse;
import com.raullopezpenalva.contact_service.contact.api.error.ApiError;
import com.raullopezpenalva.contact_service.contact.application.service.ContactAdminService;
import com.raullopezpenalva.contact_service.contact.domain.model.ContactMessageStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PageableDefault;
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
    
    
}
