
package uav_gateway_simulator.client;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uav_gateway_simulator.dto.response.ApiResponse;
import uav_gateway_simulator.dto.response.MissionResultResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MissionResultsContractTest {

    @Test
    void shouldDeserializeMissionResultsResponse() throws Exception {

        UUID missionId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        String json = """
                {
                  "code": 1000,
                  "message": "Receive mission results successfully",
                  "result": {
                    "missionId": "%s",
                    "executionId": "%s",
                    "status": "COMPLETED",
                    "totalNodes": 3,
                    "successNodes": 3,
                    "failedNodes": 0
                  }
                }
                """.formatted(missionId, executionId);

        ObjectMapper mapper = new ObjectMapper();

        ApiResponse<MissionResultResponse> response =
                mapper.readValue(
                        json,
                        mapper.getTypeFactory().constructParametricType(
                                ApiResponse.class,
                                MissionResultResponse.class
                        )
                );

        assertNotNull(response);
        assertNotNull(response.result());

        MissionResultResponse result = response.result();

        assertEquals(missionId, result.missionId());
        assertEquals(executionId, result.executionId());
        assertEquals("COMPLETED", result.status());
        assertEquals(3, result.totalNodes());
        assertEquals(3, result.successNodes());
        assertEquals(0, result.failedNodes());
    }
}
