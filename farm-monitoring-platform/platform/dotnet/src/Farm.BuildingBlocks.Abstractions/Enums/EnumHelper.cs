using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Reflection;
using System.Text;

namespace Farm.BuildingBlocks.Abstractions.Enums
{
    public static class EnumHelper
    {
        public static string GetDescription(this object value)
        {
            FieldInfo field = value!.GetType()!.GetField(value!.ToString()!)!;
            DescriptionAttribute attribute = (DescriptionAttribute)Attribute.GetCustomAttribute(field!, typeof(DescriptionAttribute))!;

            return attribute == null ? value!.ToString()! : attribute!.Description;
        }
    }
}
