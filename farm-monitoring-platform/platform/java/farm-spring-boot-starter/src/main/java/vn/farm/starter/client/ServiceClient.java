package vn.farm.starter.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;

public class ServiceClient {
    private final ObjectProvider<RestClient.Builder> builders;
    private final ServiceTokenProvider tokens;

    public ServiceClient(ObjectProvider<RestClient.Builder> builders, ServiceTokenProvider tokens) {
        this.builders = builders; this.tokens = tokens;
    }

    /** Ví dụ: serviceClient.to("http://farm-device-service:8080").get().uri("/internal/v1/nodes/{id}", id)... */
    public RestClient to(String baseUrl) {
        return builders.getObject().baseUrl(baseUrl)
                .requestInterceptor((req, body, exec) -> {
                    req.getHeaders().setBearerAuth(tokens.get());
                    return exec.execute(req, body);
                }).build();
    }
}