package io.github.arve0.onepassword.properties;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecretsCacheServiceTest {

    // Concrete subclass to satisfy Gradle's abstract BuildService contract in tests.
    static class TestCacheService extends SecretsCacheService {
        @Override
        public BuildServiceParameters.None getParameters() { return null; }
    }

    @Test
    void callsLoaderOnlyOnceForSameReference() {
        SecretsCacheService service = new TestCacheService();
        AtomicInteger calls = new AtomicInteger();

        String first  = service.computeIfAbsent("op://vault/item/field", ref -> { calls.incrementAndGet(); return "secret"; });
        String second = service.computeIfAbsent("op://vault/item/field", ref -> { calls.incrementAndGet(); return "secret"; });

        assertEquals("secret", first);
        assertEquals("secret", second);
        assertEquals(1, calls.get());
    }

    @Test
    void callsLoaderSeparatelyForDistinctReferences() {
        SecretsCacheService service = new TestCacheService();
        AtomicInteger calls = new AtomicInteger();

        service.computeIfAbsent("op://vault/item/field-a", ref -> { calls.incrementAndGet(); return "a"; });
        service.computeIfAbsent("op://vault/item/field-b", ref -> { calls.incrementAndGet(); return "b"; });

        assertEquals(2, calls.get());
    }

    @Test
    void separateInstancesDoNotShareCache() {
        AtomicInteger calls = new AtomicInteger();
        SecretsCacheService service1 = new TestCacheService();
        SecretsCacheService service2 = new TestCacheService();

        service1.computeIfAbsent("op://vault/item/field", ref -> { calls.incrementAndGet(); return "v1"; });
        service2.computeIfAbsent("op://vault/item/field", ref -> { calls.incrementAndGet(); return "v2"; });

        assertEquals(2, calls.get(), "each build (service instance) should call op independently");
    }
}
