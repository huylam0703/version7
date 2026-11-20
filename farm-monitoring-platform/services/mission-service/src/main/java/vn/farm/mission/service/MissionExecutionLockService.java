package vn.farm.mission.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.core.BusinessException;
import vn.farm.mission.entity.MissionExecution;
import vn.farm.mission.repository.MissionExecutionRepository;
import vn.farm.mission.repository.MissionRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MissionExecutionLockService {

    private final MissionRepository missionRepository;
    private final MissionExecutionRepository executionRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public MissionExecution lock(
            UUID missionId,
            UUID executionId
    ) {
        missionRepository.findForResults(missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "MISSION_NOT_FOUND",
                        "Không tìm thấy Mission"
                ));

        return executionRepository
                .findForProgress(executionId, missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "EXECUTION_NOT_FOUND",
                        "Không tìm thấy Mission Execution"
                ));
    }
}