$ErrorActionPreference = 'Stop'
$JAVA = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot\bin\java.exe'
$JAR = 'server.jar'
$MinMem = '2G'
$MaxMem = '4G'
if (!(Test-Path $JAVA)) { Write-Error "Java not found at $JAVA"; exit 1 }
if (!(Test-Path $JAR)) { Write-Error "Missing $JAR in $(Get-Location)"; exit 1 }
## Aikar's recommended flags (G1GC tuning for modern MC)
$Flags = @(
  '-XX:+UseG1GC',
  '-XX:MaxGCPauseMillis=200',
  '-XX:+UnlockExperimentalVMOptions',
  '-XX:+DisableExplicitGC',
  '-XX:+AlwaysPreTouch',
  '-XX:G1NewSizePercent=30',
  '-XX:G1MaxNewSizePercent=40',
  '-XX:G1HeapRegionSize=8M',
  '-XX:G1ReservePercent=20',
  '-XX:InitiatingHeapOccupancyPercent=15',
  '-XX:G1MixedGCLiveThresholdPercent=90',
  '-XX:SurvivorRatio=32',
  '-XX:+PerfDisableSharedMem',
  '-XX:MaxTenuringThreshold=1'
)
$MemFlags = @("-Xms$MinMem", "-Xmx$MaxMem")
& $JAVA @Flags @MemFlags -jar $JAR nogui
