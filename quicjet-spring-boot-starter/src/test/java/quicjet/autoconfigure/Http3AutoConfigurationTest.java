package quicjet.autoconfigure;

import quicjet.client.QuicHttpClient;
import quicjet.server.Http3Server;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URL;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

public class Http3AutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Http3AutoConfiguration.class));

    @Test
    public void testAutoConfigurationCreatesBeans() {
        URL keyStoreUrl = Http3AutoConfigurationTest.class.getResource("/test-keystore.p12");
        assertThat(keyStoreUrl).isNotNull();
        String keyStorePath = Paths.get(keyStoreUrl.getPath()).toString();

        contextRunner
            .withPropertyValues(
                "quicjet.port=9443",
                "quicjet.keystore.path=" + keyStorePath,
                "quicjet.keystore.password=password"
            )
                .run(context -> {
                    assertThat(context).hasSingleBean(QuicHttpClient.class);
                    assertThat(context).hasSingleBean(Http3Server.class);
                });
    }

    @Test
    public void testDisabledAutoConfiguration() {
        contextRunner
                .withPropertyValues("quicjet.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(QuicHttpClient.class);
                    assertThat(context).doesNotHaveBean(Http3Server.class);
                });
    }
}
