# Análisis básico de salud para Windows (KomainOS · contrato JSON de métricas).
# Solo lectura: no modifica el servidor. La última línea es el objeto {"metricas": [...]}.
# Ejemplo provisional: ajuste las métricas a las que defina el catálogo de su organización.
$ErrorActionPreference = 'Stop'

$sistema = Get-CimInstance -ClassName Win32_OperatingSystem
$cpu = (Get-CimInstance -ClassName Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average
$unidad = Get-CimInstance -ClassName Win32_LogicalDisk -Filter "DeviceID='$env:SystemDrive'"
$paginacion = @(Get-CimInstance -ClassName Win32_PageFileUsage -ErrorAction SilentlyContinue)
$asignado = ($paginacion | Measure-Object -Property AllocatedBaseSize -Sum).Sum
$enUso = ($paginacion | Measure-Object -Property CurrentUsage -Sum).Sum
$cola = (Get-CimInstance -ClassName Win32_PerfFormattedData_PerfOS_System).ProcessorQueueLength

function Porcentaje($parte, $total) {
    if (-not $total) { return 0 }
    return [math]::Round(($parte / $total) * 100, 2)
}

$metricas = @(
    @{ codigo = 'cpu_uso_pct'; valor = [math]::Round([double]$cpu, 2) },
    @{ codigo = 'memoria_uso_pct'; valor = Porcentaje ($sistema.TotalVisibleMemorySize - $sistema.FreePhysicalMemory) $sistema.TotalVisibleMemorySize },
    @{ codigo = 'disco_uso_pct'; instancia = $env:SystemDrive; valor = Porcentaje ($unidad.Size - $unidad.FreeSpace) $unidad.Size },
    @{ codigo = 'uso_swap'; valor = Porcentaje $enUso $asignado },
    @{ codigo = 'carga_promedio'; valor = [double]$cola },
    @{ codigo = 'puertos_escucha'; elementos = @(Get-NetTCPConnection -State Listen | Select-Object -ExpandProperty LocalPort -Unique | Sort-Object) },
    @{ codigo = 'servicios_ejecucion'; elementos = @(Get-Service | Where-Object Status -eq 'Running' | Select-Object -ExpandProperty Name) },
    @{ codigo = 'usuarios_locales'; elementos = @(Get-CimInstance -ClassName Win32_UserAccount -Filter 'LocalAccount=True AND Disabled=False' | Select-Object -ExpandProperty Name) }
)

Write-Output "Análisis de $env:COMPUTERNAME completado"
@{ metricas = $metricas } | ConvertTo-Json -Depth 5 -Compress
