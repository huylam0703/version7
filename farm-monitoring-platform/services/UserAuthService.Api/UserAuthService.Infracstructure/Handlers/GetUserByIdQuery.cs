using System;
using System.Collections.Generic;
using System.Text;
using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;
using FluentValidation;
using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Dtos.Mapper;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Queries.Users;
    public class GetUserByIdQueryHanle : IQueryHandler<GetUserByIdQuery, GetUserDto> {
        private readonly IUserService _userService;

        public GetUserByIdQueryHanle(IUserService userService)
        {
            _userService = userService;
        }
        public async Task<Result<GetUserDto>> Handle(GetUserByIdQuery request, CancellationToken cancellationToken)
        {
            var user = await _userService.GetByIdAsync(request.UserId);
            if (user is null)
                return Error.NotFound(UserAuthErrors.UserNotFound, "Không tìm thấy người dùng.");

            return UserMapper.ToDto(user);
        }
    }
