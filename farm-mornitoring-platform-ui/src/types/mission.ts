
export type MissionWaypointDto = {
  waypointId: string
  seq: number
  lat: number
  lng: number
  altitudeM: number | null
  status: string
  nodeIds: string[]
}

// ===== Mission Response DTO =====

export type MissionDto = {
  missionId: string
  farmId: string
  uavId: string
  gatewayId: string
  name: string
  status: string
  plannedAt?: string
  totalNodes: number
  successNodes: number
  failedNodes: number
  nodeIds: string[]
  waypoints: MissionWaypointDto[]
}

export type MissionDetailDto = {
  missionId: string
  farmId: string
  uavId: string
  gatewayId: string
  name: string
  createdBy: string
  notes?: string
  status: string
  plannedAt?: string
  startedAt?: string
  endedAt?: string
  totalNodes: number
  successNodes: number
  failedNodes: number
  waypoints?: MissionWaypointDto[]
  nodeIds?: string[]
}

export type MissionPageDto = {
  content: MissionDto[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export type MissionNodeResultDto = {
  nodeId: string
  result: string | null
  recordsCount: number | null
  attempts: number | null
  collectedAt: string | null
  errorCode: string | null
  errorMessage: string | null
}

export type MissionStatusLogDto = {
  logId: string
  changedBy: string | null
  source: string | null
  oldStatus: string | null
  newStatus: string | null
  note: string | null
  createdAt: string
}

export type MissionProgressDto = {
  missionId: string
  executionId: string
  missionStatus: string
  executionStatus: string
  lastConfirmedSequence: number
  totalWaypoints: number
  reachedWaypoints: number
  totalNodes: number
  processedNodes: number
  progressPercent: number

  waypoints: {
    waypointId: string
    seq: number
    status: string
    reachedAt: string | null
  }[]

  nodes: {
    nodeId: string
    result: string
    recordsCount: number
    attempts: number | null
    collectedAt: string | null
  }[]
}

export type MissionProgressResponseDto = {
  missionId: string
  executionId: string
  executionStatus: string
  lastConfirmedSequence: number
  waypointSeq: number
  updatedAt: string
}

export type MissionAckResponseDto = {
  missionId: string
  executionId: string
  status: string
  acknowledgedAt: string
}

export type MissionRecoveryClaimResponseDto = {
  missionId: string
  executionId: string
  instanceId: string
  recoveryGeneration: number
  leaseUntil: string
}

export type MissionExecutionProgressResponseDto = {
  missionId: string
  executionId: string
  executionStatus: string
  lastConfirmedSequence: number
  acknowledgedAt: string | null
  lastHeartbeatAt: string | null
}

export type MissionHeartbeatResponseDto = {
  missionId: string
  executionId: string
  executionStatus: string
  lastHeartbeatAt: string
}

// ===== API Response Wrapper =====

export type MissionApiResponse<T> = {
  code: number | string
  message: string
  result: T
}

// ===== Mission Request DTO =====

export type CreateMissionRequest = {
  farmId: string
  uavId: string
  gatewayId: string
  name: string
  plannedAt?: string
  nodeIds: string[]
  waypoints: {
    seq: number
    lat: number
    lng: number
    altitudeM?: number
    nodeIds: string[]
  }[]
}

export type UpdateMissionRequest = {
  name: string
  notes?: string
  plannedAt: string
}

export type MissionNodeProgressRequest = {
  nodeId: string
  result: 'SUCCESS' | 'FAILED'
  recordsCount: number
  attempts: number
  collectedAt?: string
}

export type MissionProgressRequest = {
  executionId: string
  sequence: number
  waypointSeq: number
  reachedAt: string
  nodeResults?: MissionNodeProgressRequest[]
  instanceId?: string
  recoveryGeneration?: number
}

export type MissionAckRequest = {
  executionId: string
}

export type MissionRecoveryClaimRequest = {
  executionId: string
  instanceId: string
}

export type MissionRecoveryRenewRequest = {
  executionId: string
  instanceId: string
  recoveryGeneration: number
}

export type MissionHeartbeatRequest = {
  executionId: string
  instanceId?: string
  recoveryGeneration?: number
}

export interface MissionNodeResultRequest {
  nodeId: string
  result: 'SUCCESS' | 'FAILED'
  recordsCount: number
  attempts: number
  collectedAt?: string
  errorCode?: string
  errorMessage?: string
}

export interface MissionResultRequest {
  results: MissionNodeResultRequest[]
  completed: boolean
  executionId: string
  instanceId?: string
  recoveryGeneration?: number
}
