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
 * línea y reciben solo las entradas declaradas; los mantenimientos terminan con código distinto de cero cuando algo falla.
 * Son esqueletos: los scripts completos están en el catálogo (el editor permite partir de otra versión) y en
 * Documentos/Scripts
 */
const PLANTILLAS: Record<TipoScript, Record<FamiliaSistemaOperativo, Plantilla>> = {
  ANALISIS: {
    LINUX: {
      titulo: 'Análisis de salud (Bash)',
      descripcion:
        'Estructura de un análisis: una métrica de valor, una de lista con archivos elegidos del catálogo y el JSON final. Solo lee.',
      parametros: [{ nombre: 'ARCHIVOS_CRITICOS', tipo: 'LISTA' }],
      contenido: `#!/usr/bin/env bash
# Análisis: solo lee el servidor. Declare ARCHIVOS_CRITICOS como Lista y elija su lista de referencia.
# KomainOS define el arreglo antes de este código; cada elemento contiene ruta:modo.
# Informe cada métrica que tenga un umbral activo: si falta una, la evaluación queda fallida.
# La última línea debe ser el JSON {"metricas": [...]} con los códigos del catálogo de métricas.
set -u
declare -p ARCHIVOS_CRITICOS >/dev/null 2>&1 || { echo "Falta la lista ARCHIVOS_CRITICOS" >&2; exit 2; }

# Valor único: porcentaje de memoria en uso
memoria=$(free | awk '/^Mem:/ { printf "%.1f", $3 * 100 / $2 }')

# Lista: los archivos elegidos con su permiso actual (ruta:modo)
archivos=""
for esperado in "\${ARCHIVOS_CRITICOS[@]+"\${ARCHIVOS_CRITICOS[@]}"}"; do
    archivo=\${esperado%:*}
    [ -e "$archivo" ] && archivos="$archivos\${archivos:+,}\\"$archivo:$(stat -c '%a' "$archivo")\\""
done

printf '{"metricas":[{"codigo":"mem_uso","valor":%s},{"codigo":"arch_crit_permisos","elementos":[%s]}]}\\n' \\
    "$memoria" "$archivos"
`,
    },
    WINDOWS: {
      titulo: 'Análisis de salud (PowerShell)',
      descripcion:
        'Estructura de un análisis: una métrica de valor, una de lista y el JSON final, con clases CIM que no dependen del idioma. Solo lee.',
      parametros: [],
      contenido: `# Análisis: solo lee el servidor. Enumera los puertos por sí mismo; no necesita una lista de entrada.
# Declare en el editor cualquier parámetro que necesite. No añada un bloque param().
# Informe cada métrica que tenga un umbral activo: si falta una, la evaluación queda fallida.
# La última línea debe ser el JSON {"metricas": [...]} con los códigos del catálogo de métricas.
$ErrorActionPreference = 'Stop'

# Valor único: porcentaje de memoria en uso
$so = Get-CimInstance Win32_OperatingSystem
$memoria = [math]::Round(100 * (1 - $so.FreePhysicalMemory / $so.TotalVisibleMemorySize), 1)

# Lista: puertos TCP en escucha fuera de la interfaz de bucle local; los que no están en la lista del umbral sobran
$puertos = @(Get-NetTCPConnection -State Listen | Where-Object { $_.LocalAddress -notmatch '^(127\\.|::1$)' } |
    ForEach-Object { [string]$_.LocalPort } | Sort-Object -Unique)

$metricas = @(
    [ordered]@{ codigo = 'mem_uso'; valor = $memoria },
    [ordered]@{ codigo = 'red_puerto_no_aut'; elementos = $puertos }
)
@{ metricas = $metricas } | ConvertTo-Json -Depth 4 -Compress
`,
    },
  },
  MANTENIMIENTO: {
    LINUX: {
      titulo: 'Mantenimiento con parámetros (Bash)',
      descripcion: 'Estructura con validación de parámetros, modo simulación y códigos de salida.',
      parametros: [
        { nombre: 'RUTA', tipo: 'STRING', valorPorDefecto: '/var/tmp' },
        { nombre: 'DIAS_RETENCION', tipo: 'INTEGER', valorPorDefecto: '7' },
        { nombre: 'SOLO_SIMULAR', tipo: 'BOOLEAN', valorPorDefecto: 'true' },
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
        { nombre: 'RUTA', tipo: 'STRING', valorPorDefecto: 'C:\\Windows\\Temp' },
        { nombre: 'DIAS_RETENCION', tipo: 'INTEGER', valorPorDefecto: '7' },
        { nombre: 'SOLO_SIMULAR', tipo: 'BOOLEAN', valorPorDefecto: 'true' },
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
{"metricas":[{"codigo":"cpu_uso","valor":42.5},{"codigo":"disco_uso","instancia":"/","valor":71},{"codigo":"firewall_inactivo","valor":0},{"codigo":"red_puerto_no_aut","elementos":["22","443"]}]}`
