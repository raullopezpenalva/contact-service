package com.raullopezpenalva.contact_service;

import com.raullopezpenalva.contact_service.modules.platform.websecurity.application.RateLimitService;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.filter.ContactRateLimitFilter;
import com.raullopezpenalva.contact_service.modules.contact.api.dto.pub.request.CreateContactMessageRequest;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class HardeningTest {
    ContactRateLimitFilter filter() {
        var f = new ContactRateLimitFilter(new RateLimitService());
        ReflectionTestUtils.setField(f, "publicLimit", 2);
        ReflectionTestUtils.setField(f, "publicWindow", 3600L);
        ReflectionTestUtils.setField(f, "adminLimit", 2);
        ReflectionTestUtils.setField(f, "adminWindow", 60L);
        ReflectionTestUtils.setField(f, "maxBytes", 100);
        ReflectionTestUtils.setField(f, "trustedProxies", "10.0.0.2");
        return f;
    }
    MockHttpServletRequest request(String path) {
        var r = new MockHttpServletRequest("POST", path);
        r.setRemoteAddr("10.0.0.1");
        return r;
    }
    @Test void rejectsOversizedUnknownLengthBodyBeforeDownstream() throws Exception {
        var r = new MockHttpServletRequest("POST", "/api/v1/contact/messages") {
            @Override public long getContentLengthLong() { return -1; }
        };
        r.setContent(new byte[101]);
        var response = new MockHttpServletResponse();
        filter().doFilter(r, response, (a,b) -> fail("Must not reach persistence"));
        assertEquals(413, response.getStatus());
    }
    @Test void rejectsDeclaredOversizedBody() throws Exception {
        var r = request("/api/v1/contact/messages"); r.setContent(new byte[101]);
        var response = new MockHttpServletResponse();
        filter().doFilter(r, response, (a,b) -> fail());
        assertEquals(413, response.getStatus());
    }
    @Test void preservesBodyAndBlocksSpoofedForwardingHeaders() throws Exception {
        var f = filter();
        for (int i=0;i<3;i++) {
            var r = request("/api/v1/contact/messages");
            r.addHeader("X-Forwarded-For", "192.0.2."+i);
            r.addHeader("X-Real-IP", "192.0.2."+i);
            r.setContent("hello".getBytes());
            var response = new MockHttpServletResponse();
            f.doFilter(r, response, (a,b) -> assertEquals("hello", new String(a.getInputStream().readAllBytes())));
            assertEquals(i<2 ? 200 : 429, response.getStatus());
            if(i==2) assertEquals("3600",response.getHeader("Retry-After"));
        }
    }
    @Test void trustedProxySeparatesClients() throws Exception {
        var f = filter();
        for(int i=0;i<4;i++) {
            var r=request("/api/v1/contact/messages");r.setRemoteAddr("10.0.0.2");
            r.addHeader("X-Real-IP","192.0.2."+i);
            var response=new MockHttpServletResponse();
            f.doFilter(r,response,(a,b)->{});assertEquals(200,response.getStatus());
        }
    }
    @Test void adminQuotaIsSeparateAndOtherRoutesPass() throws Exception {
        var f=filter();
        for(int i=0;i<3;i++) {
            var response=new MockHttpServletResponse();
            f.doFilter(request("/api/v1/admin/contact/messages"),response,(a,b)->{});
            assertEquals(i<2?200:429,response.getStatus());
        }
        var response=new MockHttpServletResponse();
        f.doFilter(request("/api/v1/contact/messages"),response,(a,b)->{});
        assertEquals(200,response.getStatus());
        f.doFilter(request("/actuator/health"),response,(a,b)->{});
        assertEquals(200,response.getStatus());
    }
    @Test void atomicQuotaUnderConcurrency() throws Exception {
        var limiter=new RateLimitService();var allowed=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(8)) {
            for(int i=0;i<100;i++) pool.submit(()->{if(limiter.allowRequest("ip",5,60))allowed.incrementAndGet();});
        }
        assertEquals(5,allowed.get());
    }
    @Test void resetsAtExactWindowBoundary() {
        class MutableClock extends Clock {
            Instant now=Instant.EPOCH;
            public ZoneId getZone(){return ZoneOffset.UTC;}
            public Clock withZone(ZoneId zone){return this;}
            public Instant instant(){return now;}
        }
        var clock=new MutableClock();var limiter=new RateLimitService(clock);
        assertTrue(limiter.allowRequest("ip",1,60));
        assertFalse(limiter.allowRequest("ip",1,60));
        clock.now=Instant.EPOCH.plusSeconds(60);
        assertTrue(limiter.allowRequest("ip",1,60));
    }
    @Test void boundsClientMapAndKeepsExistingClientsUsable() {
        var limiter = new RateLimitService();
        for(int i=0;i<10000;i++) assertTrue(limiter.allowRequest("ip"+i,2,60));
        assertFalse(limiter.allowRequest("new-client",2,60));
        assertTrue(limiter.allowRequest("ip0",2,60));
    }
    @Test void validatesInputLimits() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var v=factory.getValidator();
            assertTrue(v.validate(new CreateContactMessageRequest("a@example.com","Hello","x".repeat(3000))).isEmpty());
            assertFalse(v.validate(new CreateContactMessageRequest("invalid","Hi","short")).isEmpty());
            assertFalse(v.validate(new CreateContactMessageRequest("a@example.com","Hello","x".repeat(3001))).isEmpty());
            assertFalse(v.validate(new CreateContactMessageRequest("a".repeat(245)+"@example.com","Hello","valid message")).isEmpty());
        }
    }
}
