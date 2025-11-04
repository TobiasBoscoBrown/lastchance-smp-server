param(
  [ValidateSet('All','DeathsReporter','SessionsReporter','DownedGate','KickOnDeath','KillReporter','RandomSpawn')]
  [string]$Plugin = 'All'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Build-One($name) {
  $path = Join-Path $root $name
  $script = Join-Path $path 'build.ps1'
  if (!(Test-Path $script)) { throw "Build script not found: $script" }
  Write-Host "Building $name ..."
  & powershell -NoProfile -ExecutionPolicy Bypass -File $script
}

switch ($Plugin) {
  'All' { Build-One 'DeathsReporter'; Build-One 'SessionsReporter'; Build-One 'DownedGate'; Build-One 'KickOnDeath'; Build-One 'KillReporter'; Build-One 'RandomSpawn' }
  'DeathsReporter' { Build-One 'DeathsReporter' }
  'SessionsReporter' { Build-One 'SessionsReporter' }
  'DownedGate' { Build-One 'DownedGate' }
  'KickOnDeath' { Build-One 'KickOnDeath' }
  'KillReporter' { Build-One 'KillReporter' }
  'RandomSpawn' { Build-One 'RandomSpawn' }
}

Write-Host "Done. Check c:\mc\Server\plugins for installed jars."
