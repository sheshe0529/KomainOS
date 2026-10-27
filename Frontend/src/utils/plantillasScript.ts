import type { FamiliaSistemaOperativo, TipoScript } from '@/api/dominio'
import type { ParametroPeticion } from '@/api/types'

export interface Plantilla {
  titulo: string
  descripcion: string
  contenido: string
  parametros: ParametroPeticion[]
}

/**
 * Puntos de partida que ya cumplen los contratos de KomainOS: los análisis imprimen el JSON de métricas en la última
 * línea y los mantenimientos terminan con código distinto de cero cuando algo falla. Los códigos de métrica son
 * genéricos y deben coincidir con los del catálogo de métricas
 */
const PLANTILLAS: Record<TipoScript, Record<FamiliaSistemaOperativo, Plantilla>> = {
  ANALISIS: {
    LINUX: {
      titulo: 'Análisis de salud (Bash)',
      descripcion: 'CPU, memoria y disco raíz en porcentaje, más los puertos en escucha como lista.',
      parametros: [],
      contenido: `#!/usr/bin/env bash
# Análisis de salud: solo lee, no modifica el servidor.
# Contrato: la última línea de la salida es el JSON {"metricas": [...]}.
set -eu

# vmstat mide durante un segundo; la columna 15 es el tiempo ocioso
cpu=$(vmstat 1 2 | awk 'END { printf "%.2f", 100 - $15 }')
memoria=$(awk '/^MemTotal:/ { t = $2 } /^MemAvailable:/ { d = $2 } END { printf "%.2f", (1 - d / t) * 100 }' /proc/meminfo)
disco=$(df -P / | awk 'NR == 2 { sub("%", "", $5); print $5 }')
puertos=$(ss -H -lnt | awk '{ n = split($4, p, ":"); print "\\"" p[n] "\\"" }' | sort -u | paste -sd, -)

echo "Análisis de $(hostname) completado"
printf '{"metricas":['
printf '{"codigo":"cpu_uso_pct","valor":%s},' "$cpu"
printf '{"codigo":"memoria_uso_pct","valor":%s},' "$memoria"
printf '{"codigo":"disco_uso_pct","instancia":"/","valor":%s},' "$disco"
printf '{"codigo":"puertos_escucha","elementos":[%s]}' "$puertos"
printf ']}\\n'
`,
    },
    WINDOWS: {
      titulo: 'Análisis de salud (PowerShell)',
      descripcion: 'CPU, memoria y disco C: en porcentaje, más los puertos TCP en escucha como lista.',
      parametros: [],
      contenido: `# Análisis de salud: solo lee, no modifica el servidor.
# Contrato: la última línea de la salida es el JSON {"metricas": [...]}.
$ErrorActionPreference = 'Stop'

$so = Get-CimInstance Win32_OperatingSystem
$cpu = (Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average
$memoria = 100 * (1 - $so.FreePhysicalMemory / $so.TotalVisibleMemorySize)
$unidad = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'"
$disco = 100 * (1 - $unidad.FreeSpace / $unidad.Size)
$puertos = @(Get-NetTCPConnection -State Listen | Select-Object -ExpandProperty LocalPort -Unique | Sort-Object | ForEach-Object { "$_" })

Write-Output "Análisis de $env:COMPUTERNAME completado"
@{
    metricas = @(
        @{ codigo = 'cpu_uso_pct'; valor = [math]::Round($cpu, 2) }
        @{ codigo = 'memoria_uso_pct'; valor = [math]::Round($memoria, 2) }
        @{ codigo = 'disco_uso_pct'; instancia = 'C:'; valor = [math]::Round($disco, 2) }
        @{ codigo = 'puertos_escucha'; elementos = $puertos }
    )
} | ConvertTo-Json -Depth 4 -Compress
`,
    },
  },
  MANTENIMIENTO: {
    LINUX: {
      titulo: 'Mantenimiento con parámetros (Bash)',
      descripcion: 'Estructura con validación de parámetros, modo simulación y códigos de salida.',
      parametros: [
        { nombre: 'RUTA', tipo: 'STRING', obligatorio: true, valorPorDefecto: '/var/tmp' },
        { nombre: 'DIAS_RETENCION', tipo: 'INTEGER', obligatorio: true, valorPorDefecto: '7' },
        { nombre: 'SOLO_SIMULAR', tipo: 'BOOLEAN', obligatorio: true, valorPorDefecto: 'true' },
      ],
      contenido: `#!/usr/bin/env bash
# Mantenimiento: KomainOS define RUTA, DIAS_RETENCION y SOLO_SIMULAR antes de este código.
# Termine con exit 0 si todo salió bien; cualquier otro código marca la tarea como fallida.
set -eu

if [ ! -d "$RUTA" ]; then
    echo "La carpeta $RUTA no existe" >&2
    exit 2
fi

cantidad=$(find "$RUTA" -xdev -type f -mtime +"$DIAS_RETENCION" | wc -l)
if [ "$SOLO_SIMULAR" = true ]; then
    echo "Simulación: se procesarían $cantidad archivo(s) de $RUTA"
    exit 0
fi

find "$RUTA" -xdev -type f -mtime +"$DIAS_RETENCION" -delete
echo "Se procesaron $cantidad archivo(s) de $RUTA"
`,
    },
    WINDOWS: {
      titulo: 'Mantenimiento con parámetros (PowerShell)',
      descripcion: 'Estructura con validación de parámetros, modo simulación y códigos de salida.',
      parametros: [
        { nombre: 'RUTA', tipo: 'STRING', obligatorio: true, valorPorDefecto: 'C:\\Windows\\Temp' },
        { nombre: 'DIAS_RETENCION', tipo: 'INTEGER', obligatorio: true, valorPorDefecto: '7' },
        { nombre: 'SOLO_SIMULAR', tipo: 'BOOLEAN', obligatorio: true, valorPorDefecto: 'true' },
      ],
      contenido: `# Mantenimiento: KomainOS define $RUTA, $DIAS_RETENCION y $SOLO_SIMULAR antes de este código.
# No declare un bloque param(): las variables ya existen.
# Termine con exit 0 si todo salió bien; cualquier otro código marca la tarea como fallida.
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $RUTA -PathType Container)) {
    Write-Error "La carpeta $RUTA no existe"
    exit 2
}

$limite = (Get-Date).AddDays(-$DIAS_RETENCION)
$archivos = @(Get-ChildItem -LiteralPath $RUTA -File -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -lt $limite })

if ($SOLO_SIMULAR) {
    Write-Output "Simulación: se procesarían $($archivos.Count) archivo(s) de $RUTA"
    exit 0
}

$archivos | Remove-Item -Force -ErrorAction SilentlyContinue
Write-Output "Se procesaron $($archivos.Count) archivo(s) de $RUTA"
`,
    },
  },
}

export function plantilla(tipo: TipoScript, familia: FamiliaSistemaOperativo): Plantilla {
  return PLANTILLAS[tipo][familia]
}

/** Ejemplo del contrato de salida que muestra la ayuda del editor y el revisor de salidas */
export const EJEMPLO_SALIDA = `Texto libre del script: se conserva como salida.
{"metricas":[{"codigo":"cpu_uso_pct","valor":42.5},{"codigo":"disco_uso_pct","instancia":"/","valor":71},{"codigo":"puertos_escucha","elementos":["22","443"]}]}`
