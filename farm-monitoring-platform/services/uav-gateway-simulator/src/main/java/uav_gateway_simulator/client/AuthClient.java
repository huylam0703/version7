package uav_gateway_simulator.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import uav_gateway_simulator.config.AuthProperties;
import uav_gateway_simulator.dto.response.TokenResponse;

@Component
@RequiredArgsConstructor
public class AuthClient {

    private final AuthProperties authProperties;

    private final RestClient restClient = RestClient.create();

    public TokenResponse requestToken() {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("grant_type", "client_credentials");
        form.add("scope", authProperties.scope());

        return restClient.post()
                .uri(authProperties.tokenUrl())
                .headers(headers -> {
                    headers.setBasicAuth(
                            authProperties.clientId(),
                            authProperties.clientSecret()
                    );
                    headers.setContentType(
                            MediaType.APPLICATION_FORM_URLENCODED
                    );
                })
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
    }
}