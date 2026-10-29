using System;
using System.Collections.Generic;
using System.Text;

namespace UserAuthService.Domain.Entity;

public class Role
{
    /// <summary>
    /// 
    /// </summary>
    public Guid RoleId { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string Name { get; set; } = "";
    /// <summary>
    /// 
    /// </summary>
    public string? Description { get; set; }
}
