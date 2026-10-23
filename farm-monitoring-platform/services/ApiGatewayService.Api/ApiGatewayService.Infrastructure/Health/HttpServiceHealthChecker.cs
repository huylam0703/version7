using System.Diagnostics;

using ApiGatewayService.Application.Abstractions;
using ApiGatewayService.Application.Health;

using Microsoft.Extensions.Configuration;

namespace ApiGatewayService.Infrastructure.Health;

public sealed class HttpServiceHealthChecker : IServiceHealthChecker
{
    /// <summary>
    /// 
    /// </summary>
    private readonly IHttpClientFactory _factory;

    /// <summary>
    /// 
    /// </summary>
    private readonly IConfiguration _config;

    /// <summary>
    /// 
    /// </summary>
    /// <param name="factory"></param>
    /// <param name="config"></param>
    public HttpServiceHealthChecker(IHttpClientFactory factory, IConfiguration config)
    {
        _factory = factory;
        _config = config;
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<IReadOnlyList<ServiceHealth>> CheckAllAsync(CancellationToken ct)
    {
        var targets = _config.GetSection("ReverseProxy:Clusters").GetChildren()
            .Select(c => (Name: c.Key, Address: c.GetSection("Destinations").GetChildren().FirstOrDefault()?["Address"]))
            .Where(x => !string.IsNullOrWhiteSpace(x.Address))
            .ToList();

        var results = await Task.WhenAll(targets.Select(t => CheckOneAsync(t.Name, t.Address!, ct)));
        return results;
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="name"></param>
    /// <param name="address"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    private async Task<ServiceHealth> CheckOneAsync(string name, string address, CancellationToken ct)
    {
        var client = _factory.CreateClient("health");
        var url = new Uri(new Uri(address), "health/ready");
        var sw = Stopwatch.StartNew();
        try
        {
            using var response = await client.GetAsync(url, ct);
            return new ServiceHealth(name, response.IsSuccessStatusCode ? "UP" : "DOWN", (int)response.StatusCode, sw.ElapsedMilliseconds);
        }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException && !ct.IsCancellationRequested)
        {
            return new ServiceHealth(name, "DOWN", null, sw.ElapsedMilliseconds);
        }
    }
}