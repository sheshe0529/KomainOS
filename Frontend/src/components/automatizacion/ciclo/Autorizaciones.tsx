import { useState } from 'react'
import type { ReactNode } from 'react'
import { Bot, ChevronRight } from 'lucide-react'
import type { DecisionAutorizacion, RondaAutorizacion } from '@/api/types'
import { BotonDetalle, FilaDetalle } from '@/components/ui/FilaDetalle'
import { Modal } from '@/components/ui/Modal'
import { StatusPill } from '@/components/ui/StatusPill'
import type { StatusTone } from '@/components/ui/StatusPill'
import { ESTADO_AUTORIZACION } from '@/utils/automatizacion'
import { formatearFechaHora } from '@/utils/formato'
import { CELDA, CELDA_NUMERO, ENCABEZADO } from './estilosTabla'

interface AutorizacionesProps {
  rondas: RondaAutorizacion[]
}

const APROBADOR = { RESPONSABLE: 'Responsable del servidor', ADMINISTRADOR: 'Administrador' }

/** La decisión que se responde ahora: la primera pendiente, salvo que haya una fecha propuesta por resolver */
function turnoDe(r: RondaAutorizacion): number | undefined {
  const decisiones = r.decisiones ?? []
  if (decisiones.some((d) => d.estado === 'FECHA_PROPUESTA')) return undefined
  return decisiones.find((d) => d.estado === 'PENDIENTE')?.id
}

/** Quién decide, en la tabla solo por su código: el que decidió o, si está pendiente, quién puede hacerlo */
function QuienDecide({ d }: { d: DecisionAutorizacion }) {
  if (d.usuario) return <span className="font-mono text-xs">{d.codigoUsuario ?? d.usuario}</span>
  if (d.tipo === 'ADMINISTRADOR') return <span className="text-ink-soft">Cualquier administrador</span>
  const esperados = d.esperados ?? []
  if (esperados.length === 0) return <span className="text-ink-soft">Sin asignar</span>
  return <span className="font-mono text-xs">{esperados.map((e) => e.codigo).join(', ')}</span>
}

function estadoDe(d: DecisionAutorizacion, turno: number | undefined, hayPropuesta: boolean): { etiqueta: string; tono: StatusTone } {
  if (d.estado !== 'PENDIENTE') return ESTADO_AUTORIZACION[d.estado ?? 'PENDIENTE']
  if (hayPropuesta) return { etiqueta: 'Decide sobre la fecha propuesta', tono: 'warning' }
  return d.id === turno ? { etiqueta: 'Pendiente', tono: 'warning' } : { etiqueta: 'Espera su turno', tono: 'neutral' }
}

function Persona({ nombre, codigo }: { nombre?: string; codigo?: string }) {
  return (
    <span className="block">
      {nombre}
      {codigo && <span className="ml-1.5 font-mono text-xs text-ink-faint">{codigo}</span>}
    </span>
  )
}

function Dato({ etiqueta, children, ancho }: { etiqueta: string; children: ReactNode; ancho?: boolean }) {
  return (
    <div className={ancho ? 'sm:col-span-2' : ''}>
      <dt className="text-xs text-ink-faint">{etiqueta}</dt>
      <dd className="mt-0.5 text-sm text-ink">{children}</dd>
    </div>
  )
}

/** Todo lo de una aprobación: quién decide o decidió, cuándo, y su comentario o la fecha que propuso */
function DetalleDecision({ d, estado, onCerrar }: { d: DecisionAutorizacion; estado: { etiqueta: string; tono: StatusTone }; onCerrar: () => void }) {
  const esperados = d.esperados ?? []
  return (
    <Modal abierto titulo={`Aprobación ${d.secuencia}: ${d.tipo ? APROBADOR[d.tipo] : ''}`} onCerrar={onCerrar} ancho="md">
      <dl className="grid gap-4 sm:grid-cols-2">
        <Dato etiqueta="Decisión">
          <StatusPill tone={estado.tono} label={estado.etiqueta} />
        </Dato>
        <Dato etiqueta={d.usuario ? 'Decidió' : 'Puede decidir'}>
          {d.usuario ? (
            <Persona nombre={d.usuario} codigo={d.codigoUsuario} />
          ) : d.tipo === 'ADMINISTRADOR' ? (
            'Cualquier administrador'
          ) : esperados.length === 0 ? (
            'El servidor no tiene responsable asignado'
          ) : (
            esperados.map((e) => <Persona key={e.codigo} nombre={e.nombre} codigo={e.codigo} />)
          )}
        </Dato>
        <Dato etiqueta="Solicitada">{formatearFechaHora(d.fechaSolicitud)}</Dato>
        <Dato etiqueta="Decidida">{d.fechaDecision ? formatearFechaHora(d.fechaDecision) : '—'}</Dato>
        {d.fechaPropuesta && <Dato etiqueta="Propone iniciar el">{formatearFechaHora(d.fechaPropuesta)}</Dato>}
        <Dato etiqueta="Comentario" ancho>
          {d.motivo ? `«${d.motivo}»` : <span className="text-ink-soft">Sin comentario</span>}
        </Dato>
      </dl>
    </Modal>
  )
}

