package com.api.quimia.domain.catalog.internal.image;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class ImageStorageConfiguration {
    private static final Duration CONEXAO = Duration.ofSeconds(3);
    private static final Duration LEITURA = Duration.ofSeconds(15);

    @Bean
    ImageStorage imageStorage(
            @Value("${quimia.cloudinary.cloud-name:}") String cloudName,
            @Value("${quimia.cloudinary.api-key:}") String apiKey,
            @Value("${quimia.cloudinary.api-secret:}") String apiSecret) {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            return new DisabledImageStorage();
        }
        JdkClientHttpRequestFactory fabrica =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONEXAO).build());
        fabrica.setReadTimeout(LEITURA);
        RestClient client = RestClient.builder().requestFactory(fabrica).build();
        return new CloudinaryImageStorage(client, Clock.systemUTC(), cloudName, apiKey, apiSecret);
    }
}
