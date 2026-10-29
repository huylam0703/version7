using System;
using System.Collections.Generic;
using System.Text;

namespace UserAuthService.Infracstructure.Database;

public interface IQuery
{
    /// <summary>
    /// 
    /// </summary>
    /// <typeparam name="T"></typeparam>
    /// <param name="connectionString"></param>
    /// <param name="sql"></param>
    /// <param name="param"></param>
    /// <param name="commandTimeout"></param>
    /// <returns></returns>
    Task<IEnumerable<T>> QueryAsync<T>(string connectionString, string sql, object? param = null, int commandTimeout = 20);

    /// <summary>
    /// 
    /// </summary>
    /// <typeparam name="T"></typeparam>
    /// <param name="connectionString"></param>
    /// <param name="sql"></param>
    /// <param name="param"></param>
    /// <param name="commandTimeout"></param>
    /// <returns></returns>
    IEnumerable<T> Query<T>(string connectionString, string sql, object? param = null, int commandTimeout = 20);

    /// <summary>
    /// 
    /// </summary>
    /// <typeparam name="T1"></typeparam>
    /// <typeparam name="T2"></typeparam>
    /// <param name="connectionString"></param>
    /// <param name="sql"></param>
    /// <param name="param"></param>
    /// <param name="commandTimeout"></param>
    /// <returns></returns>
    Task<(IEnumerable<T1>, IEnumerable<T2>)> QueryMultipleAsync<T1, T2>(string connectionString, string sql, object? param = null, int commandTimeout = 20);

    /// <summary>
    /// 
    /// </summary>
    /// <param name="connectionString"></param>
    /// <param name="sql"></param>
    /// <param name="param"></param>
    /// <param name="commandTimeout"></param>
    /// <returns></returns>
    Task<int> ExecuteAsync(string connectionString, string sql, object? param = null, int commandTimeout = 20);
}
