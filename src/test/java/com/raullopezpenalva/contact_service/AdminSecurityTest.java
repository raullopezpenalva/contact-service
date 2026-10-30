package com.raullopezpenalva.contact_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AdminSecurityTest extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.emailSender.EmailClient emailClient;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.telegram.TelegramClient telegramClient;
    @Test void requiresCredentialsAndLogsFailuresWithoutSecrets(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/v1/admin/contact/messages")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/contact/messages").with(httpBasic("test","secret-invalid-password")))
            .andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
            .contentType("application/json").content("{\"username\":\"unknown-admin\",\"password\":\"secret-invalid-password\"}"))
            .andExpect(status().isUnauthorized());
        assertTrue(output.getAll().contains("Admin authentication failed"));
        assertFalse(output.getAll().contains("secret-invalid-password"));
        mvc.perform(get("/api/v1/admin/contact/messages").with(adminBearer()))
            .andExpect(status().isOk());
    }
}
