
package vn.farm.farmdevice.validation;

import org.springframework.stereotype.Component;
import vn.farm.core.BusinessException;

@Component
public class NodeIpValidator {

    public String validate(String ipAddress) {

        if (ipAddress == null || ipAddress.isBlank()) {
            throw invalidIp();
        }

        String ip = ipAddress.trim();
        String[] parts = ip.split("\\.", -1);

        if (parts.length != 4) {
            throw invalidIp();
        }

        int[] octets = new int[4];

        for (int i = 0; i < parts.length; i++) {

            if (!parts[i].matches("0|[1-9][0-9]{0,2}")) {
                throw invalidIp();
            }

            octets[i] = Integer.parseInt(parts[i]);

            if (octets[i] > 255) {
                throw invalidIp();
            }
        }

        boolean privateIp =
                octets[0] == 10
                        || (octets[0] == 172
                        && octets[1] >= 16
                        && octets[1] <= 31)
                        || (octets[0] == 192
                        && octets[1] == 168);

        if (!privateIp) {
            throw BusinessException.invalid(
                    "IP_NETWORK_NOT_ALLOWED",
                    "Chỉ chấp nhận địa chỉ IPv4 nội bộ."
            );
        }

        return ip;
    }

    private BusinessException invalidIp() {
        return BusinessException.invalid(
                "INVALID_IP_ADDRESS",
                "Địa chỉ IPv4 không hợp lệ."
        );
    }
}
