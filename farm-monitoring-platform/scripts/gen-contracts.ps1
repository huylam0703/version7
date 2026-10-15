$ErrorActionPreference = "Stop"
$out = "platform/dotnet/src/Farm.Contracts"

Get-ChildItem contracts/events -Filter *.v1.yaml | ForEach-Object {
    Write-Host "Generating $($_.Name)"
    openapi-generator-cli generate -g csharp `
        -i "contracts/events/$($_.Name)" `
        -o $out `
        --global-property "models,supportingFiles=false" `
        --additional-properties "packageName=Farm.Contracts,useDateTimeOffset=true"
}