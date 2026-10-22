using System;
using System.Collections.Generic;
using System.Text;

using ApiGatewayService.Application.Health;

namespace ApiGatewayService.Application.Abstractions
{
    public interface IServiceHealthChecker
    {
        Task<IReadOnlyList<ServiceHealth>> CheckAllAsync(CancellationToken ct);
    }
}
