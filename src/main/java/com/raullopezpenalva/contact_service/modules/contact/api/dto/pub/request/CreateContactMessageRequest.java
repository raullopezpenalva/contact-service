package com.raullopezpenalva.contact_service.modules.contact.api.dto.pub.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateContactMessageRequest {

	@NotBlank(message = "Email is required")
	@Email(message = "Email should be valid")
	@Size(min = 5, max = 320, message = "Email must be between 5 and 320 characters")
	private String email;

	@NotBlank(message = "Subject is required")
	@Size(min = 3, max = 120, message = "Subject must be between 3 and 120 characters")
	private String subject;

	@NotBlank(message = "Content is required")
	@Size(min = 10, max = 3000, message = "Content must be between 10 and 3000 characters")
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
