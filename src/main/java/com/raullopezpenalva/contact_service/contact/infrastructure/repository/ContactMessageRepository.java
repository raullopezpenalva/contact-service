package com.raullopezpenalva.contact_service.contact.infrastructure.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.raullopezpenalva.contact_service.contact.domain.model.ContactMessage;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {
    // Additional query methods can be defined here if needed
}