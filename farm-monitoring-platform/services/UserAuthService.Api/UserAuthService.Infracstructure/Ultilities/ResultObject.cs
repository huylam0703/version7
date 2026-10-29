using System;
using System.Collections.Generic;
using System.Text;

using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Abstractions.Enums;

namespace UserAuthService.Infracstructure.Ultilities
{

    /// <summary>
    /// 
    /// </summary>
    /// <typeparam name="T"></typeparam>

    public class ResultObject<T>
    {
        private ResultCode _code;

        /// <summary>
        /// Constructor
        /// </summary>
        public ResultObject()
        {
            this._code = ResultCode.Ok;
            Message = EnumHelper.GetDescription(this._code);
        }

        /// <summary>
        /// Constructor
        /// </summary>
        /// <param name="code"></param>
        /// <param name="message"></param>
        /// <param name="data"></param>
        public ResultObject(ResultCode code, string? message, T data)
        {
            this._code = code;
            this.Message = message;
            this.Data = data;
        }

        /// <summary>
        /// 
        /// </summary>
        public ResultCode Code
        {
            set
            {
                this._code = value;
            }
            get
            {
                return this._code;
            }
        }
        /// <summary>
        /// 
        /// </summary>
        public string? Message { get; set; }

        /// <summary>
        /// 
        /// </summary>
        public T? Data { set; get; }

        /// <summary>
        /// 
        /// </summary>
        public Dictionary<string, string[]>? Errors { get; set; }
    }

    /// <summary>
    /// 
    /// </summary>
    public class NullDataType
    {
    }
}


