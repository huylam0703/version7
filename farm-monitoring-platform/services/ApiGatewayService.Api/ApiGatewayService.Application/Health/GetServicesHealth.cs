using ApiGatewayService.Application.Abstractions;

using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

namespace ApiGatewayService.Application.Health;

public sealed record ServiceHealth(string Name, string Status, int? HttpStatus, long ElapsedMs);

public sealed record ServicesHealthDto(string Status, IReadOnlyList<ServiceHealth> Services);

public class GetServicesHealthQuery : IQuery<ServicesHealthDto> { }
