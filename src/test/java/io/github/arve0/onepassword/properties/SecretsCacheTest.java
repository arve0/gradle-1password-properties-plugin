package io.github.arve0.onepassword.properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecretsCacheTest {

    @AfterEach
    void clearCache() {
        SecretsCache.clear();
    }

    @Test
    void callsLoaderOnlyOnceForSameReference() {
        AtomicInteger calls = new AtomicInteger();

        String first = SecretsCache.computeIfAbsent("op://vault/item/field", ref -> {
            calls.incrementAndGet();
            return "secret-value";
        });
        String second = SecretsCache.computeIfAbsent("op://vault/item/field", ref -> {
            calls.incrementAndGet();
            return "secret-value";
        });

        assertEquals("secret-value", first);
        assertEquals("secret-value", second);
        assertEquals(1, calls.get());
    }

    @Test
    void callsLoaderSeparatelyForDistinctReferences() {
        AtomicInteger calls = new AtomicInteger();

        SecretsCache.computeIfAbsent("op://vault/item/field-a", ref -> { calls.incrementAndGet(); return "a"; });
        SecretsCache.computeIfAbsent("op://vault/item/field-b", ref -> { calls.incrementAndGet(); return "b"; });

        assertEquals(2, calls.get());
    }
}
