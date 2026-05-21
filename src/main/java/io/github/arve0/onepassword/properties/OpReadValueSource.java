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
    }

    @Override
    public String obtain() {
        Parameters params = getParameters();
        String reference = params.getReference().get();
        return SecretsCache.computeIfAbsent(reference, ref -> {
            OpCliClient client = new OpCliClient(
                    params.getCommand().get(),
                    Duration.ofMillis(params.getTimeoutMillis().get())
            );
            ProjectPropertyResolver resolver = new ProjectPropertyResolver(client);
            return resolver.resolve(params.getPropertyName().get(), ref);
        });
    }
}
