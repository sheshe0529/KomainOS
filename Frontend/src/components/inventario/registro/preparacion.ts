import type { FamiliaSistemaOperativo } from '@/api/dominio'
import type { MantenimientoRegistroPeticion } from '@/api/types'
import type { FichaServidorRespuesta, FilaAnalisisRespuesta, ServidorPeticion } from '@/api/types'
import type { Catalogos } from '@/hooks/useCatalogos'
import { duracion } from '@/utils/ventanas'

/** Borrador del asistente: sin frecuencias predeterminadas (DEC-60), la de mantenimiento falta hasta que se indica */
export type MantenimientoBorrador = Omit<MantenimientoRegistroPeticion, 'configuracion'> & {
  configuracion: Omit<MantenimientoRegistroPeticion['configuracion'], 'frecuenciaMantenimientoDias'> & {
    frecuenciaMantenimientoDias?: number
  }
}

export interface ServidorPreparado {
  clave: number
  hostname: string
  direccionIp: string
  familia?: FamiliaSistemaOperativo
  criticidad?: string
  sistemaOperativo?: string
  idServidorExistente?: number
  versionServidor?: string
  mantenimiento: MantenimientoBorrador
}

/** El mantenimiento listo para enviar; antes de confirmar, problemaMantenimiento ya exigió las frecuencias */
export function mantenimientoListo(m: MantenimientoBorrador): MantenimientoRegistroPeticion {
  const { frecuenciaMantenimientoDias } = m.configuracion
  if (frecuenciaMantenimientoDias == null) throw new Error('Indique la frecuencia de mantenimiento antes de confirmar.')
  return { ...m, configuracion: { ...m.configuracion, frecuenciaMantenimientoDias } }
}

/** Sin frecuencias predeterminadas (DEC-60): se indican por servidor; los duplicados conservan su configuración actual. */
export function prepararServidor(
  datos: Pick<ServidorPeticion, 'hostname' | 'direccionIp' | 'idNivelCriticidad' | 'idVersionSistemaOperativo'>,
  catalogos: Catalogos,
  clave = 0,
  existente?: FichaServidorRespuesta,
): ServidorPreparado {
  const criticidad = catalogos.criticidades.find((c) => c.id === datos.idNivelCriticidad)
  const sistema = catalogos.sistemasOperativos.find((s) => s.versiones?.some((v) => v.id === datos.idVersionSistemaOperativo))
  const version = sistema?.versiones?.find((v) => v.id === datos.idVersionSistemaOperativo)
  const actual = existente?.configuracion
  return {
    clave,
    hostname: datos.hostname,
    direccionIp: datos.direccionIp,
    familia: sistema?.familia,
    criticidad: criticidad?.nombre,
    sistemaOperativo: [sistema?.nombre, version?.version].filter(Boolean).join(' '),
    idServidorExistente: existente?.id,
    versionServidor: existente?.fechaActualizacion,
    mantenimiento: {
      configuracion: {
        frecuenciaRevisionDias: actual?.frecuenciaRevisionDias,
        frecuenciaMantenimientoDias: actual?.frecuenciaMantenimientoDias,
        revisionPeriodicaHabilitada: actual?.revisionPeriodicaHabilitada ?? true,
        modalidadPlanificacion: actual?.modalidadPlanificacion ?? 'AUTOMATICA',
        idCuentaServicioEvaluacion: actual?.idCuentaServicioEvaluacion,
        idCuentaServicioMantenimiento: actual?.idCuentaServicioMantenimiento,
        versionEsperada: actual?.fechaActualizacion,
      },
      ventanas: (existente?.ventanas ?? []).map((v) => ({
        diaInicio: v.diaInicio!, horaInicio: v.horaInicio!.slice(0, 5),
        diaFin: v.diaFin!, horaFin: v.horaFin!.slice(0, 5),
      })),
    },
  }
}

export function filasSeleccionadas(filas: FilaAnalisisRespuesta[], sobrescribir: number[]) {
  return filas.filter((f) => f.estado === 'NUEVA' || (f.sobrescribible && sobrescribir.includes(f.fila!)))
}

export function problemaMantenimiento(s: ServidorPreparado): string | undefined {
  const { configuracion: c, ventanas } = s.mantenimiento
  // Con la revisión inactiva su frecuencia no se muestra ni se exige; si falta, la configuración queda sin ella
  const revisionInvalida = c.revisionPeriodicaHabilitada !== false &&
    (!Number.isInteger(c.frecuenciaRevisionDias) || c.frecuenciaRevisionDias! < 1)
  if (revisionInvalida || !Number.isInteger(c.frecuenciaMantenimientoDias) || c.frecuenciaMantenimientoDias! < 1) {
    return `${s.hostname}: las frecuencias deben ser números enteros mayores que cero.`
  }
  if (ventanas.length === 0) return `${s.hostname}: agregue al menos un intervalo de mantenimiento.`
  if (ventanas.length > 50 || ventanas.some((v) => !v.horaInicio || !v.horaFin || duracion(v) <= 0)) {
    return `${s.hostname}: revise los intervalos de la ventana permisiva.`
  }
}
