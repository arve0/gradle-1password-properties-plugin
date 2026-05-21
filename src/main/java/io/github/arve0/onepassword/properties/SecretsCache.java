package io.github.arve0.onepassword.properties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * JVM-level cache for resolved 1Password secret references.
 *
 * <p>Gradle's {@link org.gradle.api.provider.ValueSource} deduplication is scoped to a single
 * project's {@link org.gradle.api.provider.ProviderFactory}. In a multi-project build each
 * subproject therefore gets its own {@code ProviderFactory}, so the same {@code op://} reference
 * would be resolved once per subproject without this cache.
 *
 * <p>Because all projects in a Gradle build share the same JVM (daemon), a static map provides
 * exactly the right scope: one {@code op} invocation per unique reference per daemon.
 */
final class SecretsCache {
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private SecretsCache() {}

    static String computeIfAbsent(String reference, Function<String, String> loader) {
        return CACHE.computeIfAbsent(reference, loader);
    }

    /** Visible for testing only. Clears the cache between test cases. */
    static void clear() {
        CACHE.clear();
    }
}
