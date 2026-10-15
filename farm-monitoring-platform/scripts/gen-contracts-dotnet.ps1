$ErrorActionPreference = "Stop"
Set-Location (Resolve-Path "$PSScriptRoot\..")

dotnet tool restore

$out = "platform/dotnet/src/Farm.Contracts/Generated"
if (Test-Path $out) { Remove-Item $out -Recurse -Force }   # tránh file cũ còn sót khi đổi tên/xóa event
New-Item -ItemType Directory -Force $out | Out-Null

Get-ChildItem contracts/events -Filter "*.yaml" | ForEach-Object {
    $file = $_
    if ($file.BaseName -match '\.v(\d+)$') { $ver = $Matches[1] }
    else { throw "Tên file phải dạng <event>.v<N>.yaml: $($file.Name)" }

    # readings.synced.v1 -> ReadingsSynced
    $base = $file.BaseName -replace '\.v\d+$', ''
    $cls = ($base -split '[._]' | ForEach-Object { $_.Substring(0,1).ToUpper() + $_.Substring(1) }) -join ''

    Write-Host "Generating $($file.Name)"
    dotnet nswag openapi2csclient `
        /input:"contracts/events/$($file.Name)" `
        /output:"$out/$cls.V$ver.g.cs" `
        /namespace:"Farm.Contracts.Events.V$ver" `
        /generateClientClasses:false `
        /generateClientInterfaces:false `
        /generateExceptionClasses:false `
        /generateDtoTypes:true `
        /jsonLibrary:SystemTextJson `
        /generateNullableReferenceTypes:true `
        /dateTimeType:System.DateTimeOffset
    if ($LASTEXITCODE -ne 0) { throw "NSwag lỗi ở $($file.Name)" }
}

Write-Host "Done -> $out"