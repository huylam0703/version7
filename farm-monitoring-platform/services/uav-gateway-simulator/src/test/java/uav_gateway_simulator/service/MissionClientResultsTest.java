
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.MissionNodeResultRequest;
import uav_gateway_simulator.dto.MissionResultRequest;
import uav_gateway_simulator.dto.MissionResultResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MissionClientResultsTest {

    private static final String BASE_URL =
            "http://localhost:8083/api/v1/mission";

    private MissionClient missionClient;
    private MockRestServiceServer server;

    private UUID missionId;
    private UUID executionId;
    private UUID nodeId;
    private MissionResultRequest request;

    @BeforeEach
    void setUp() {

        TokenService tokenService = mock(TokenService.class);

        when(tokenService.getAccessToken())
                .thenReturn("test-token");

        RestClient.Builder builder = RestClient.builder();

        server = MockRestServiceServer
                .bindTo(builder)
                .build();

        RecoveryLeaseRegistry leaseRegistry =
                new RecoveryLeaseRegistry();

        missionClient = new MissionClient(
                builder,
                tokenService,
                leaseRegistry,
                BASE_URL
        );

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
        nodeId = UUID.randomUUID();

        request = new MissionResultRequest(
                executionId,
                List.of(
                        new MissionNodeResultRequest(
                                nodeId,
                                "SUCCESS",
                                10,
                                1,
                                Instant.parse("2026-11-17T08:00:00Z"),
                                null,
                                null
                        )
                ),
                true
        );
    }

    @Test
    void shouldSendResultsSuccessfully() {

        String responseJson = successResponseJson();

        server.expect(requestTo(resultsUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        "Authorization",
                        "Bearer test-token"
                ))
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(content().json("""
                        {
                          "executionId": "%s",
                          "completed": true
                        }
                        """.formatted(executionId), false))
                .andRespond(withSuccess(
                        responseJson,
                        MediaType.APPLICATION_JSON
                ));

        MissionResultResponse response =
                missionClient.sendResults(missionId, request);

        assertNotNull(response);
        assertEquals(missionId, response.missionId());
        assertEquals(executionId, response.executionId());
        assertEquals("COMPLETED", response.status());
        assertEquals(1, response.totalNodes());
        assertEquals(1, response.successNodes());
        assertEquals(0, response.failedNodes());

        server.verify();
    }

    @Test
    void shouldRejectEmptyResultResponse() {

        server.expect(requestTo(resultsUrl()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "code": 1000,
                          "message": "Success",
                          "result": null
                        }
                        """, MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> missionClient.sendResults(
                        missionId,
                        request
                )
        );

        assertEquals(
                "Mission Results response is empty",
                exception.getMessage()
        );

        server.verify();
    }

    @Test
    void shouldRejectMissionIdMismatch() {

        UUID otherMissionId = UUID.randomUUID();

        server.expect(requestTo(resultsUrl()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "code": 1000,
                          "message": "Success",
                          "result": {
                            "missionId": "%s",
                            "executionId": "%s",
                            "status": "COMPLETED",
                            "totalNodes": 1,
                            "successNodes": 1,
                            "failedNodes": 0
                          }
                        }
                        """.formatted(
                        otherMissionId,
                        executionId
                ), MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> missionClient.sendResults(
                        missionId,
                        request
                )
        );

        assertEquals(
                "Mission Results response missionId mismatch",
                exception.getMessage()
        );

        server.verify();
    }

    @Test
    void shouldPropagateServerError() {

        server.expect(requestTo(resultsUrl()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThrows(
                RestClientResponseException.class,
                () -> missionClient.sendResults(
                        missionId,
                        request
                )
        );

        server.verify();
    }

    private String resultsUrl() {
        return BASE_URL + "/" + missionId + "/results";
    }

    private String successResponseJson() {
        return """
                {
                  "code": 1000,
                  "message": "Receive mission results successfully",
                  "result": {
                    "missionId": "%s",
                    "executionId": "%s",
                    "status": "COMPLETED",
                    "totalNodes": 1,
                    "successNodes": 1,
                    "failedNodes": 0
                  }
                }
                """.formatted(missionId, executionId);
    }
}
