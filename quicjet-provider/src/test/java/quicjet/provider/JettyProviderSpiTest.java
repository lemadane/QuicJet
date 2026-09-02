package quicjet.provider;

import quicjet.provider.JettyQuicProvider;
import quicjet.spi.QuicProvider;
import quicjet.spi.QuicProviderLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JettyProviderSpiTest {

    @Test
    public void testSpiLoaderFindsJettyProvider() {
        QuicProvider provider = QuicProviderLoader.load();
        assertNotNull(provider, "QuicProvider should be loaded");
        assertEquals("jetty", provider.name().toLowerCase());
        assertTrue(provider instanceof JettyQuicProvider);
    }
}
