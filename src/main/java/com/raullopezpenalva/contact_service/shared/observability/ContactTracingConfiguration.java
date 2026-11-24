package com.raullopezpenalva.contact_service.shared.observability;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.URI;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class ContactTracingConfiguration {
    // Adding the tracing API must not switch existing HTTP metrics to a handler
    // that expects automatic tracing. Metrics retain their ordinary lifecycle.
    @Bean
    io.micrometer.core.instrument.observation.DefaultMeterObservationHandler metricObservations(
            io.micrometer.core.instrument.MeterRegistry registry) {
        return new io.micrometer.core.instrument.observation.DefaultMeterObservationHandler(registry);
    }

    @Bean(destroyMethod = "close")
    ContactTracing contactTracing(@Value("${app.tracing.enabled:false}") boolean enabled,
            @Value("${app.tracing.endpoint:}") String endpoint,
            @Value("${app.tracing.sampling-probability:1.0}") double probability) {
        if (!enabled) return new ContactTracing();
        URI uri;
        try { uri = URI.create(endpoint); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid credential-free OTLP endpoint"); }
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !"/v1/traces".equals(uri.getPath()) || !Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Invalid credential-free OTLP endpoint or sampling probability");
        }
        var exporter = OtlpHttpSpanExporter.builder().setEndpoint(endpoint).setTimeout(Duration.ofSeconds(2)).build();
        var provider = SdkTracerProvider.builder()
            .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "contact-service")))
            .setSpanLimits(SpanLimits.builder().setMaxNumberOfAttributes(0).setMaxNumberOfEvents(0).setMaxNumberOfLinks(0).build())
            .setSampler(Sampler.parentBased(Sampler.traceIdRatioBased(probability)))
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).setMaxQueueSize(256).setMaxExportBatchSize(32)
                .setScheduleDelay(Duration.ofSeconds(1)).setExporterTimeout(Duration.ofSeconds(2)).build()).build();
        var sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build();
        return new ContactTracing(sdk, provider);
    }
}
