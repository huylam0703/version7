ARG DOTNET_VERSION=10.0

FROM mcr.microsoft.com/dotnet/sdk:${DOTNET_VERSION} AS build
ARG PROJECT_PATH
WORKDIR /repo
COPY . .
RUN dotnet publish "${PROJECT_PATH}" -c Release -o /out

FROM mcr.microsoft.com/dotnet/aspnet:${DOTNET_VERSION}
ARG DLL_NAME
ENV DLL_NAME=${DLL_NAME} ASPNETCORE_URLS=http://+:8080
WORKDIR /app
COPY --from=build /out .
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "dotnet ${DLL_NAME}"]