# Análisis de salud del servidor Windows (KomainOS · variables del R3.1 · contrato JSON de métricas).
# Solo lectura: no modifica el servidor. Informa las 17 variables del R3.1 que se miden en Windows y la última línea es
# el objeto {"metricas": [...]}. Necesita una cuenta administradora para leer el registro de seguridad.
# Usa clases CIM y no rutas de contadores, que cambian con el idioma del sistema. Compatible con PowerShell 5.1.
#
# Parámetros (KomainOS los define antes de este código; si se ejecuta a mano, valen los de abajo):
#   $MUESTREO_SEGUNDOS          ventana en la que se miden CPU, discos, paginación y red
#   $VENTANA_HORAS              periodo de los registros: autenticaciones fallidas, errores de disco y agotamiento de memoria
#   $EXCLUIR_PUERTOS_DINAMICOS  $true: no informa los puertos del rango dinámico, que Windows asigna a los servicios RPC
$ErrorActionPreference = 'Stop'
if ($null -eq $MUESTREO_SEGUNDOS -or $MUESTREO_SEGUNDOS -lt 1) { $MUESTREO_SEGUNDOS = 5 }
if ($null -eq $VENTANA_HORAS) { $VENTANA_HORAS = 24 }
if ($null -eq $EXCLUIR_PUERTOS_DINAMICOS) { $EXCLUIR_PUERTOS_DINAMICOS = $true }

$metricas = New-Object System.Collections.Generic.List[object]

function Valor($codigo, $valor, $instancia) {
    $metrica = [ordered]@{ codigo = $codigo }
    if ($instancia) { $metrica.instancia = [string]$instancia }
    $metrica.valor = $valor
    $metricas.Add($metrica)
}

function Elementos($codigo, $elementos) {
    $lista = @($elementos | Where-Object { $_ } | ForEach-Object { [string]$_ } | Sort-Object -Unique)
    $metricas.Add([ordered]@{ codigo = $codigo; elementos = $lista })
}

function Porcentaje($parte, $total) {
    if (-not $total) { return 0 }
    return [math]::Round(([double]$parte / [double]$total) * 100, 2)
}

# Eventos del periodo; ninguno no es un error. Sin permiso para leer el registro, el análisis falla con el motivo
function ContarEventos($filtro) {
    $filtro.StartTime = (Get-Date).AddHours(-$VENTANA_HORAS)
    try {
        return @(Get-WinEvent -FilterHashtable $filtro -ErrorAction Stop).Count
    } catch {
        if ($_.FullyQualifiedErrorId -like 'NoMatching*') { return 0 }
        throw
    }
}

# ---- Muestreo: CPU, discos, paginación y red en dos lecturas separadas por $MUESTREO_SEGUNDOS ----

function LeerMuestra {
    @{
        Cpu     = Get-CimInstance -ClassName Win32_PerfRawData_PerfOS_Processor -Filter "Name='_Total'"
        Memoria = Get-CimInstance -ClassName Win32_PerfRawData_PerfOS_Memory
        Discos  = @(Get-CimInstance -ClassName Win32_PerfRawData_PerfDisk_PhysicalDisk | Where-Object Name -ne '_Total')
        Red     = @(Get-NetAdapter -Physical | Where-Object Status -eq 'Up' | Get-NetAdapterStatistics)
    }
}

$antes = LeerMuestra
Start-Sleep -Seconds $MUESTREO_SEGUNDOS
$despues = LeerMuestra

# V01: el contador de procesador mide el tiempo ocioso en unidades de 100 ns; el uso es su complemento
$ocioso = [double]$despues.Cpu.PercentProcessorTime - [double]$antes.Cpu.PercentProcessorTime
$transcurrido = [double]$despues.Cpu.Timestamp_Sys100NS - [double]$antes.Cpu.Timestamp_Sys100NS
Valor 'cpu_uso' ([math]::Max(0, [math]::Round(100 - (Porcentaje $ocioso $transcurrido), 2)))

