param([switch]$Build, [switch]$Check)
$ErrorActionPreference = 'Stop'
$frontRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $frontRoot
$backRoot = Join-Path $workspaceRoot 'back'
$logRoot = Join-Path $frontRoot '.tools'
$jarPath = Join-Path $backRoot 'target\support-reconciliation-1.0.0.jar'

function Test-LocalPort([int]$Port) {
    $client = New-Object Net.Sockets.TcpClient
    try { $client.Connect('127.0.0.1', $Port); return $true } catch { return $false } finally { $client.Dispose() }
}

if ((Test-LocalPort 8080) -or (Test-LocalPort 5173)) {
    throw 'Los puertos 8080 y 5173 deben estar libres. Detenga las instancias anteriores o inicie cada componente por separado.'
}
$nodePath = (Get-Command node -ErrorAction Stop).Source
if (-not $env:JAVA_HOME) {
    $portableJdk = Get-ChildItem -LiteralPath (Join-Path $backRoot '.tools') -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($portableJdk) { $env:JAVA_HOME = $portableJdk.FullName }
}
$javaPath = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
if ($Build -or -not (Test-Path -LiteralPath $jarPath)) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $backRoot 'scripts\dev.ps1') verify
    if ($LASTEXITCODE -ne 0) { throw 'La compilacion del backend fallo. Revise el resultado anterior.' }
}
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
$backendProcess = $null
$frontendProcess = $null
$oldBackendUrl = $env:BACKEND_URL
$oldPort = $env:PORT
try {
    $backendProcess = Start-Process -FilePath $javaPath -ArgumentList @('-jar', ('"' + $jarPath + '"'), '--debug=false', '--spring.profiles.active=local', '--server.port=8080', '--server.address=127.0.0.1') -WorkingDirectory $backRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logRoot 'backend.log') -RedirectStandardError (Join-Path $logRoot 'backend-error.log')
    $env:BACKEND_URL = 'http://127.0.0.1:8080'
    $env:PORT = '5173'
    $frontendProcess = Start-Process -FilePath $nodePath -ArgumentList 'server.mjs' -WorkingDirectory $frontRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logRoot 'frontend.log') -RedirectStandardError (Join-Path $logRoot 'frontend-error.log')
    $ready = $false
    for ($attempt = 0; $attempt -lt 100; $attempt++) {
        if ($backendProcess.HasExited -or $frontendProcess.HasExited) { throw "Un servicio termino. Revise los logs en $logRoot" }
        if ((Test-LocalPort 8080) -and (Test-LocalPort 5173)) { $ready = $true; break }
        Start-Sleep -Milliseconds 300
    }
    if (-not $ready) { throw "Los servicios no iniciaron a tiempo. Revise los logs en $logRoot" }
    if ($Check) {
        $page = Invoke-WebRequest -Uri 'http://127.0.0.1:5173' -UseBasicParsing
        if ($page.StatusCode -ne 200) { throw 'El frontend no respondio correctamente.' }
        $unauthorizedStatus = 0
        try {
            Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/support/payments/PAY-BX17/reconcile' -Method Post -UseBasicParsing | Out-Null
        } catch {
            if ($_.Exception.Response) { $unauthorizedStatus = [int]$_.Exception.Response.StatusCode }
        }
        if ($unauthorizedStatus -ne 401) { throw 'El backend no rechazo la solicitud sin credenciales como se esperaba.' }
        Write-Host 'PASS: frontend HTTP 200, backend HTTP 401 sin credenciales; servicios cerrados al finalizar.'
        return
    }
    Write-Host ''
    Write-Host 'PagoClaro listo: http://127.0.0.1:5173' -ForegroundColor Green
    Write-Host 'Abra Configurar acceso > Usar demo > Guardar acceso (perfil local predeterminado).'
    Write-Host 'Ctrl+C detiene los dos servicios. Logs en front\.tools.'
    while (-not $frontendProcess.HasExited -and -not $backendProcess.HasExited) { Start-Sleep -Seconds 1 }
} finally {
    foreach ($ownedProcess in @($frontendProcess, $backendProcess)) {
        if ($ownedProcess -and -not $ownedProcess.HasExited) { Stop-Process -Id $ownedProcess.Id -ErrorAction SilentlyContinue }
    }
    $env:BACKEND_URL = $oldBackendUrl
    $env:PORT = $oldPort
}
