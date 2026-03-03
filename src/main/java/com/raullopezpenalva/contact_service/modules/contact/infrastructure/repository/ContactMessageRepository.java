package com.raullopezpenalva.contact_service.modules.contact.infrastructure.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.raullopezpenalva.contact_service.modules.contact.domain.model.ContactMessage;
import com.raullopezpenalva.contact_service.modules.contact.domain.model.ContactMessageStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {
    // Additional query methods can be defined here if needed
    Page<ContactMessage> findAll(Pageable pageable);
    Page<ContactMessage> findByStatus(ContactMessageStatus status, Pageable pageable);
    Optional<ContactMessage> findById(UUID id);
}