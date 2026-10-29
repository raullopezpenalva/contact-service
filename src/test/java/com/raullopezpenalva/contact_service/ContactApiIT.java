package com.raullopezpenalva.contact_service;

import java.util.UUID;
import com.raullopezpenalva.contact_service.modules.contact.domain.model.*;
import com.raullopezpenalva.contact_service.modules.contact.domain.events.ContactRequestCreatedEvent;
import com.raullopezpenalva.contact_service.modules.contact.infrastructure.repository.ContactMessageRepository;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.emailSender.EmailClient;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.telegram.TelegramClient;
import com.raullopezpenalva.contact_service.shared.events.EventPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="app.security.rate-limit.contactrequest.max-requests=100")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContactApiIT {
    static final String ADMIN="/api/v1/admin/contact/messages";
    @Autowired MockMvc mvc;
    @Autowired ContactMessageRepository repository;
    @MockitoBean EmailClient email;
    @MockitoBean TelegramClient telegram;
    @MockitoBean EventPublisher events;
    @BeforeEach void clean() { repository.deleteAll(); }
    ContactMessage message(String subject, ContactMessageStatus status) {
        var m=new ContactMessage();m.setEmail("person@example.com");m.setSubject(subject);
        m.setContent("A meaningful message body.");m.setStatus(status);
        return repository.saveAndFlush(m);
    }
    @Test void createsMessageAndPublishesMatchingEvent() throws Exception {
        mvc.perform(post("/api/v1/contact/messages").contentType("application/json")
            .content("{\"email\":\"person@example.com\",\"subject\":\"Hello\",\"content\":\"A meaningful message body.\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.createdAt").exists());
        var saved=repository.findAll().getFirst();assertEquals(ContactMessageStatus.NEW,saved.getStatus());
        verify(events).publish(argThat(e -> e instanceof ContactRequestCreatedEvent c
            && c.contactRequestId().equals(saved.getId()) && c.email().equals(saved.getEmail())));
    }
    @ParameterizedTest
    @ValueSource(strings={"{}", "{", "{\"email\":\"invalid\",\"subject\":\"Hi\",\"content\":\"short\"}"})
    void invalidPublicRequestsReturnStructuredErrorsWithoutSideEffects(String body) throws Exception {
        mvc.perform(post("/api/v1/contact/messages").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.path").value("/api/v1/contact/messages"))
            .andExpect(jsonPath("$.details").isArray());
        assertEquals(0,repository.count());verifyNoInteractions(events,email,telegram);
    }
    @Test void filtersAndPaginatesWithoutExposingMessageContent() throws Exception {
        message("Read message",ContactMessageStatus.READ);message("New message",ContactMessageStatus.NEW);
        mvc.perform(get(ADMIN).param("status","READ").param("size","1").with(httpBasic("test","test")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].subject").value("Read message"))
            .andExpect(jsonPath("$.content[0].content").doesNotExist());
        mvc.perform(get(ADMIN).param("page","1").param("size","1").with(httpBasic("test","test")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.number").value(1))
            .andExpect(jsonPath("$.totalElements").value(2));
    }
    @Test void retrievesFullDetailAndPersistsStatusNoteAndTimestamp() throws Exception {
        var m=message("Detail message",ContactMessageStatus.NEW);var before=m.getUpdatedAt();
        mvc.perform(get(ADMIN+"/"+m.getId()).with(httpBasic("test","test")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").value(m.getContent()));
        mvc.perform(patch(ADMIN+"/"+m.getId()+"/status").with(httpBasic("test","test"))
            .contentType("application/json").content("{\"status\":\"READ\",\"adminNote\":\"Replied\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READ"));
        var updated=repository.findById(m.getId()).orElseThrow();
        assertEquals(ContactMessageStatus.READ,updated.getStatus());assertEquals("Replied",updated.getAdminNote());
        assertTrue(updated.getUpdatedAt().isAfter(before));
    }
    @Test void missingMessagesReturn404ForReadAndUpdate() throws Exception {
        String path=ADMIN+"/"+UUID.randomUUID();
        mvc.perform(get(path).with(httpBasic("test","test"))).andExpect(status().isNotFound());
        mvc.perform(patch(path+"/status").with(httpBasic("test","test")).contentType("application/json")
            .content("{\"status\":\"READ\"}")).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"status\":\"INVALID\"}"})
    void invalidStatusUpdatesDoNotMutateData(String body) throws Exception {
        var m=message("Unchanged message",ContactMessageStatus.NEW);
        mvc.perform(patch(ADMIN+"/"+m.getId()+"/status").with(httpBasic("test","test"))
            .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        assertEquals(ContactMessageStatus.NEW,repository.findById(m.getId()).orElseThrow().getStatus());
    }
    @ParameterizedTest @ValueSource(strings={"?status=INVALID","/invalid-uuid"})
    void invalidAdminParametersReturn400(String suffix) throws Exception {
        mvc.perform(get(ADMIN+suffix).with(httpBasic("test","test"))).andExpect(status().isBadRequest());
    }
    @Test void nonNumericPageUsesSpringDefault() throws Exception {
        mvc.perform(get(ADMIN).param("page","invalid").with(httpBasic("test","test")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.number").value(0));
    }
    @Test void unauthenticatedUpdatesAreRejected() throws Exception {
        var m=message("Protected message",ContactMessageStatus.NEW);
        mvc.perform(patch(ADMIN+"/"+m.getId()+"/status").contentType("application/json")
            .content("{\"status\":\"READ\"}")).andExpect(status().isUnauthorized());
        assertEquals(ContactMessageStatus.NEW,repository.findById(m.getId()).orElseThrow().getStatus());
    }
}
