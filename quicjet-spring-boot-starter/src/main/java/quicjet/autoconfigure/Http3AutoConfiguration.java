package quicjet.autoconfigure;

import quicjet.client.QuicHttpClient;
import quicjet.server.Http3Server;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

@AutoConfiguration
@EnableConfigurationProperties(Http3Properties.class)
@ConditionalOnProperty(prefix = "quicjet", name = "enabled", havingValue = "true", matchIfMissing = true)
public class Http3AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public QuicHttpClient quicHttpClient(Http3Properties properties) {
        QuicHttpClient.Builder builder = QuicHttpClient.builder()
                .connectTimeout(properties.getClient().getConnectTimeout())
                .idleTimeout(properties.getClient().getIdleTimeout())
                .trustAllCertificates(properties.getClient().isTrustAllCertificates())
                .followRedirects(properties.getClient().isFollowRedirects());

        if (properties.getTruststore().getPath() != null) {
            builder.trustStore(Path.of(properties.getTruststore().getPath()), properties.getTruststore().getPassword());
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public Http3Server http3Server(Http3Properties properties) {
        Http3Server.Builder builder = Http3Server.builder()
                .host(properties.getHost())
                .port(properties.getPort())
                .virtualThreads(properties.isVirtualThreads())
                .maxRequestBodySize(properties.getMaxRequestBodySize());


        if (properties.getKeystore().getPath() != null) {
            Path ksPath = Path.of(properties.getKeystore().getPath());
            String ksPass = properties.getKeystore().getPassword();
            String keyPass = properties.getKeystore().getKeyPassword();
            if (keyPass != null) {
                builder.keyStore(ksPath, ksPass, keyPass);
            } else {
                builder.keyStore(ksPath, ksPass);
            }
        }

        if (properties.getTruststore().getPath() != null) {
            builder.trustStore(Path.of(properties.getTruststore().getPath()), properties.getTruststore().getPassword());
        }

        return builder.build();
    }
}
