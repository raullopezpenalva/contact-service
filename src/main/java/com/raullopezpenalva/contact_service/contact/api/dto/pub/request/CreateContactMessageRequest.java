package com.raullopezpenalva.contact_service.contact.api.dto.pub.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateContactMessageRequest {

	@NotBlank
	@Email
	@Size(min = 5, max = 254)
	private String email;

	@NotBlank
	@Size(min = 3, max = 120)
	private String subject;

	@NotBlank
	@Size(min = 10, max = 4000)
	private String content;

	public CreateContactMessageRequest() {
	}

	public CreateContactMessageRequest(String email, String subject, String content) {
		this.email = email;
		this.subject = subject;
		this.content = content;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
