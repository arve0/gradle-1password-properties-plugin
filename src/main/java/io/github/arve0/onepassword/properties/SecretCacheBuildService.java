package io.github.arve0.onepassword.properties;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Build-scoped in-memory cache for resolved 1Password secret values.
 *
 * <p>A single instance is shared across all projects in a build via a static volatile
 * reference. When the plugin is applied to any project, it force-instantiates this service
 * and registers it as the active instance. When the build finishes, {@link #close()} clears
 * the reference so the next build starts with an empty cache.
 *
 * <p>This means a secret reference used by multiple projects in a large monorepo is only
 * resolved by one {@code op read} call per build, saving time when many projects share
 * the same secrets.
 */
public abstract class SecretCacheBuildService
        implements BuildService<BuildServiceParameters.None>, AutoCloseable {

    static final String SERVICE_NAME = "onePasswordSecretCache";

    private static volatile SecretCacheBuildService ACTIVE = null;

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Called by the plugin's {@code apply()} to register this instance as the active cache
     * for the current build. Only the first call per build takes effect.
     */
    static void activate(SecretCacheBuildService instance) {
        if (ACTIVE == null) {
            ACTIVE = instance;
        }
    }

    /**
     * Returns the cached value for {@code key}, or calls {@code loader} and caches the result.
     * Falls back to a direct {@code loader} call when no service is active (e.g. during a
     * configuration-cache warm load where the plugin's {@code apply()} is not invoked).
     */
    static String resolve(String key, Function<String, String> loader) {
        SecretCacheBuildService instance = ACTIVE;
        if (instance != null) {
            return instance.cache.computeIfAbsent(key, loader);
        }
        return loader.apply(key);
    }

    @Override
    public void close() {
        ACTIVE = null;
    }
}