# V04 y V09: operaciones por segundo y milisegundos promedio por operación, por disco físico
foreach ($disco in $despues.Discos) {
    $previo = $antes.Discos | Where-Object Name -eq $disco.Name
    if (-not $previo) { continue }
    $segundos = ([double]$disco.Timestamp_PerfTime - [double]$previo.Timestamp_PerfTime) / [double]$disco.Frequency_PerfTime
    $operaciones = [double]$disco.DiskTransfersPersec - [double]$previo.DiskTransfersPersec
    $tiempo = ([double]$disco.AvgDisksecPerTransfer - [double]$previo.AvgDisksecPerTransfer) / [double]$disco.Frequency_PerfTime
    $base = [double]$disco.AvgDisksecPerTransfer_Base - [double]$previo.AvgDisksecPerTransfer_Base
    Valor 'disco_iops' ($(if ($segundos -gt 0) { [math]::Round($operaciones / $segundos, 2) } else { 0 })) $disco.Name
    Valor 'disco_latencia' ($(if ($base -gt 0) { [math]::Round($tiempo / $base * 1000, 2) } else { 0 })) $disco.Name
}

# V07: páginas leídas o escritas en disco para resolver fallos de página, convertidas a KiB por segundo
$segundos = ([double]$despues.Memoria.Timestamp_PerfTime - [double]$antes.Memoria.Timestamp_PerfTime) / [double]$despues.Memoria.Frequency_PerfTime
$paginas = [double]$despues.Memoria.PagesPersec - [double]$antes.Memoria.PagesPersec
Valor 'swap_act' ($(if ($segundos -gt 0) { [math]::Round($paginas * [Environment]::SystemPageSize / 1KB / $segundos, 2) } else { 0 }))

# V11: paquetes con error sobre el total recibido (correctos más erróneos), por adaptador
foreach ($adaptador in $despues.Red) {
    $previo = $antes.Red | Where-Object Name -eq $adaptador.Name
    if (-not $previo) { continue }
    $errores = [double]$adaptador.ReceivedPacketErrors - [double]$previo.ReceivedPacketErrors
    $recibidos = ([double]$adaptador.ReceivedUnicastPackets + $adaptador.ReceivedMulticastPackets + $adaptador.ReceivedBroadcastPackets) -
        ([double]$previo.ReceivedUnicastPackets + $previo.ReceivedMulticastPackets + $previo.ReceivedBroadcastPackets)
    Valor 'red_error_rx' (Porcentaje $errores ($recibidos + $errores)) $adaptador.Name
}

# ---- Uso de recursos ----

$sistema = Get-CimInstance -ClassName Win32_OperatingSystem

# V05: memoria usada sin contar la disponible (libre más la en espera, que el sistema reutiliza al instante)
$total = [double]$sistema.TotalVisibleMemorySize * 1KB
Valor 'mem_uso' (Porcentaje ($total - [double]$despues.Memoria.AvailableBytes) $total)

# V06: archivo de paginación ocupado; 0 si no hay archivo de paginación
$paginacion = @(Get-CimInstance -ClassName Win32_PageFileUsage)
Valor 'swap_uso' (Porcentaje ($paginacion | Measure-Object -Property CurrentUsage -Sum).Sum ($paginacion | Measure-Object -Property AllocatedBaseSize -Sum).Sum)

# V08: por volumen local
foreach ($unidad in @(Get-CimInstance -ClassName Win32_LogicalDisk -Filter 'DriveType=3')) {
    if ($unidad.Size) {
        Valor 'disco_uso' (Porcentaje ([double]$unidad.Size - [double]$unidad.FreeSpace) $unidad.Size) $unidad.DeviceID
    }
}

# V19: días desde el último arranque
Valor 'uptime_dias' ([math]::Round(((Get-Date) - $sistema.LastBootUpTime).TotalDays, 2))

# ---- Registros del periodo ----

# V10: errores de E/S de los discos (bloques dañados, controlador, dispositivo no listo, paginación, reintentos)
# y corrupción del sistema de archivos
Valor 'disco_errores' (ContarEventos @{ LogName = 'System'; ProviderName = 'disk', 'Ntfs'; Id = 7, 11, 15, 51, 55, 153 })

