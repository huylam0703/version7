package vn.farm.starter.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnProperty(prefix = "farm.auth", name = "token-uri")
public class FarmClientAutoConfiguration {

    @Bean
    ServiceTokenProvider serviceTokenProvider(ObjectProvider<RestClient.Builder> builders,
                                              @Value("${farm.auth.token-uri}") String uri,
                                              @Value("${farm.auth.client-id}") String id,
                                              @Value("${farm.auth.client-secret}") String secret) {
        return new ServiceTokenProvider(builders.getObject(), uri, id, secret);
    }

    @Bean
    ServiceClient serviceClient(ObjectProvider<RestClient.Builder> builders, ServiceTokenProvider tokens) {
        return new ServiceClient(builders, tokens);
    }
}