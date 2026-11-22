param(
    [string]$ConfigPath = (Join-Path $PSScriptRoot '..\..\.local\mortgage-referrals-config.json')
)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectPath 'build\libs\mortgage-referral-system-0.1.0.jar'
if (-not (Test-Path -LiteralPath $jarPath)) {
    throw 'Primero ejecute npm ci y gradlew.bat bootJar desde la carpeta del proyecto.'
}
if (-not (Test-Path -LiteralPath $ConfigPath)) {
    throw 'Falta la configuracion local. Copie config/local.example.json fuera del repositorio y complete sus credenciales.'
}
$configValues = Get-Content -LiteralPath $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($requiredName in @('DB_URL','DB_USERNAME','DB_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace($configValues.$requiredName)) { throw "Falta configurar $requiredName." }
}
$previousValues = @{}
try {
    foreach ($property in $configValues.PSObject.Properties) {
        $previousValues[$property.Name] = [Environment]::GetEnvironmentVariable($property.Name, 'Process')
        [Environment]::SetEnvironmentVariable($property.Name, [string]$property.Value, 'Process')
    }
    Write-Host 'Aplicacion local: http://127.0.0.1:8080. Use Ctrl+C para detenerla.'
    & java -jar $jarPath
    if ($LASTEXITCODE -ne 0) { throw "Java finalizo con codigo $LASTEXITCODE." }
} finally {
    foreach ($name in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousValues[$name], 'Process')
    }
}
