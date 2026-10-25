import type { FamiliaSistemaOperativo } from '@/api/dominio'
import type { MantenimientoRegistroPeticion } from '@/api/types'
import type { FichaServidorRespuesta, FilaAnalisisRespuesta, ServidorPeticion } from '@/api/types'
import type { Catalogos } from '@/hooks/useCatalogos'
import { duracion } from '@/utils/ventanas'

export interface ServidorPreparado {
  clave: number
  hostname: string
  direccionIp: string
  familia?: FamiliaSistemaOperativo
  criticidad?: string
  sistemaOperativo?: string
  idServidorExistente?: number
  versionServidor?: string
  mantenimiento: MantenimientoRegistroPeticion
}

/** Las frecuencias se copian de la criticidad; los duplicados conservan su configuración actual (HU11 CA2). */
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
        frecuenciaRevisionDias: actual?.frecuenciaRevisionDias ?? criticidad?.frecuenciaRevisionDias,
        frecuenciaMantenimientoDias: actual?.frecuenciaMantenimientoDias ?? criticidad?.frecuenciaMantenimientoDias,
        modalidadPlanificacion: actual?.modalidadPlanificacion ?? 'AUTOMATICA',
        idCuentaServicio: actual?.idCuentaServicio,
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
  if (!Number.isInteger(c.frecuenciaRevisionDias) || c.frecuenciaRevisionDias! < 1 ||
      !Number.isInteger(c.frecuenciaMantenimientoDias) || c.frecuenciaMantenimientoDias! < 1) {
    return `${s.hostname}: las frecuencias deben ser números enteros mayores que cero.`
  }
  if (ventanas.length === 0) return `${s.hostname}: agregue al menos un intervalo de mantenimiento.`
  if (ventanas.length > 50 || ventanas.some((v) => !v.horaInicio || !v.horaFin || duracion(v) <= 0)) {
    return `${s.hostname}: revise los intervalos de la ventana permisiva.`
  }
}
