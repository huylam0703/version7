using System;
using System.Collections.Generic;
using System.Text;
using Farm.BuildingBlocks.Application;

using Microsoft.Extensions.DependencyInjection;

using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application;

public static class DependencyInjectionExtension
{
    public static IServiceCollection AddApplicationService(this IServiceCollection services)
        => services.AddFarmApplication(typeof(DependencyInjectionExtension).Assembly);
}
