using System;
using System.Collections.Generic;
using System.Data;
using System.Text;

using Dapper;

using Microsoft.Data.SqlClient;

namespace UserAuthService.Infracstructure.Database;

public class SqlServer : IQuery
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
    public async Task<IEnumerable<T>> QueryAsync<T>(string connectionString, string sql, object? param = null, int commandTimeout = 20)
    {
        try
        {
            using (SqlConnection conn = new SqlConnection(connectionString))
            {
                conn.Open();
                var result = await conn.QueryAsync<T>(sql, param, commandType: CommandType.Text, commandTimeout: commandTimeout);
                return result.ToList();
            }
        }
        catch
        {
            throw;
        }
    }

    /// <summary>
    /// 
    /// </summary>
    /// <typeparam name="T"></typeparam>
    /// <param name="connectionString"></param>
    /// <param name="sql"></param>
    /// <param name="param"></param>
    /// <param name="commandTimeout"></param>
    /// <returns></returns>
    public IEnumerable<T> Query<T>(string connectionString, string sql, object? param = null, int commandTimeout = 20)
    {
        try
        {
            using (SqlConnection conn = new SqlConnection(connectionString))
            {
                conn.Open();
                var result = conn.Query<T>(sql, param, commandType: CommandType.Text, commandTimeout: commandTimeout);
                return result.ToList();
            }
        }
        catch
        {
            throw;
        }
    }

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
    public async Task<(IEnumerable<T1>, IEnumerable<T2>)> QueryMultipleAsync<T1, T2>(string connectionString, string sql, object? param = null, int commandTimeout = 20)
    {
        try
        {
            using (SqlConnection conn = new SqlConnection(connectionString))
            {
                conn.Open();
                var result = await conn.QueryMultipleAsync(sql, param, commandType: CommandType.Text, commandTimeout: commandTimeout);
                var item1 = await result.ReadAsync<T1>();
                var item2 = await result.ReadAsync<T2>();
                return (item1, item2);
            }
        }
        catch
        {
            throw;
        }
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="connectionString"></param>
    /// <param name="sql"></param>
    /// <param name="param"></param>
    /// <param name="commandTimeout"></param>
    /// <returns></returns>
    public async Task<int> ExecuteAsync(string connectionString, string sql, object? param = null, int commandTimeout = 20)
    {
        try
        {
            using (SqlConnection conn = new SqlConnection(connectionString))
            {
                conn.Open();
                return await conn.ExecuteAsync(sql, param, commandType: CommandType.Text, commandTimeout: commandTimeout);
            }
        }
        catch
        {
            throw;
        }
    }
}
