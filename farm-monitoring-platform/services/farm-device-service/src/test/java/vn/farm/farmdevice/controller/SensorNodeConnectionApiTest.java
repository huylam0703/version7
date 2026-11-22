package vn.farm.farmdevice.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.farm.core.BusinessException;
import vn.farm.farmdevice.common.Access;
import vn.farm.farmdevice.config.WebErrorHandler;
import vn.farm.farmdevice.dto.SensorDtos.NodeConnectionResponse;
import vn.farm.farmdevice.entity.ConnectionType;
import vn.farm.farmdevice.entity.NodeStatus;
import vn.farm.farmdevice.service.SensorNodeService;

/** Test API (MockMvc standalone, dùng WebErrorHandler thật) + test phân quyền của Access.internal(). */
class SensorNodeConnectionApiTest {
    private final SensorNodeService service = mock(SensorNodeService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SensorNodeController(service))
            .setControllerAdvice(new WebErrorHandler()).build();
    private final UUID id = UUID.randomUUID();

    @BeforeEach @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void ok200() throws Exception {
        when(service.connection(id)).thenReturn(new NodeConnectionResponse(id, "ESP32-AABB",
                ConnectionType.HTTP, "http://192.168.1.50", NodeStatus.ACTIVE, null));
        mvc.perform(get("/api/v1/nodes/{id}/connection", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value(id.toString()))
                .andExpect(jsonPath("$.deviceUid").value("ESP32-AABB"))
                .andExpect(jsonPath("$.connectionType").value("HTTP"))
                .andExpect(jsonPath("$.endpointUrl").value("http://192.168.1.50"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void notFound404() throws Exception {
        when(service.connection(id)).thenThrow(BusinessException.notFound("NODE_NOT_FOUND", "x"));
        mvc.perform(get("/api/v1/nodes/{id}/connection", id))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NODE_NOT_FOUND"));
    }

    @Test
    void noEndpoint404() throws Exception {
        when(service.connection(id)).thenThrow(BusinessException.notFound("NODE_ENDPOINT_NOT_SET", "x"));
        mvc.perform(get("/api/v1/nodes/{id}/connection", id))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NODE_ENDPOINT_NOT_SET"));
    }

    @Test
    void inactive409() throws Exception {
        when(service.connection(id)).thenThrow(BusinessException.conflict("NODE_NOT_ACTIVE", "x"));
        mvc.perform(get("/api/v1/nodes/{id}/connection", id))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NODE_NOT_ACTIVE"));
    }

    @Test
    void badUuid400() throws Exception {
        mvc.perform(get("/api/v1/nodes/abc/connection"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_UUID"));
    }

    private Access securedAccess() {
        Access a = new Access();
        ReflectionTestUtils.setField(a, "enabled", true);
        return a;
    }

    private void loginAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u", "n", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void onlyServiceAndAdminPassInternalCheck() {
        Access a = securedAccess();
        assertThat(a.internal()).as("chưa đăng nhập").isFalse();
        loginAs("SERVICE");
        assertThat(a.internal()).isTrue();
        loginAs("ADMIN");
        assertThat(a.internal()).isTrue();
        loginAs("OPERATOR");
        assertThat(a.internal()).isFalse();
        loginAs("VIEWER");
        assertThat(a.internal()).isFalse();
    }
}
