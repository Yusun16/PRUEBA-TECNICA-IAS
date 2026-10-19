param([ValidateRange(1024,65535)][int]$Port = 18082, [string]$JarPath)
$ErrorActionPreference = 'Stop'
$backRoot = Split-Path -Parent $PSScriptRoot
$jarPath = if ($JarPath) { (Resolve-Path -LiteralPath $JarPath).Path } else { Join-Path $backRoot 'target\support-reconciliation-1.0.0-audit.jar' }
$logRoot = Join-Path $backRoot '.tools'
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Build first with -Dartifact.suffix=-audit verify.' }
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
if (-not $env:JAVA_HOME) {
    $portableJdk = Get-ChildItem -LiteralPath $logRoot -Directory -Filter 'jdk-*' | Select-Object -First 1
    if ($portableJdk) { $env:JAVA_HOME = $portableJdk.FullName }
}
$javaPath = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
$client = New-Object Net.Sockets.TcpClient
try { $client.Connect('127.0.0.1',$Port); throw "Port $Port is already in use." }
catch [Net.Sockets.SocketException] { } finally { $client.Dispose() }
$testUser = if ($env:SUPPORT_USER) { $env:SUPPORT_USER } else { 'n3' }
$testPassword = if ($env:SUPPORT_PASSWORD) { $env:SUPPORT_PASSWORD } else { 'demo-change-me' }
$authorization = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($testUser+':'+$testPassword))
$headers = @{Authorization=('Basic '+$authorization)}
$baseUrl = "http://127.0.0.1:$Port"
$observations = New-Object Collections.Generic.List[object]
$ownedServer = $null

function Start-AuditServer([bool]$AllowCompensation) {
    $policy = if ($AllowCompensation) { 'true' } else { 'false' }
    $script:ownedServer = Start-Process -FilePath $javaPath -WorkingDirectory $backRoot -WindowStyle Hidden -PassThru -ArgumentList @(
        '-jar',('"'+$jarPath+'"'),'--spring.profiles.active=local',"--server.port=$Port",'--server.address=127.0.0.1',
        '--debug=false',('--reconciliation.allow-duplicate-compensation='+$policy)) -RedirectStandardOutput (Join-Path $logRoot 'audit-smoke-server.log') -RedirectStandardError (Join-Path $logRoot 'audit-smoke-server-error.log')
    for ($attempt=0; $attempt -lt 80; $attempt++) {
        if ($script:ownedServer.HasExited) { throw 'Isolated audit server exited unexpectedly.' }
        try {
            $response = Invoke-WebRequest -Uri ($baseUrl+'/api/support/session') -Headers $headers -UseBasicParsing
            if ($response.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Milliseconds 250
    }
    throw 'Isolated audit server did not become ready.'
}
function Assert-Case([string]$Reference,[string]$Outcome,[int]$Movements,[bool]$Audit) {
    $response = Invoke-WebRequest -Uri ($baseUrl+'/api/support/payments/'+$Reference+'/reconcile') -Method Post -Headers $headers -UseBasicParsing
    $result = $response.Content | ConvertFrom-Json
    if ($result.outcome -ne $Outcome -or $result.movementsCreated -ne $Movements -or $result.auditCreated -ne $Audit) {
        throw ('Unexpected reconciliation result for '+$Reference)
    }
    $observations.Add($result)
    return $result
}
try {
    Start-AuditServer $true
    $null = Assert-Case 'PAY-AX91' 'RESOLVED' 0 $true
    $duplicate = Assert-Case 'PAY-BX17' 'RESOLVED' 1 $true
    if ($duplicate.netDebitBefore -ne 178000 -or $duplicate.netDebitAfter -ne 89000) { throw 'Incorrect duplicate compensation.' }
    $null = Assert-Case 'PAY-BX17' 'ALREADY_CONSISTENT' 0 $false
    $null = Assert-Case 'PAY-CQ22' 'MANUAL_REVIEW' 0 $true
    $null = Assert-Case 'PAY-CQ22' 'MANUAL_REVIEW' 0 $false
    $null = Assert-Case 'PAY-DK44' 'ALREADY_CONSISTENT' 0 $false
    Stop-Process -Id $ownedServer.Id
    $ownedServer.WaitForExit()
    $ownedServer = $null
    Start-AuditServer $false
    $safe = Assert-Case 'PAY-BX17' 'MANUAL_REVIEW' 0 $true
    if ($safe.reason -ne 'DUPLICATE_DEBITS_REQUIRE_REVIEW' -or $safe.netDebitBefore -ne 178000 -or $safe.netDebitAfter -ne 178000) {
        throw 'Safe policy or H2 restart initialization failed.'
    }
    $null = Assert-Case 'PAY-BX17' 'MANUAL_REVIEW' 0 $false
    $evidence = @{generatedAtUtc=[DateTime]::UtcNow.ToString('o');artifactSha256=(Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash;h2RestartRestoredFixtures=$true;cases=$observations}
    $evidence | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $logRoot 'audit-http-results.json') -Encoding UTF8
    Write-Host 'PASS: 8 HTTP commands, all four samples, retries, safe policy and H2 restart; active user application untouched.'
} finally {
    if ($ownedServer -and -not $ownedServer.HasExited) { Stop-Process -Id $ownedServer.Id }
}
