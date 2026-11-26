import axiosClient from '../axiosClient'
import { mockApi } from '../../mockStore'

import type {
  Mission,
  MissionStatus,
  MissionResult,
  Waypoint,
} from '../../types'

import type {
  MissionApiResponse,
  MissionDto,
  MissionDetailDto,
  MissionWaypointDto,
  MissionPageDto,
  MissionNodeResultDto,
  MissionStatusLogDto,
  MissionProgressDto,
  MissionProgressResponseDto,
  MissionRecoveryClaimResponseDto,
  MissionExecutionProgressResponseDto,
  MissionHeartbeatResponseDto,
  CreateMissionRequest,
  UpdateMissionRequest,
  MissionProgressRequest,
  MissionRecoveryClaimRequest,
  MissionRecoveryRenewRequest,
  MissionHeartbeatRequest,
  MissionResultRequest,
} from '../../types/mission'

// Chuyển Waypoint từ backend sang cấu trúc frontend
const toFrontendWaypoints = (
  waypoints?: MissionWaypointDto[]
): Waypoint[] | undefined => {
  return waypoints?.map((wp) => ({
    latitude: wp.lat,
    longitude: wp.lng,
    order: wp.seq,
    sensorNodeId: wp.nodeIds?.[0],
  }))
}

// Chuyển dữ liệu backend sang kiểu Mission của frontend
const toMissionDetail = (m: MissionDetailDto): Mission => ({
  id: m.missionId,
  name: m.name,
  farmId: m.farmId,
  uavId: m.uavId,
  gatewayId: m.gatewayId,
  status: (
    m.status === 'IN_PROGRESS'
      ? 'inProgress'
      : toMissionStatus(m.status)
  ) as Mission['status'],
  scheduledAt: m.plannedAt,
  notes: m.notes,
  waypoints: toFrontendWaypoints(m.waypoints),
  sensorNodeIds: m.nodeIds,
})

const toMissionStatus = (status: string): MissionStatus => {
  switch (status) {
    case 'IN_PROGRESS':
      return 'inProgress'
    case 'PLANNED':
      return 'planned'
    case 'PAUSED':
      return 'paused'
    case 'COMPLETED':
      return 'completed'
    case 'FAILED':
      return 'failed'
    case 'CANCELLED':
      return 'cancelled'
    default:
      return 'planned'
  }
}

