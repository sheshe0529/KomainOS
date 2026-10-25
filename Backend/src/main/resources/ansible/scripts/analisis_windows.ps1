$ErrorActionPreference = 'Stop'
$os = Get-CimInstance Win32_OperatingSystem
$cpu = (Get-CimInstance Win32_Processor | Measure-Object LoadPercentage -Average).Average
$disk = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'"
@{metricas=@(
 @{codigo='cpu_uso_pct';valor=[math]::Round($cpu,2)},
 @{codigo='memoria_uso_pct';valor=[math]::Round((1-$os.FreePhysicalMemory/$os.TotalVisibleMemorySize)*100,2)},
 @{codigo='disco_uso_pct';instancia='sistema';valor=[math]::Round((1-$disk.FreeSpace/$disk.Size)*100,2)},
 @{codigo='carga_promedio';valor=0}, @{codigo='uso_swap';valor=0},
 @{codigo='puertos_escucha';elementos=@(Get-NetTCPConnection -State Listen | Select-Object -ExpandProperty LocalPort -Unique)},
 @{codigo='servicios_ejecucion';elementos=@(Get-Service | Where-Object Status -eq Running | Select-Object -ExpandProperty Name)},
 @{codigo='usuarios_locales';elementos=@(Get-LocalUser | Select-Object -ExpandProperty Name)}
)} | ConvertTo-Json -Depth 6 -Compress
