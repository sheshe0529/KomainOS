# Revisar consumo de memoria (KomainOS · mantenimiento de diagnóstico, sin cambios en el servidor).
# Parámetro que KomainOS define antes de este código:
#   $TOP_PROCESOS  cantidad de procesos con más memoria que se informan
$ErrorActionPreference = 'Stop'

$sistema = Get-CimInstance -ClassName Win32_OperatingSystem
$totalMb = [math]::Round($sistema.TotalVisibleMemorySize / 1KB)
$libreMb = [math]::Round($sistema.FreePhysicalMemory / 1KB)
Write-Output "Memoria de $($env:COMPUTERNAME): $libreMb MB libres de $totalMb MB"
Write-Output ""
Write-Output "Los $TOP_PROCESOS procesos con más memoria:"
Get-Process | Sort-Object -Property WorkingSet64 -Descending | Select-Object -First $TOP_PROCESOS |
    Format-Table -Property Id, ProcessName, @{ Name = 'MB'; Expression = { [math]::Round($_.WorkingSet64 / 1MB) } } -AutoSize |
    Out-String -Width 200
