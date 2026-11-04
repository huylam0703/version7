package vn.farm.mission.dto.response;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionCheckResponse {

    private boolean valid;
    private List<Problem> problems;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Problem {
        private String field;
        private String code;
        private String message;
    }
}

