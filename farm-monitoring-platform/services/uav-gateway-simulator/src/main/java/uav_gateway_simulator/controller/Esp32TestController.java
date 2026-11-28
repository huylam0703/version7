
package uav_gateway_simulator.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import uav_gateway_simulator.client.Esp32Client;

import java.util.UUID;

@RestController
@RequestMapping("/test/esp32")
@RequiredArgsConstructor
@Profile("dev")
public class Esp32TestController {

    private final Esp32Client esp32Client;

    @GetMapping("/{nodeId}")
    public JsonNode readByNodeId(@PathVariable UUID nodeId) {
        return esp32Client.readByNodeId(nodeId);
    }
}
