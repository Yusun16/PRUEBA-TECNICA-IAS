param([ValidateSet('verify','run')][string]$Task = 'verify')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    if (-not $env:JAVA_HOME) {
        $portableJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools') -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($portableJdk) { $env:JAVA_HOME = $portableJdk.FullName }
    }
    if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw 'Se requiere JDK 21: configure JAVA_HOME o agregue java al PATH.'
    }
    $env:MAVEN_USER_HOME = Join-Path $projectRoot '.tools\maven-home'
    if ($Task -eq 'verify') {
        & .\mvnw.cmd '-Dmaven.repo.local=.m2' clean verify
    } else {
        & .\mvnw.cmd '-Dmaven.repo.local=.m2' spring-boot:run
    }
    $result = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $result
