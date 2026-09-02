package quicjet.spi;

import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import quicjet.exception.QuicException;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class QuicProviderLoader {
    private static final Logger LOG = LoggerFactory.getLogger(QuicProviderLoader.class);
    private static final Map<String, QuicProvider> CACHED_PROVIDERS = new ConcurrentHashMap<>();
    private static volatile QuicProvider DEFAULT_PROVIDER;

    private QuicProviderLoader() {}

    public static QuicProvider load() {
        if (DEFAULT_PROVIDER != null) {
            return DEFAULT_PROVIDER;
        }

        synchronized (QuicProviderLoader.class) {
            if (DEFAULT_PROVIDER != null) {
                return DEFAULT_PROVIDER;
            }

            ServiceLoader<QuicProvider> loader = ServiceLoader.load(QuicProvider.class);
            for (QuicProvider provider : loader) {
                CACHED_PROVIDERS.put(provider.name().toLowerCase(), provider);
                LOG.info("Discovered and cached QuicProvider: '{}' [{}]", provider.name(), provider.getClass().getName());
                if (DEFAULT_PROVIDER == null) {
                    DEFAULT_PROVIDER = provider;
                }
            }

            if (DEFAULT_PROVIDER == null) {
                LOG.error("No QuicProvider implementation found on the classpath.");
                throw new QuicException("No QuicProvider implementation found on the classpath. Please ensure quicjet-provider (or another provider) is included.");
            }

            LOG.info("Selected default QuicProvider: '{}'", DEFAULT_PROVIDER.name());
            return DEFAULT_PROVIDER;
        }
    }

    public static QuicProvider get(String providerName) {
        load(); // ensure discovery ran
        QuicProvider provider = CACHED_PROVIDERS.get(providerName.toLowerCase());
        if (provider == null) {
            LOG.error("Requested QuicProvider '{}' not found", providerName);
            throw new QuicException("No QuicProvider found with name: " + providerName);
        }
        return provider;
    }
}

