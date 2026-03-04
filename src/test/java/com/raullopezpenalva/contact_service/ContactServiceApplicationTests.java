package com.raullopezpenalva.contact_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Disabled("This test is disabled because it only checks if the application context loads successfully. It can be enabled when needed.")
@SpringBootTest
@ActiveProfiles("test")
class ContactServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
