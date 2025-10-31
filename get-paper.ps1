param(
  [string]$Version = 'latest'
)

$ErrorActionPreference = 'Stop'
$project = 'paper'

if ($Version -eq 'latest') {
  $projInfo = Invoke-RestMethod -UseBasicParsing "https://api.papermc.io/v2/projects/$project"
  if (-not $projInfo.versions) { throw "Failed to get versions list from Paper API" }
  # Pick the highest version string from the list
  $Version = ($projInfo.versions | Sort-Object { $_ -as [version] } | Select-Object -Last 1)
}

$verInfo = Invoke-RestMethod -UseBasicParsing "https://api.papermc.io/v2/projects/$project/versions/$Version"
if (-not $verInfo.builds) { throw "No builds found for Paper $Version" }
$build = ($verInfo.builds | Sort-Object -Descending | Select-Object -First 1)
$file = "paper-$Version-$build.jar"
$url = "https://api.papermc.io/v2/projects/$project/versions/$Version/builds/$build/downloads/$file"
Write-Host "Downloading $url"
Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile 'server.jar'
Write-Host "Saved to server.jar (Paper $Version build $build)"