export const missionApi = {
  list: async () => {
    const response = await axiosClient.get<
      MissionApiResponse<MissionPageDto>
    >('/v1/missions', {
      params: {
        page: 0,
        size: 10,
      },
    })

    const missions = response.data.result.content

    return missions.map((m) => ({
      id: m.missionId,
      name: m.name,
      farmId: m.farmId,
      uavId: m.uavId,
      gatewayId: m.gatewayId,
      status: toMissionStatus(m.status),
      scheduledAt: m.plannedAt,
      waypoints: toFrontendWaypoints(m.waypoints),
      sensorNodeIds: m.nodeIds,
    }))
  },

  start: async (id: string) => {
    const response = await axiosClient.post<
      MissionApiResponse<MissionDto>
    >(`/v1/missions/${id}/start`)

    const result = response.data.result

    if (!result) {
      throw new Error('Không thể bắt đầu Mission')
    }

    return {
      id: result.missionId,
      name: result.name,
      farmId: result.farmId,
      uavId: result.uavId,
      gatewayId: result.gatewayId,
      status: toMissionStatus(result.status),
      scheduledAt: result.plannedAt,
      waypoints: toFrontendWaypoints(result.waypoints),
      sensorNodeIds: result.nodeIds,
    }
  },

  cancel: async (id: string) => {
    const response = await axiosClient.post<
      MissionApiResponse<MissionDto>
    >(`/v1/missions/${id}/cancel`)

    const result = response.data.result

    if (!result) {
      throw new Error('Không thể hủy Mission')
    }

    return {
      id: result.missionId,
      name: result.name,
      farmId: result.farmId,
      uavId: result.uavId,
      gatewayId: result.gatewayId,
      status: toMissionStatus(result.status),
      scheduledAt: result.plannedAt,
      waypoints: toFrontendWaypoints(result.waypoints),
      sensorNodeIds: result.nodeIds,
    }
  },

  get: async (id: string) => {
    const response = await axiosClient.get<
      MissionApiResponse<MissionDetailDto>
    >(`/v1/missions/${id}`)

    if (!response.data.result) {
      throw new Error('Không tìm thấy thông tin Mission')
    }

    return toMissionDetail(response.data.result)
  },

  create: async (
    body: Partial<Mission> & {
      waypoints?: {
        latitude: number
        longitude: number
        sensorNodeId?: string
      }[]
    }
  ) => {
    const requestBody: CreateMissionRequest = {
      farmId: body.farmId!,
      uavId: body.uavId!,
      gatewayId: body.gatewayId!,
      name: body.name!,
      plannedAt: body.scheduledAt
        ? new Date(body.scheduledAt).toISOString()
        : undefined,
      nodeIds: body.sensorNodeIds || [],
      waypoints: (body.waypoints || []).map((wp, index) => ({
        seq: index + 1,
        lat: wp.latitude,
        lng: wp.longitude,
        nodeIds: wp.sensorNodeId
          ? [wp.sensorNodeId]
          : [],
      })),
    }

    const response = await axiosClient.post<
      MissionApiResponse<MissionDto>
    >('/v1/missions/createMission', requestBody)

    const result = response.data.result

    if (!result) {
      throw new Error('Không tạo được Mission')
    }

    return {
      id: result.missionId,
      name: result.name,
      farmId: result.farmId,
      uavId: result.uavId,
      gatewayId: result.gatewayId,
      status: toMissionStatus(result.status),
      scheduledAt: result.plannedAt,
      waypoints: toFrontendWaypoints(result.waypoints),
      sensorNodeIds: result.nodeIds,
    }
  },

  update: async (id: string, body: Partial<Mission>) => {
    if (!body.name?.trim()) {
      throw new Error('Tên Mission không được để trống')
    }

    if (!body.scheduledAt) {
      throw new Error('Vui lòng chọn thời gian thực hiện')
    }

    const requestBody: UpdateMissionRequest = {
      name: body.name.trim(),
      notes: body.notes,
      plannedAt: new Date(body.scheduledAt).toISOString(),
    }

    const response = await axiosClient.put<
      MissionApiResponse<MissionDto>
    >(`/v1/missions/${id}`, requestBody)

    const result = response.data.result

    if (!result) {
      throw new Error('Không cập nhật được Mission')
    }

    return {
      id: result.missionId,
      name: result.name,
      farmId: result.farmId,
      uavId: result.uavId,
      gatewayId: result.gatewayId,
      status: toMissionStatus(result.status),
      scheduledAt: result.plannedAt,
      waypoints: toFrontendWaypoints(result.waypoints),
      sensorNodeIds: result.nodeIds,
    }
  },

  updateStatus: (id: string, status: string, notes?: string) =>
    mockApi.missions.updateStatus(
      id,
      toMissionStatus(status),
      notes
    ),

  results: async (id: string): Promise<MissionResult[]> => {
    const response = await axiosClient.get<
        MissionApiResponse<MissionNodeResultDto[]>
    >(`/v1/missions/${id}/results`)

    return (response.data.result ?? []).map((item) => ({
        id: item.nodeId,
        missionId: id,
        sensorNodeId: item.nodeId,
        success: item.result === 'SUCCESS',
        message: item.errorMessage ?? undefined,
        collectedAt: item.collectedAt ?? undefined,
    }))
    },

  statusLogs: async (id: string) => {
    const response = await axiosClient.get<
      MissionApiResponse<MissionStatusLogDto[]>
    >(`/v1/missions/${id}/status-logs`)

    return response.data.result ?? []
  },

  progress: async (id: string) => {
    const response = await axiosClient.get<
      MissionApiResponse<MissionProgressDto>
    >(`/v1/missions/${id}/progress`)

    const result = response.data.result

    if (!result) {
      throw new Error('Không lấy được tiến độ Mission')
    }

    return result
  },

  updateProgress: async (
    id: string,
    body: MissionProgressRequest
  ) => {
    const response = await axiosClient.post<
      MissionApiResponse<MissionProgressResponseDto>
    >(`/v1/missions/${id}/progress`, body)

    const result = response.data.result

    if (!result) {
      throw new Error('Không thể cập nhật tiến độ Mission')
    }

    return result
  },

  recoverable: async () => {
    const response = await axiosClient.get<
      MissionApiResponse<unknown>
    >('/v1/missions/recoverable')

    return response.data.result
  },

  recoveryClaim: async (
    missionId: string,
    data: MissionRecoveryClaimRequest
  ) => {
    const response = await axiosClient.post<
      MissionApiResponse<MissionRecoveryClaimResponseDto>
    >(
      `/v1/missions/${missionId}/recovery-claim`,
      data
    )

    return response.data.result
  },

  recoveryRenew: async (
    missionId: string,
    data: MissionRecoveryRenewRequest
  ) => {
    const response = await axiosClient.post<
      MissionApiResponse<MissionRecoveryClaimResponseDto>
    >(
      `/v1/missions/${missionId}/recovery-renew`,
      data
    )

    return response.data.result
  },

  plan: async (missionId: string) => {
    const response = await axiosClient.post<
      MissionApiResponse<MissionDto>
    >(`/v1/missions/${missionId}/plan`)

    return response.data.result
  },

  executionProgress: async (
    missionId: string,
    executionId: string
  ) => {
    const response = await axiosClient.get<
      MissionApiResponse<MissionExecutionProgressResponseDto>
    >(
      `/v1/missions/${missionId}/executions/${executionId}/progress`
    )

    return response.data.result
  },

  heartbeat: async (
    missionId: string,
    data: MissionHeartbeatRequest
  ) => {
    const response = await axiosClient.post<
      MissionApiResponse<MissionHeartbeatResponseDto>
    >(
      `/v1/missions/${missionId}/heartbeat`,
      data
    )

    return response.data.result
  },

  submitResults: async (
    missionId: string,
    data: MissionResultRequest
  ) => {
    const response = await axiosClient.post<
      MissionApiResponse<MissionDto>
    >(
      `/v1/missions/${missionId}/results`,
      data
    )

    return response.data.result
  },
}