function TablaDecisiones({ ronda }: { ronda: RondaAutorizacion }) {
  const [idAbierta, setIdAbierta] = useState<number>()
  const turno = turnoDe(ronda)
  const hayPropuesta = (ronda.decisiones ?? []).some((d) => d.estado === 'FECHA_PROPUESTA')
  const abierta = ronda.decisiones?.find((d) => d.id === idAbierta)
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[560px] text-sm">
        <thead>
          <tr className={ENCABEZADO}>
            <th className="w-10 px-3 py-2 text-center font-medium">N.º</th>
            <th className="px-3 py-2 font-medium">Aprobador</th>
            <th className="px-3 py-2 font-medium">Quién decide</th>
            <th className="px-3 py-2 font-medium">Decisión</th>
            <th className="px-3 py-2 font-medium">Fecha</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {ronda.decisiones?.map((d) => {
            const estado = estadoDe(d, turno, hayPropuesta)
            const aprobador = d.tipo ? APROBADOR[d.tipo] : ''
            return (
              <FilaDetalle key={d.id} onAbrir={() => setIdAbierta(d.id)} className="align-middle">
                <td className={CELDA_NUMERO}>{d.secuencia}</td>
                <td className={CELDA}>
                  <BotonDetalle onAbrir={() => setIdAbierta(d.id)} etiqueta={`Ver la aprobación de ${aprobador}`}>
                    {aprobador}
                  </BotonDetalle>
                </td>
                <td className={`${CELDA} text-ink`}>
                  <QuienDecide d={d} />
                </td>
                <td className={CELDA}>
                  <StatusPill tone={estado.tono} label={estado.etiqueta} />
                </td>
                <td className={`${CELDA} whitespace-nowrap text-xs text-ink-soft`}>
                  {d.fechaDecision
                    ? formatearFechaHora(d.fechaDecision)
                    : d.id === turno || hayPropuesta
                      ? `Solicitada ${formatearFechaHora(d.fechaSolicitud)}`
                      : '—'}
                </td>
              </FilaDetalle>
            )
          })}
        </tbody>
      </table>
      {abierta && <DetalleDecision d={abierta} estado={estadoDe(abierta, turno, hayPropuesta)} onCerrar={() => setIdAbierta(undefined)} />}
    </div>
  )
}

/** Por qué terminó una ronda anterior, en una frase */
function cierreDe(r: RondaAutorizacion): string {
  const decisiones = r.decisiones ?? []
  const propuesta = decisiones.find((d) => d.estado === 'FECHA_PROPUESTA')
  if (propuesta) return `se cambió la fecha al ${formatearFechaHora(propuesta.fechaPropuesta)}`
  if (decisiones.some((d) => d.estado === 'RECHAZADA')) return 'rechazada'
  if (decisiones.some((d) => d.estado === 'VENCIDA')) return 'venció el plazo'
  return 'aprobada'
}

/**
 * Rondas de autorización (RF43-RF48, RF75): la vigente con la secuencia de aprobadores; cada fila abre el detalle de su
 * decisión. Cada fecha propuesta y aceptada abre una ronda nueva; las anteriores quedan juntas en un bloque plegado para
 * no cargar la pantalla
 */
export function Autorizaciones({ rondas }: AutorizacionesProps) {
  if (rondas.length === 0) {
    return <p className="text-sm text-ink-soft">La autorización se solicita al terminar la evaluación previa.</p>
  }
  const vigente = rondas[rondas.length - 1]
  const anteriores = rondas.slice(0, -1).reverse()

  return (
    <div className="flex flex-col gap-3">
      <section className="overflow-hidden rounded-lg border border-line">
        {vigente.automatica ? (
          <p className="flex items-center gap-2 px-4 py-3 text-sm text-ink-soft">
            <Bot className="h-4 w-4" aria-hidden="true" />
            Autorizada automáticamente: la política del entorno no exige aprobaciones para esta afectación.
          </p>
        ) : (
          <TablaDecisiones ronda={vigente} />
        )}
      </section>

      {anteriores.length > 0 && (
        <details className="group overflow-hidden rounded-lg border border-line">
          <summary className="flex cursor-pointer list-none items-center gap-2 px-4 py-2.5 text-sm text-ink-soft hover:bg-panel-muted">
            <ChevronRight className="h-4 w-4 transition-transform group-open:rotate-90" aria-hidden="true" />
            <span className="font-medium text-ink">
              {anteriores.length === 1 ? 'Ronda anterior' : `${anteriores.length} rondas anteriores`}
            </span>
            <span>· {anteriores.map((r) => (r.automatica ? 'autorizada automáticamente' : cierreDe(r))).join(' · ')}</span>
          </summary>
          <div className="flex flex-col divide-y divide-line border-t border-line">
            {anteriores.map((r) => (
              <div key={r.id}>
                <p className="px-4 py-2 text-xs font-medium uppercase tracking-wide text-ink-faint">
                  Ronda {r.ronda} · {r.automatica ? 'autorizada automáticamente' : cierreDe(r)}
                </p>
                {!r.automatica && <TablaDecisiones ronda={r} />}
              </div>
            ))}
          </div>
        </details>
      )}
    </div>
  )
}
