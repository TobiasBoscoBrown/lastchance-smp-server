$ErrorActionPreference = 'Stop'
$JAVAC = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot\bin\javac.exe'
$JAR = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot\bin\jar.exe'
$ServerJar = 'C:\mc\Server\server.jar'
$LibRoot = 'C:\mc\Server\libraries'
$Src = Join-Path $PSScriptRoot 'src\main\java'
$Res = Join-Path $PSScriptRoot 'src\main\resources'
$BuildDir = Join-Path $PSScriptRoot 'build\classes'
$OutJar = Join-Path $PSScriptRoot 'build\KickOnDeath.jar'
$PluginsDir = 'C:\mc\Server\plugins'

if (!(Test-Path $ServerJar)) { throw "Cannot find $ServerJar. Make sure Paper server.jar exists." }
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null

$files = Get-ChildItem -Path $Src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
if (-not $files -or $files.Count -eq 0) { throw "No Java sources found under $Src" }
$libJars = @()
if (Test-Path $LibRoot) {
  $libJars = Get-ChildItem -Path $LibRoot -Recurse -Filter '*.jar' | ForEach-Object { $_.FullName }
}
$cpParts = @($ServerJar) + $libJars
$cp = ($cpParts | ForEach-Object { '"' + $_ + '"' }) -join ';'
& $JAVAC -encoding UTF-8 --release 21 -cp $cp -d $BuildDir $files

if (Test-Path $OutJar) { Remove-Item $OutJar -Force }
& $JAR --create --file $OutJar -C $BuildDir . -C $Res plugin.yml -C $Res config.yml

New-Item -ItemType Directory -Force -Path $PluginsDir | Out-Null
Copy-Item $OutJar (Join-Path $PluginsDir 'KickOnDeath.jar') -Force
Write-Host "Built and copied plugin to $PluginsDir\KickOnDeath.jar"
