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

    public class GetUserByIdQueryValidator : AbstractValidator<GetUserByIdQuery>
    {
        public GetUserByIdQueryValidator(){
            RuleFor(x => x.UserId).NotEmpty().WithMessage("Thông tin người dùng tìm kiếm không hợp lệ.");
        }
    }
