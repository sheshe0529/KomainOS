import type { FichaAutomatizacionRespuesta } from '@/api/types'
import {
  ESTADO_AUTORIZACION,
  ESTADO_EVALUACION,
  ESTADO_INCIDENCIA,
  ESTADO_TAREA_MOP,
  ESTADO_VALIDACION,
  filaCsv,
  NIVEL,
  ORIGEN_INCIDENCIA,
  TIPO_EVALUACION,
} from './automatizacion'
import { ETIQUETA_ESTADO_ORDEN } from './etiquetas'

interface Evento {
  fecha?: string
  tipo: string
  servidor?: string
  descripcion: string
  resultado?: string
  usuario?: string
  detalle?: string
}

/** La bitácora como eventos en orden cronológico: estados, análisis, decisiones, tareas, validaciones e incidencias */
export function eventosBitacora(f: FichaAutomatizacionRespuesta): Evento[] {
  const eventos: Evento[] = []

  for (const h of f.historial ?? []) {
    eventos.push({
      fecha: h.fechaHora,
      tipo: 'Estado de la orden',
      descripcion: `${h.estadoAnterior ? `${ETIQUETA_ESTADO_ORDEN[h.estadoAnterior]} → ` : ''}${
        h.estadoNuevo ? ETIQUETA_ESTADO_ORDEN[h.estadoNuevo] : ''
      }`,
      usuario: h.usuario?.nombre ?? 'Sistema',
      detalle: h.motivo,
    })
  }

  for (const a of f.evaluaciones ?? []) {
    eventos.push({
      fecha: a.fechaInicio,
      tipo: a.tipo ? TIPO_EVALUACION[a.tipo] : 'Análisis',
      servidor: a.servidor,
      descripcion: `${a.script} v${a.numeroVersion}${a.cuenta ? ` con ${a.cuenta}` : ''}`,
      resultado: [a.estado ? ESTADO_EVALUACION[a.estado].etiqueta : '', a.nivel ? NIVEL[a.nivel].etiqueta : '']
        .filter(Boolean)
        .join(' · '),
      detalle:
        a.error ??
        (a.mediciones ?? [])
          .map((m) => `${m.codigo}${m.instancia ? `[${m.instancia}]` : ''}=${m.valor ?? (m.elementos ?? []).join('|')}`)
          .join(', '),
    })
  }

  for (const r of f.autorizaciones ?? []) {
    for (const d of r.decisiones ?? []) {
      if (!d.fechaDecision && !d.fechaSolicitud) continue
      eventos.push({
        fecha: d.fechaDecision ?? d.fechaSolicitud,
        tipo: `Autorización (ronda ${r.ronda})`,
        descripcion: d.tipo === 'RESPONSABLE' ? 'Responsable' : 'Administrador',
        resultado: d.estado ? ESTADO_AUTORIZACION[d.estado].etiqueta : '',
        usuario: d.usuario,
        detalle: [d.motivo, d.fechaPropuesta ? `Fecha propuesta: ${d.fechaPropuesta}` : ''].filter(Boolean).join(' · '),
      })
    }
  }

  for (const m of f.mops ?? []) {
    for (const t of m.tareas ?? []) {
      if (!t.fechaInicio) continue
      eventos.push({
        fecha: t.fechaInicio,
        tipo: 'Tarea',
        servidor: m.servidor,
        descripcion: `${t.orden}. ${t.tarea} · ${t.script} v${t.numeroVersion}`,
        resultado: `${t.estado ? ESTADO_TAREA_MOP[t.estado].etiqueta : ''}${
          t.codigoRetorno !== undefined && t.codigoRetorno !== null ? ` (código ${t.codigoRetorno})` : ''
        }`,
        usuario: t.cuenta,
        detalle: t.error ?? t.salida,
      })
    }
  }

  for (const v of f.validacionesTecnicas ?? []) {
    eventos.push({
      fecha: v.fecha,
      tipo: 'Validación técnica',
      servidor: v.servidor,
      descripcion: 'Análisis posterior contra los umbrales',
      resultado: v.resultado === 'CONFORME' ? 'Conforme' : 'No conforme',
      detalle: v.observaciones,
    })
  }

  const vf = f.validacionFuncional
  if (vf?.fechaRespuesta) {
    eventos.push({
      fecha: vf.fechaRespuesta,
      tipo: 'Validación funcional',
      descripcion: 'Conformidad del responsable',
      resultado: vf.estado ? ESTADO_VALIDACION[vf.estado].etiqueta : '',
      usuario: vf.responsable,
      detalle: vf.observaciones,
    })
  }

  for (const i of f.incidencias ?? []) {
    eventos.push({
      fecha: i.fechaApertura,
      tipo: 'Incidencia',
      servidor: i.servidor,
      descripcion: i.origen ? ORIGEN_INCIDENCIA[i.origen] : 'Incidencia',
      resultado: i.estado ? ESTADO_INCIDENCIA[i.estado].etiqueta : '',
      usuario: i.atendidaPor,
      detalle: [i.error, i.diagnostico, i.resultado].filter(Boolean).join(' · '),
    })
  }

  return eventos.sort((a, b) => (a.fecha ?? '').localeCompare(b.fecha ?? ''))
}

export function bitacoraCsv(f: FichaAutomatizacionRespuesta): string {
  return [
    filaCsv(['Fecha', 'Evento', 'Servidor', 'Descripción', 'Resultado', 'Usuario o cuenta', 'Detalle']),
    ...eventosBitacora(f).map((e) =>
      filaCsv([e.fecha, e.tipo, e.servidor, e.descripcion, e.resultado, e.usuario, e.detalle]),
    ),
  ].join('\r\n')
}
