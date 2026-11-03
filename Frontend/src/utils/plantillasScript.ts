import type { FamiliaSistemaOperativo, TipoScript } from '@/api/dominio'
import type { ParametroPeticion } from '@/api/types'
// Copias de Backend/src/main/resources/ansible/scripts, los mismos que carga la demostración: al cambiar uno, cambie el otro
import analisisLinux from './plantillas/analisis_linux.sh?raw'
import analisisWindows from './plantillas/analisis_windows.ps1?raw'

export interface Plantilla {
  titulo: string
  descripcion: string
  contenido: string
  parametros: ParametroPeticion[]
}

const ARCHIVOS_CRITICOS = '/etc/passwd /etc/group /etc/shadow /etc/gshadow /etc/sudoers /etc/ssh/sshd_config /etc/crontab'

/** El editor trabaja con saltos de línea de Unix aunque el archivo se haya clonado con los de Windows */
const sinRetornos = (texto: string) => texto.replace(/\r\n/g, '\n')

/**
 * Puntos de partida que ya cumplen los contratos de KomainOS: los análisis imprimen el JSON de métricas en la última
 * línea y los mantenimientos terminan con código distinto de cero cuando algo falla. Los análisis informan las variables
 * de salud del R3.1 con los códigos del catálogo de métricas
 */
const PLANTILLAS: Record<TipoScript, Record<FamiliaSistemaOperativo, Plantilla>> = {
  ANALISIS: {
    LINUX: {
      titulo: 'Análisis de salud del R3.1 (Bash)',
      descripcion:
        'Las 21 variables de salud: CPU, memoria, intercambio, discos, red, registros del sistema, seguridad y servicios. Solo lee.',
      parametros: [
        { nombre: 'MUESTREO_SEGUNDOS', tipo: 'INTEGER', obligatorio: true, valorPorDefecto: '5' },
        { nombre: 'VENTANA_HORAS', tipo: 'INTEGER', obligatorio: true, valorPorDefecto: '24' },
        { nombre: 'ARCHIVOS_CRITICOS', tipo: 'STRING', obligatorio: true, valorPorDefecto: ARCHIVOS_CRITICOS },
      ],
      contenido: sinRetornos(analisisLinux),
    },
    WINDOWS: {
      titulo: 'Análisis de salud del R3.1 (PowerShell)',
      descripcion:
        'Las 17 variables de salud que se miden en Windows, con clases CIM que no dependen del idioma. Solo lee.',
      parametros: [
        { nombre: 'MUESTREO_SEGUNDOS', tipo: 'INTEGER', obligatorio: true, valorPorDefecto: '5' },
        { nombre: 'VENTANA_HORAS', tipo: 'INTEGER', obligatorio: true, valorPorDefecto: '24' },
        { nombre: 'EXCLUIR_PUERTOS_DINAMICOS', tipo: 'BOOLEAN', obligatorio: true, valorPorDefecto: 'true' },
      ],
      contenido: sinRetornos(analisisWindows),
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
{"metricas":[{"codigo":"cpu_uso","valor":42.5},{"codigo":"disco_uso","instancia":"/","valor":71},{"codigo":"firewall_inactivo","valor":0},{"codigo":"red_puerto_no_aut","elementos":["22","443"]}]}`
