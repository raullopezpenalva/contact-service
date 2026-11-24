package com.raullopezpenalva.contact_service.shared.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.slf4j.MDC;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Deliberately narrow API: no arbitrary names, attributes, headers or exceptions. */
public final class ContactTracing implements AutoCloseable {
    public enum Operation {
        HTTP("contact.http"), PERSIST("contact.persist"), QUEUE("notification.queue"),
        PROCESS("notification.process"), EMAIL("notification.email"), TELEGRAM("notification.telegram");
        final String name;
        Operation(String name) { this.name = name; }
    }
    private final Tracer tracer;
    private final SdkTracerProvider provider;
    public ContactTracing() { tracer = null; provider = null; }
    public ContactTracing(OpenTelemetry telemetry, SdkTracerProvider provider) {
        tracer = new OtelTracer(telemetry.getTracer("contact.pilot", "1"), new OtelCurrentTraceContext(), event -> {});
        this.provider = provider;
    }
    public Scope root() { return open(Operation.HTTP, true); }
    public Scope open(Operation operation) { return open(operation, false); }
    private Scope open(Operation operation, boolean root) {
        if (tracer == null || (!root && tracer.currentSpan() == null)) return new Scope(null);
        Span span = root ? tracer.spanBuilder().setNoParent().name(operation.name).kind(Span.Kind.SERVER).start()
            : tracer.nextSpan().name(operation.name).start();
        return new Scope(span);
    }
    public <T> T call(Operation operation, Supplier<T> action) {
        try (var scope = open(operation)) {
            try { return action.get(); }
            catch (RuntimeException | Error ex) { scope.failed(); throw ex; }
        }
    }
    /** Capture only our current span, never baggage or arbitrary caller ThreadLocals. */
    public Runnable decorate(Runnable task) {
        Span parent = tracer == null ? null : tracer.currentSpan();
        if (parent == null) return task;
        long queuedAt = System.currentTimeMillis();
        return () -> {
            tracer.spanBuilder().setParent(parent.context()).name(Operation.QUEUE.name)
                .startTimestamp(queuedAt, TimeUnit.MILLISECONDS).start().end();
            try (var parentScope = tracer.withSpan(parent); var process = open(Operation.PROCESS)) {
                try { task.run(); }
                catch (RuntimeException | Error ex) { process.failed(); throw ex; }
            }
        };
    }
    public void flush() { if (provider != null) provider.forceFlush().join(5, TimeUnit.SECONDS); }
    @Override public void close() { if (provider != null) provider.close(); }

    public final class Scope implements AutoCloseable {
        private final Span span;
        private final Tracer.SpanInScope scope;
        private final io.opentelemetry.api.trace.Span nativeSpan;
        private final String previousTrace = MDC.get("traceId"), previousSpan = MDC.get("spanId");
        private Scope(Span span) {
            this.span = span;
            scope = span == null ? null : tracer.withSpan(span);
            nativeSpan = span == null ? null : io.opentelemetry.api.trace.Span.fromContext(Context.current());
            if (span != null) {
                MDC.put("traceId", span.context().traceId()); MDC.put("spanId", span.context().spanId());
            }
        }
        public void failed() { if (nativeSpan != null) nativeSpan.setStatus(StatusCode.ERROR); }
        @Override public void close() {
            if (span == null) return;
            try { scope.close(); }
            finally {
                restore("traceId", previousTrace); restore("spanId", previousSpan); span.end();
            }
        }
    }
    private static void restore(String key, String previous) {
        if (previous == null) MDC.remove(key); else MDC.put(key, previous);
    }
}
