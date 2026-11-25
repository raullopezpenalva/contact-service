package com.raullopezpenalva.contact_service.shared.observability;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.api.trace.StatusCode;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ContactTracingTest {
    static class Exporter implements SpanExporter {
        final List<SpanData> spans = new CopyOnWriteArrayList<>();
        public CompletableResultCode export(Collection<SpanData> values) { spans.addAll(values); return CompletableResultCode.ofSuccess(); }
        public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
        public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
    }
    @Test void propagatesAcrossWorkerAndRestoresMdcWithoutSensitiveExceptionData() throws Exception {
        var exporter = new Exporter();
        var provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var tracing = new ContactTracing(OpenTelemetrySdk.builder().setTracerProvider(provider).build(), provider);
             var pool = Executors.newSingleThreadExecutor()) {
            MDC.put("traceId", "previous");
            Runnable job;
            try (var root = tracing.root()) {
                String trace = MDC.get("traceId");
                job = tracing.decorate(() -> {
                    assertEquals(trace, MDC.get("traceId"));
                    assertThrows(IllegalStateException.class, () -> tracing.call(ContactTracing.Operation.TELEGRAM,
                        () -> { throw new IllegalStateException("TOKEN-AND-FORM-CANARY"); }));
                });
            }
            assertEquals("previous", MDC.get("traceId"));
            pool.submit(job).get(3, TimeUnit.SECONDS);
            assertNull(pool.submit(() -> MDC.get("traceId")).get());
            assertEquals(4, exporter.spans.size());
            assertEquals(1, exporter.spans.stream().map(SpanData::getTraceId).distinct().count());
            var error = exporter.spans.stream().filter(s -> s.getName().equals("notification.telegram")).findFirst().orElseThrow();
            assertEquals(StatusCode.ERROR, error.getStatus().getStatusCode());
            for (var span : exporter.spans) {
                assertTrue(span.getAttributes().isEmpty()); assertTrue(span.getEvents().isEmpty());
                assertTrue(span.getLinks().isEmpty()); assertEquals("", span.getStatus().getDescription());
            }
        } finally { MDC.remove("traceId"); }
    }
    @Test void isolatedRootsAndNoUnsolicitedBackgroundSpans() {
        var exporter = new Exporter();
        var provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var tracing = new ContactTracing(OpenTelemetrySdk.builder().setTracerProvider(provider).build(), provider)) {
            tracing.call(ContactTracing.Operation.EMAIL, () -> "background");
            try (var first = tracing.root()) { try (var second = tracing.root()) {} }
            assertEquals(2, exporter.spans.size());
            assertEquals(2, exporter.spans.stream().map(SpanData::getTraceId).distinct().count());
            assertTrue(exporter.spans.stream().allMatch(s -> s.getParentSpanId().equals("0000000000000000")));
        }
    }
    @Test void disabledModeAndEndpointValidation() {
        var config = new ContactTracingConfiguration();
        try (var tracing = config.contactTracing(false, "invalid", Double.NaN)) {
            assertEquals("ok", tracing.call(ContactTracing.Operation.PERSIST, () -> "ok"));
            tracing.decorate(() -> {}).run();
        }
        for (String endpoint : List.of("", "http://user:token@localhost/v1/traces", "http://localhost/v1/traces?q=token", "http://localhost/wrong")) {
            assertThrows(IllegalArgumentException.class, () -> config.contactTracing(true, endpoint, 1));
        }
    }
    @Test void concurrentRequestsDoNotShareTraceOrWorkerContext() throws Exception {
        var exporter = new Exporter();
        var provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var tracing = new ContactTracing(OpenTelemetrySdk.builder().setTracerProvider(provider).build(), provider);
             var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<String> request = () -> {
                try (var root = tracing.root()) {
                    barrier.await(3, TimeUnit.SECONDS);
                    String trace = MDC.get("traceId");
                    tracing.call(ContactTracing.Operation.PERSIST, () -> "ok");
                    return trace;
                }
            };
            var first = pool.submit(request); var second = pool.submit(request);
            assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(2, exporter.spans.stream().map(SpanData::getTraceId).distinct().count());
            assertNull(pool.submit(() -> MDC.get("traceId")).get());
        }
    }
    @Test void unavailableExporterDoesNotDelayBusinessCall() {
        try (var tracing = new ContactTracingConfiguration().contactTracing(true, "http://127.0.0.1:1/v1/traces", 1)) {
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> {
                try (var root = tracing.root()) { assertEquals("ok", tracing.call(ContactTracing.Operation.PERSIST, () -> "ok")); }
            });
        }
    }
}