# V12: inicios de sesión fallidos
Valor 'aut_fallidas' (ContarEventos @{ LogName = 'Security'; Id = 4625 })

# V21: memoria virtual agotada según el detector de agotamiento de recursos
Valor 'mem_agotamiento' (ContarEventos @{ LogName = 'System'; ProviderName = 'Microsoft-Windows-Resource-Exhaustion-Detector'; Id = 2004 })

# ---- Seguridad ----

# V13: 1 si la cuenta de administrador integrada (identificador relativo 500) está habilitada
$integrada = Get-CimInstance -ClassName Win32_UserAccount -Filter 'LocalAccount=True' | Where-Object SID -like 'S-1-5-*-500'
Valor 'admin_acceso' ($(if ($integrada -and -not $integrada.Disabled) { 1 } else { 0 }))

# V14: miembros del grupo Administradores (S-1-5-32-544, su nombre depende del idioma). Las cuentas locales se informan
# sin prefijo y solo si están habilitadas; las del dominio, como DOMINIO\cuenta; una cuenta eliminada, por su SID
$grupo = (New-Object Security.Principal.SecurityIdentifier 'S-1-5-32-544').Translate([Security.Principal.NTAccount]).Value.Split('\')[-1]
$administradores = foreach ($miembro in @(([ADSI]"WinNT://$env:COMPUTERNAME/$grupo,group").Invoke('Members'))) {
    $cuenta = [ADSI]$miembro
    $partes = $cuenta.Path.Substring('WinNT://'.Length).Split('/')
    if ($partes.Count -eq 1) {
        $partes[0]
    } elseif ($partes.Count -eq 2) {
        '{0}\{1}' -f $partes[0], $partes[1]
    } elseif (-not ($cuenta.SchemaClassName -eq 'User' -and ([int]$cuenta.UserFlags.Value -band 2))) {
        $partes[-1]
    }
}
Elementos 'admin_no_aut' $administradores

# V16: puertos TCP en escucha fuera de la interfaz de bucle local; con $EXCLUIR_PUERTOS_DINAMICOS, sin el rango dinámico
$dinamico = @{ Inicio = 49152; Fin = 65535 }
try {
    $tcp = Get-NetTCPSetting -SettingName Internet
    $dinamico = @{ Inicio = $tcp.DynamicPortRangeStartPort; Fin = $tcp.DynamicPortRangeStartPort + $tcp.DynamicPortRangeNumberOfPorts - 1 }
} catch {
    # Sin la configuración TCP se usa el rango dinámico por defecto
}
$puertos = Get-NetTCPConnection -State Listen | Where-Object {
    $_.LocalAddress -notmatch '^(127\.|::1$|::ffff:127\.)' -and
    -not ($EXCLUIR_PUERTOS_DINAMICOS -and $_.LocalPort -ge $dinamico.Inicio -and $_.LocalPort -le $dinamico.Fin)
} | ForEach-Object { $_.LocalPort }
$metricas.Add([ordered]@{ codigo = 'red_puerto_no_aut'; elementos = @($puertos | Sort-Object -Unique) })

# V17: 1 si el servicio de firewall no está en ejecución o algún perfil efectivo (dominio, privado, público) está desactivado
$servicioFirewall = Get-Service -Name MpsSvc
$perfilesInactivos = @(Get-NetFirewallProfile -PolicyStore ActiveStore | Where-Object { $_.Enabled -ne 'True' })
Valor 'firewall_inactivo' ($(if ($servicioFirewall.Status -ne 'Running' -or $perfilesInactivos.Count -gt 0) { 1 } else { 0 }))

# ---- Disponibilidad ----

# V20: servicios en ejecución; los requeridos que falten respecto de la lista son los no operativos
Elementos 'serv_no_operativo' (Get-Service | Where-Object Status -eq 'Running' | ForEach-Object { $_.Name })

Write-Output "Análisis de $env:COMPUTERNAME`: $($metricas.Count) mediciones en una ventana de $MUESTREO_SEGUNDOS s"
@{ metricas = $metricas.ToArray() } | ConvertTo-Json -Depth 5 -Compress
