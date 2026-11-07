import { useState } from 'react'
import type { NivelAlerta } from '@/api/dominio'
import type { AnalisisRespuesta, FilaComparativa, ReporteServidor, ValidacionFuncionalRespuesta, ValidacionTecnicaRespuesta } from '@/api/types'
import { BotonDetalle, FilaDetalle } from '@/components/ui/FilaDetalle'
import { Modal } from '@/components/ui/Modal'
import { StatusPill } from '@/components/ui/StatusPill'
import type { StatusTone } from '@/components/ui/StatusPill'
import { ESTADO_VALIDACION, NIVEL } from '@/utils/automatizacion'
import { formatearFechaHora } from '@/utils/formato'
import { CELDA, ENCABEZADO } from './estilosTabla'

const GRAVEDAD: Record<NivelAlerta, number> = { NORMAL: 0, ADVERTENCIA: 1, CRITICO: 2 }

/** Qué pasó con la métrica al intervenir, en una palabra: así no hay que comparar dos columnas a ojo */
function cambio(m: FilaComparativa): { etiqueta: string; tono: StatusTone } {
  if (!m.despues) return { etiqueta: 'Sin medir aún', tono: 'neutral' }
  const antes = GRAVEDAD[m.nivelAntes ?? 'NORMAL']
  const despues = GRAVEDAD[m.nivelDespues ?? 'NORMAL']
  if (despues < antes) return despues === 0 ? { etiqueta: 'Corregida', tono: 'success' } : { etiqueta: 'Mejoró', tono: 'success' }
  if (despues > antes) return { etiqueta: 'Empeoró', tono: 'danger' }
  if (despues === 0) return { etiqueta: 'Normal', tono: 'neutral' }
  return { etiqueta: `Sigue en ${NIVEL[m.nivelDespues ?? 'NORMAL'].etiqueta.toLowerCase()}`, tono: despues === 2 ? 'danger' : 'warning' }
}

function Valor({ texto, nivel }: { texto?: string; nivel?: NivelAlerta }) {
  if (!texto) return <span className="text-ink-faint">—</span>
  const color = nivel === 'CRITICO' ? 'text-danger' : nivel === 'ADVERTENCIA' ? 'text-warning' : 'text-ink'
  return <span className={`font-mono ${color}`}>{texto}</span>
}

/** Lo que logró el mantenimiento en una frase: cuántas métricas se corrigieron, siguen fuera de umbral o empeoraron */
function cambios(r: ReporteServidor): string {
  const metricas = r.metricas ?? []
  if (metricas.every((m) => !m.despues)) return 'Sin medir aún'
  const etiquetas = metricas.map((m) => cambio(m).etiqueta)
  const corregidas = etiquetas.filter((e) => e === 'Corregida' || e === 'Mejoró').length
  const siguen = etiquetas.filter((e) => e.startsWith('Sigue')).length
  const empeoraron = etiquetas.filter((e) => e === 'Empeoró').length
  const partes = [
    corregidas > 0 && `${corregidas} ${corregidas === 1 ? 'corregida' : 'corregidas'}`,
    siguen > 0 && `${siguen} ${siguen === 1 ? 'sigue' : 'siguen'} fuera de umbral`,
    empeoraron > 0 && `${empeoraron} ${empeoraron === 1 ? 'empeoró' : 'empeoraron'}`,
  ].filter(Boolean)
  return partes.length > 0 ? partes.join(' · ') : 'Todas las métricas dentro de sus umbrales'
}

function Resultado({ tecnica }: { tecnica?: ValidacionTecnicaRespuesta }) {
  if (!tecnica) return <StatusPill tone="neutral" label="Pendiente" />
  return tecnica.resultado === 'CONFORME' ? (
    <StatusPill tone="success" label="Conforme" />
  ) : (
    <StatusPill tone="danger" label="No conforme" />
  )
}

