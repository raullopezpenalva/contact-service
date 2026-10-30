package com.raullopezpenalva.contact_service;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.application.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminHashAdmissionTest {
    @Test void admitsTwoComputationsAndRejectsThirdWithoutQueueThenReleasesSlots() throws Exception {
        var entered=new CountDownLatch(2);var release=new CountDownLatch(1);
        var service=new AdminLoginService(authentication->{
            entered.countDown();
            try {if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Timeout");}
            catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted");}
            throw new BadCredentialsException("Invalid credentials");
        },null);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->assertThrows(BadCredentialsException.class,()->service.login("admin",JwtTestSupport.PASSWORD.toCharArray())));
            var second=pool.submit(()->assertThrows(BadCredentialsException.class,()->service.login("admin",JwtTestSupport.PASSWORD.toCharArray())));
            try {
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                var limited=assertThrows(AuthFailure.class,()->service.login("admin",JwtTestSupport.PASSWORD.toCharArray()));
                assertEquals(429,limited.status);assertEquals(1,limited.retryAfter);
            } finally {release.countDown();}
            first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS);
        }
        assertThrows(BadCredentialsException.class,()->service.login("admin",JwtTestSupport.PASSWORD.toCharArray()));
    }
}
