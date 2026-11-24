package com.raullopezpenalva.contact_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude={org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class,
    org.springframework.boot.actuate.autoconfigure.logging.OpenTelemetryLoggingAutoConfiguration.class,
    org.springframework.boot.actuate.autoconfigure.logging.otlp.OtlpLoggingAutoConfiguration.class,
    org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration.class,
    org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration.class,
    org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration.class,
    org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpTracingAutoConfiguration.class})
public class ContactServiceApplication {

	public static void main(String[] args) {
		if (args.length > 0 && args[0].equals("--admin-operator")) {
			int result = com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.operator.AdminOperator
				.run(java.util.Arrays.copyOfRange(args, 1, args.length));
			if (result != 0) System.exit(result);
			return;
		}
		SpringApplication.run(ContactServiceApplication.class, args);
	}

}