/** Detalle de un servidor: el resultado, sus tareas y cada métrica antes y después de intervenir */
function DetalleServidor({ r, tecnica, cuenta, onCerrar }: {
  r: ReporteServidor
  tecnica?: ValidacionTecnicaRespuesta
  cuenta?: string
  onCerrar: () => void
}) {
  const metricas = r.metricas ?? []
  const total = (r.tareasFinalizadas ?? 0) + (r.tareasFallidas ?? 0) + (r.tareasNoIniciadas ?? 0)
  return (
    <Modal
      abierto
      titulo={`Validación técnica · ${r.servidor}`}
      descripcion={tecnica?.fecha ? formatearFechaHora(tecnica.fecha) : 'El análisis posterior todavía no termina.'}
      onCerrar={onCerrar}
      ancho="2xl"
    >
      <div className="flex flex-col gap-4">
        <dl className="grid gap-4 rounded-lg bg-panel-muted px-4 py-3 sm:grid-cols-3">
          <div>
            <dt className="text-xs text-ink-faint">Resultado</dt>
            <dd className="mt-1">
              <Resultado tecnica={tecnica} />
            </dd>
          </div>
          <div>
            <dt className="text-xs text-ink-faint">Tareas</dt>
            <dd className="mt-0.5 text-sm text-ink">
              {r.tareasFinalizadas} de {total} finalizadas
              {(r.tareasFallidas ?? 0) > 0 && <span className="text-danger"> · {r.tareasFallidas} fallida(s)</span>}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-ink-faint">Cuenta de servicio</dt>
            <dd className="mt-0.5 break-words text-sm text-ink">{cuenta ?? '—'}</dd>
          </div>
          {tecnica?.observaciones && (
            <div className="sm:col-span-3">
              <dt className="text-xs text-ink-faint">Observaciones</dt>
              <dd className="mt-0.5 text-sm text-ink">{tecnica.observaciones}</dd>
            </div>
          )}
        </dl>
        {metricas.length === 0 ? (
          <p className="text-sm text-ink-soft">La comparación aparece cuando la evaluación previa registra métricas.</p>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-line">
            <table className="w-full min-w-[560px] text-sm">
              <thead>
                <tr className={ENCABEZADO}>
                  <th className="px-3 py-2 font-medium">Métrica</th>
                  <th className="px-3 py-2 font-medium">Antes de intervenir</th>
                  <th className="px-3 py-2 font-medium">Después de intervenir</th>
                  <th className="px-3 py-2 font-medium">Cambio</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {metricas.map((m, i) => {
                  const c = cambio(m)
                  return (
                    <tr key={`${m.codigo}-${m.instancia ?? ''}-${i}`} className="align-middle">
                      <td className={CELDA}>
                        {m.nombre ?? m.codigo}
                        {m.instancia && <span className="ml-1 font-mono text-xs text-ink-soft">{m.instancia}</span>}
                        {m.unidad && <span className="ml-1 text-xs text-ink-faint">({m.unidad})</span>}
                      </td>
                      <td className={`${CELDA} max-w-[14rem]`}>
                        <Valor texto={m.antes} nivel={m.nivelAntes} />
                      </td>
                      <td className={`${CELDA} max-w-[14rem]`}>
                        <Valor texto={m.despues} nivel={m.nivelDespues} />
                      </td>
                      <td className={CELDA}>
                        <StatusPill tone={c.tono} label={c.etiqueta} />
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        {(r.errores ?? []).length > 0 && (
          <ul className="flex flex-col gap-1 rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">
            {r.errores?.map((e, i) => (
              <li key={i}>{e}</li>
            ))}
          </ul>
        )}
        {(r.advertencias ?? []).length > 0 && (
          <ul className="flex flex-col gap-1 rounded-lg bg-warning-soft px-3 py-2 text-sm text-warning">
            {r.advertencias?.map((a, i) => (
              <li key={i}>{a}</li>
            ))}
          </ul>
        )}
      </div>
    </Modal>
  )
}

/**
 * Validación técnica por servidor (RF54-RF56), como la evaluación previa: una fila por servidor con su resultado y lo
 * que cambió; al pulsarla se abre la comparación de cada métrica antes y después de intervenir
 */
export function ValidacionTecnica({ reporte, tecnicas, posteriores }: {
  reporte: ReporteServidor[]
  tecnicas: ValidacionTecnicaRespuesta[]
  posteriores: AnalisisRespuesta[]
}) {
  // Se guarda el servidor y no la fila: mientras la orden avanza, la ficha se refresca y el detalle abierto también
  const [idAbierto, setIdAbierto] = useState<number>()
  if (reporte.length === 0) {
    return <p className="text-sm text-ink-soft">Al terminar las tareas, KomainOS vuelve a analizar cada servidor.</p>
  }
  const tecnicaDe = (r: ReporteServidor) => tecnicas.find((t) => t.servidor === r.servidor)
  const cuentaDe = (r: ReporteServidor) => r.idServidor == null
    ? undefined
    : posteriores.findLast((a) => a.idServidor === r.idServidor)?.cuenta
  const abierto = reporte.find((r) => r.idServidor === idAbierto)

  return (
    <>
      <div className="overflow-x-auto rounded-lg border border-line">
        <table className="w-full min-w-[680px] text-sm">
          <thead>
            <tr className={ENCABEZADO}>
              <th className="px-3 py-2 font-medium">Servidor</th>
              <th className="px-3 py-2 font-medium">Resultado</th>
              <th className="px-3 py-2 font-medium">Cambios</th>
              <th className="px-3 py-2 font-medium">Cuenta de servicio</th>
              <th className="px-3 py-2 font-medium">Fecha</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {reporte.map((r) => {
              const tecnica = tecnicaDe(r)
              return (
                <FilaDetalle key={r.idServidor} onAbrir={() => setIdAbierto(r.idServidor)} className="align-middle">
                  <td className={CELDA}>
                    <BotonDetalle onAbrir={() => setIdAbierto(r.idServidor)} etiqueta={`Ver la validación de ${r.servidor}`}>
                      <span className="font-mono">{r.servidor}</span>
                    </BotonDetalle>
                  </td>
                  <td className={CELDA}>
                    <Resultado tecnica={tecnica} />
                  </td>
                  <td className={`${CELDA} text-ink-soft`}>{cambios(r)}</td>
                  <td className={`${CELDA} break-words text-ink-soft`}>{cuentaDe(r) ?? '—'}</td>
                  <td className={`${CELDA} whitespace-nowrap text-ink-soft`}>{tecnica?.fecha ? formatearFechaHora(tecnica.fecha) : '—'}</td>
                </FilaDetalle>
              )
            })}
          </tbody>
        </table>
      </div>
      {abierto && <DetalleServidor r={abierto} tecnica={tecnicaDe(abierto)} cuenta={cuentaDe(abierto)} onCerrar={() => setIdAbierto(undefined)} />}
    </>
  )
}

/** Conformidad del responsable (RF57) */
export function ValidacionFuncional({ funcional }: { funcional?: ValidacionFuncionalRespuesta }) {
  if (!funcional) {
    return <p className="text-sm text-ink-soft">Se solicita al responsable cuando la validación técnica es conforme.</p>
  }
  const estado = funcional.estado ? ESTADO_VALIDACION[funcional.estado] : undefined
  const respondida = funcional.estado === 'CONFORME' || funcional.estado === 'RECHAZADA'
  const etiquetaFecha = respondida ? 'Fecha de respuesta' : funcional.estado === 'VENCIDA' ? 'Venció' : 'Plazo'
  return (
    <dl className="grid gap-4 rounded-lg border border-line px-4 py-3 sm:grid-cols-4">
      <div>
        <dt className="text-xs text-ink-faint">Responsable</dt>
        <dd className="mt-0.5 text-sm font-medium text-ink">{funcional.responsable}</dd>
      </div>
      <div>
        <dt className="text-xs text-ink-faint">Respuesta</dt>
        <dd className="mt-0.5">{estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}</dd>
      </div>
      <div>
        <dt className="text-xs text-ink-faint">Solicitada</dt>
        <dd className="mt-0.5 text-sm text-ink">{formatearFechaHora(funcional.fechaSolicitud)}</dd>
      </div>
      <div>
        <dt className="text-xs text-ink-faint">{etiquetaFecha}</dt>
        <dd className="mt-0.5 text-sm text-ink">{formatearFechaHora(funcional.fechaRespuesta ?? funcional.fechaLimite)}</dd>
      </div>
      {funcional.observaciones && (
        <div className="sm:col-span-4">
          <dt className="text-xs text-ink-faint">Observaciones</dt>
          <dd className="mt-0.5 text-sm text-ink">«{funcional.observaciones}»</dd>
        </div>
      )}
    </dl>
  )
}
