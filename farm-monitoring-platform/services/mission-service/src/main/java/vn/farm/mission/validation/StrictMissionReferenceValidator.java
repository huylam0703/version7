
package vn.farm.mission.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import vn.farm.core.BusinessException;
import vn.farm.mission.client.FarmDeviceClient;
import vn.farm.mission.dto.integration.MissionCheckRequest;
import vn.farm.mission.dto.request.CreateMissionRequest;
import vn.farm.mission.dto.response.MissionCheckResponse;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "mission.reference-validation",
        name = "mode",
        havingValue = "remote",
        matchIfMissing = true
)
public class StrictMissionReferenceValidator
        implements MissionReferenceValidator {

    private final FarmDeviceClient farmDeviceClient;

    @Override
    public void validate(CreateMissionRequest request) {

        MissionCheckRequest checkRequest =
                new MissionCheckRequest(
                        request.getFarmId(),
                        request.getUavId(),
                        request.getGatewayId(),
                        request.getNodeIds()
                );

        MissionCheckResponse response;

        try {
            response = farmDeviceClient
                    .checkMissionReferences(checkRequest);
        } catch (RestClientException ex) {
            throw new IllegalStateException(
                    "Không thể xác thực tham chiếu với Farm-Device Service",
                    ex
            );
        }

        if (response == null) {
            throw new IllegalStateException(
                    "Farm-Device Service trả về response rỗng"
            );
        }

        if (!response.isValid()) {
            String message = response.getProblems() == null
                    ? "Mission references không hợp lệ"
                    : response.getProblems().stream()
                    .map(MissionCheckResponse.Problem::getMessage)
                    .collect(Collectors.joining("; "));

            throw BusinessException.invalid(
                    "INVALID_MISSION_REFERENCES",
                    message
            );
        }
    }
}
