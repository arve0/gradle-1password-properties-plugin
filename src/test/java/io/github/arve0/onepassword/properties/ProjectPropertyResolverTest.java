package io.github.arve0.onepassword.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectPropertyResolverTest {

    @Test
    void resolvesOpReferenceWithSecretReader() {
        ProjectPropertyResolver resolver = new ProjectPropertyResolver(reference -> "resolved-secret");

        String value = resolver.resolve("TOKEN", "op://vault/item/field");

        assertEquals("resolved-secret", value);
    }

    @Test
    void passesThroughNonOpValueUnchanged() {
        ProjectPropertyResolver resolver = new ProjectPropertyResolver(reference -> {
            throw new IllegalStateException("Should not be called");
        });

        String value = resolver.resolve("TOKEN", "plain-value");

        assertEquals("plain-value", value);
    }

    @Test
    void throwsExplicitErrorForMissingPropertyValue() {
        ProjectPropertyResolver resolver = new ProjectPropertyResolver(reference -> "unused");

        PropertyResolutionException exception = assertThrows(
                PropertyResolutionException.class,
                () -> resolver.resolve("TOKEN", null)
        );

        assertTrue(exception.getMessage().contains("Property 'TOKEN' is missing"));
    }

    @Test
    void throwsExplicitErrorForNonStringPropertyValue() {
        ProjectPropertyResolver resolver = new ProjectPropertyResolver(reference -> "unused");

        PropertyResolutionException exception = assertThrows(
                PropertyResolutionException.class,
                () -> resolver.resolve("TOKEN", 42)
        );

        assertTrue(exception.getMessage().contains("must be a String"));
    }

    @Test
    void mapsCliErrorsToPropertyContext() {
        ProjectPropertyResolver resolver = new ProjectPropertyResolver(reference -> {
            throw new OpCliException("CLI failed");
        });

        PropertyResolutionException exception = assertThrows(
                PropertyResolutionException.class,
                () -> resolver.resolve("TOKEN", "op://vault/item/field")
        );

        assertTrue(exception.getMessage().contains("Failed to resolve property 'TOKEN'"));
        assertTrue(exception.getMessage().contains("CLI failed"));
    }
}
