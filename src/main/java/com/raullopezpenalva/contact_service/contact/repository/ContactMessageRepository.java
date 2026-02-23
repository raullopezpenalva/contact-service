package com.raullopezpenalva.contact_service.contact.repository;

import com.raullopezpenalva.contact_service.contact.model.ContactMessage;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {
    // Additional query methods can be defined here if needed
}