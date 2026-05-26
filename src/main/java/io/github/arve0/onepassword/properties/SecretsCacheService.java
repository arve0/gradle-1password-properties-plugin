package io.github.arve0.onepassword.properties;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A Gradle {@link BuildService} that caches resolved 1Password secret values for the
 * duration of a single build.
 *
 * <p>Gradle's {@link org.gradle.api.provider.ValueSource} deduplication is scoped to a single
 * project's {@link org.gradle.api.provider.ProviderFactory}. In a multi-project build each
 * subproject therefore gets its own {@code ProviderFactory}, so the same {@code op://} reference
 * would be resolved once per subproject without this cache.
 *
 * <p>Because Gradle creates a new {@link BuildService} instance for every build (even when the
 * same daemon handles back-to-back builds), the cache is automatically cleared between builds.
 * This ensures Gradle's configuration-cache fingerprinting always sees a fresh {@code op} call
 * on the first resolution within each build, while subsequent subprojects sharing the same
 * reference hit the in-memory map instead of invoking {@code op} again.
 */
public abstract class SecretsCacheService implements BuildService<BuildServiceParameters.None> {
    static final String SERVICE_NAME = "io.github.arve0.onepassword.secretsCache";

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Returns the cached value for {@code reference}, invoking {@code loader} to resolve it if absent.
     *
     * @param reference the {@code op://} reference used as the cache key
     * @param loader    called at most once per reference per build to fetch the secret
     * @return the resolved secret value
     */
    public String computeIfAbsent(String reference, Function<String, String> loader) {
        return cache.computeIfAbsent(reference, loader);
    }
}
