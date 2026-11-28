package vn.farm.farmdevice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.core.BusinessException;
import vn.farm.farmdevice.dto.SensorDtos.RegisterNodeRequest;
import vn.farm.farmdevice.dto.SensorDtos.RegisterNodeResponse;
import vn.farm.farmdevice.entity.NodeStatus;
import vn.farm.farmdevice.entity.SensorNode;
import vn.farm.farmdevice.repository.SensorNodeRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NodeRegistrationService {

    private final SensorNodeRepository nodes;
    private final PasswordEncoder encoder;


    @Transactional
    public RegisterNodeResponse register(RegisterNodeRequest request) {

        SensorNode node = authenticate(request);

        String ipAddress = validateIpAddress(request.ipAddress());

        String endpointUrl = "http://" + ipAddress + ":80";

        Instant now = Instant.now();

        node.setEndpointUrl(endpointUrl);
        node.setLastSeenAt(now);

        SensorNode saved = nodes.save(node);

        return new RegisterNodeResponse(
                saved.getId(),
                saved.getDeviceUid(),
                saved.getEndpointUrl(),
                saved.getLastSeenAt(),
                true
        );
    }

    private SensorNode authenticate(RegisterNodeRequest request) {

        String deviceUid = request.deviceUid().trim();
        String authKey = request.authKey();

        if (authKey.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw BusinessException.invalid(
                    "VALIDATION_FAILED",
                    "authKey không được vượt quá 72 byte."
            );
        }

        SensorNode node = nodes
                .findByDeviceUidIgnoreCase(deviceUid)
                .orElseThrow(this::invalidCredentials);

        if (node.getAuthKeyHash() == null
                || !encoder.matches(authKey, node.getAuthKeyHash())) {
            throw invalidCredentials();
        }

        if (node.getStatus() != NodeStatus.ACTIVE) {
            throw BusinessException.conflict(
                    "NODE_NOT_ACTIVE",
                    "Node không ở trạng thái ACTIVE."
            );
        }

        return node;
    }


    private String validateIpAddress(String value) {

        if (value == null || value.isBlank()) {
            throw invalidIpAddress();
        }

        String ip = value.trim();
        String[] parts = ip.split("\\.", -1);

        if (parts.length != 4) {
            throw invalidIpAddress();
        }

        int[] octets = new int[4];

        for (int i = 0; i < parts.length; i++) {

            if (!parts[i].matches("0|[1-9][0-9]{0,2}")) {
                throw invalidIpAddress();
            }

            int number = Integer.parseInt(parts[i]);

            if (number > 255) {
                throw invalidIpAddress();
            }

            octets[i] = number;
        }

        boolean isPrivateIp =
                octets[0] == 10
                        || (octets[0] == 172
                        && octets[1] >= 16
                        && octets[1] <= 31)
                        || (octets[0] == 192
                        && octets[1] == 168);

        if (!isPrivateIp) {
            throw BusinessException.invalid(
                    "IP_NETWORK_NOT_ALLOWED",
                    "Địa chỉ IP phải thuộc dải IPv4 private."
            );
        }

        return ip;
    }


    private BusinessException invalidCredentials() {
        return BusinessException.invalid(
                "INVALID_DEVICE_CREDENTIALS",
                "Thông tin xác thực thiết bị không hợp lệ."
        );
    }

    private BusinessException invalidIpAddress() {
        return BusinessException.invalid(
                "INVALID_IP_ADDRESS",
                "Địa chỉ IPv4 không hợp lệ."
        );
    }
}