package io.github.arve0.onepassword.properties;

import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

import java.time.Duration;

/**
 * A Gradle {@link ValueSource} that resolves a 1Password secret reference at execution time.
 *
 * <p>By delegating resolution to this ValueSource, the plugin exposes secrets as
 * {@code Provider<String>} instead of resolved {@code String} values. Gradle then controls
 * whether the secret ends up in the configuration cache based on when the caller calls
 * {@code .get()}:
 * <ul>
 *   <li>Called during execution (e.g. in {@code doLast}) → secret NOT stored in the cache,
 *       {@code op} called once per build at execution time.</li>
 *   <li>Called during configuration (e.g. for repository credentials) → Gradle fingerprints
 *       the value and stores it in the cache; {@code op} is called on every build for
 *       fingerprint validation.</li>
 * </ul>
 */
public abstract class OpReadValueSource implements ValueSource<String, OpReadValueSource.Parameters> {

    /** Parameters passed to {@link OpReadValueSource} for resolving a single 1Password secret. */
    public interface Parameters extends ValueSourceParameters {
        /**
         * The {@code op://} reference to resolve (e.g. {@code op://vault/item/field}).
         *
         * @return the 1Password reference property
         */
        Property<String> getReference();

        /**
         * The Gradle project property key — used in error messages.
         *
         * @return the property name property
         */
        Property<String> getPropertyName();

        /**
         * The {@code op} CLI command (e.g. {@code op} or a custom path).
         *
         * @return the CLI command property
         */
        Property<String> getCommand();

        /**
         * The timeout in milliseconds for the {@code op} CLI subprocess.
         *
         * @return the timeout property
         */
        Property<Long> getTimeoutMillis();

        /**
         * The per-build cache service that deduplicates {@code op} invocations across subprojects.
         * Optional — may be absent when the configuration cache is warm and the plugin was not
         * re-applied; in that case {@link #obtain()} falls back to a direct {@code op} invocation.
         *
         * @return the cache service property
         */
        Property<SecretsCacheService> getCacheService();
    }

    @Override
    public String obtain() {
        Parameters params = getParameters();
        String reference = params.getReference().get();
        Property<SecretsCacheService> cacheServiceProp = params.getCacheService();
        if (cacheServiceProp.isPresent()) {
            return cacheServiceProp.get().computeIfAbsent(reference, ref -> resolve(params, ref));
        }
        return resolve(params, reference);
    }

    private String resolve(Parameters params, String reference) {
        OpCliClient client = new OpCliClient(
                params.getCommand().get(),
                Duration.ofMillis(params.getTimeoutMillis().get())
        );
        return new ProjectPropertyResolver(client).resolve(params.getPropertyName().get(), reference);
    }
}
