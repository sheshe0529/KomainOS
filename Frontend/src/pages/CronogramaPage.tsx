import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { CalendarPlus, ChevronLeft, ChevronRight } from 'lucide-react'
import { planificacionApi } from '@/api/planificacion'
import type { OrdenResumenRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { ProgramarModal, type ObjetivoProgramacion } from '@/components/planificacion/ProgramarModal'
import { SeleccionObjetivoModal } from '@/components/planificacion/SeleccionObjetivoModal'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { useConsulta } from '@/hooks/useConsulta'
import { PUNTO_GRUPO, grupoCronograma } from '@/utils/etiquetas'
import { claveDia, formatearMesAnio } from '@/utils/formato'

const DIAS = ['LUN', 'MAR', 'MIÉ', 'JUE', 'VIE', 'SÁB', 'DOM']

function mesDesdeParametro(valor: string | null): Date {
  const hoy = new Date()
  if (!valor || !/^\d{4}-\d{2}$/.test(valor)) return new Date(hoy.getFullYear(), hoy.getMonth(), 1)
  const [anio, mes] = valor.split('-').map(Number)
  return new Date(anio, mes - 1, 1)
}

/** Primer lunes visible de la grilla del mes (6 semanas, como un calendario de pared) */
function inicioDeGrilla(mes: Date): Date {
  const inicio = new Date(mes)
  const desplazamiento = (inicio.getDay() + 6) % 7 // lunes = 0
  inicio.setDate(inicio.getDate() - desplazamiento)
  return inicio
}

export function CronogramaPage() {
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const navigate = useNavigate()
  const { avisar } = useAvisos()
  const [parametros, setParametros] = useSearchParams()
  const mes = mesDesdeParametro(parametros.get('mes'))
  const [eligiendo, setEligiendo] = useState(false)
  const [objetivo, setObjetivo] = useState<ObjetivoProgramacion>()

  const inicio = inicioDeGrilla(mes)
  const fin = new Date(inicio)
  fin.setDate(fin.getDate() + 42)
  const claveMes = `${mes.getFullYear()}-${String(mes.getMonth() + 1).padStart(2, '0')}`

  const ordenes = useConsulta(() => planificacionApi.cronograma(inicio, fin), [claveMes])

  const porDia = useMemo(() => {
    const mapa = new Map<string, OrdenResumenRespuesta[]>()
    for (const o of ordenes.datos ?? []) {
      if (!o.inicioProgramado) continue
      const clave = claveDia(new Date(o.inicioProgramado))
      mapa.set(clave, [...(mapa.get(clave) ?? []), o])
    }
    return mapa
  }, [ordenes.datos])

  function cambiarMes(delta: number) {
    const nuevo = new Date(mes.getFullYear(), mes.getMonth() + delta, 1)
    setParametros({ mes: `${nuevo.getFullYear()}-${String(nuevo.getMonth() + 1).padStart(2, '0')}` })
  }

  const hoy = claveDia(new Date())
  const celdas = Array.from({ length: 42 }, (_, i) => {
    const d = new Date(inicio)
    d.setDate(d.getDate() + i)
    return d
  })

  return (
    <>
      <PageHeader
        title="Cronograma de mantenimiento"
        description="Vista mensual de las órdenes programadas. Seleccione un día para ver su distribución horaria."
        actions={
          <div className="flex items-center gap-2">
            {esAdmin && (
              <Boton variante="primario" icono={CalendarPlus} onClick={() => setEligiendo(true)}>
                Programar
              </Boton>
            )}
          </div>
        }
      />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <ul className="flex flex-wrap gap-4 text-xs text-ink-soft" aria-label="Leyenda">
          {Object.values(PUNTO_GRUPO).map((g) => (
            <li key={g.etiqueta} className="flex items-center gap-1.5">
              <span className={`h-2 w-2 rounded-full ${g.clase}`} aria-hidden="true" />
              {g.etiqueta}
            </li>
          ))}
        </ul>
        <div className="flex items-center gap-2">
          <button type="button" onClick={() => cambiarMes(-1)} aria-label="Mes anterior" className="rounded-lg border border-line bg-panel p-2 text-ink-soft hover:bg-panel-muted hover:text-ink">
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          </button>
          <span className="min-w-40 text-center text-sm font-semibold text-ink">{formatearMesAnio(mes)}</span>
          <button type="button" onClick={() => cambiarMes(1)} aria-label="Mes siguiente" className="rounded-lg border border-line bg-panel p-2 text-ink-soft hover:bg-panel-muted hover:text-ink">
            <ChevronRight className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
      </div>

      <MensajeError error={ordenes.error} onReintentar={ordenes.recargar} />

      <div className="overflow-hidden rounded-xl border border-line bg-panel">
        <div className="overflow-x-auto">
          <div className="min-w-[720px]">
            <div className="grid grid-cols-7 border-b border-line">
              {DIAS.map((d) => (
                <div key={d} className="px-3 py-2 text-center text-xs font-medium tracking-wide text-ink-faint">
                  {d}
                </div>
              ))}
            </div>
            {ordenes.cargando && !ordenes.datos ? (
              <Cargando texto="Cargando cronograma…" />
            ) : (
              <div className="grid grid-cols-7">
                {celdas.map((d, i) => {
                  const clave = claveDia(d)
                  const delDia = porDia.get(clave) ?? []
                  const fueraDeMes = d.getMonth() !== mes.getMonth()
                  const esHoy = clave === hoy
                  return (
                    <button
                      key={clave}
                      type="button"
                      onClick={() => navigate(`/cronograma/dia/${clave}`)}
                      aria-label={`${d.getDate()}: ${delDia.length} órdenes`}
                      className={`flex h-24 flex-col items-start gap-1.5 border-line p-2 text-left transition-colors hover:bg-panel-muted ${
                        i % 7 !== 6 ? 'border-r' : ''
                      } ${i < 35 ? 'border-b' : ''} ${fueraDeMes ? 'bg-panel-muted/40' : ''}`}
                    >
                      <span
                        className={`flex h-6 w-6 items-center justify-center rounded-full font-mono text-xs ${
                          esHoy ? 'bg-accent font-semibold text-accent-ink ring-2 ring-detail' : fueraDeMes ? 'text-ink-faint' : 'text-ink-soft'
                        }`}
                      >
                        {d.getDate()}
                      </span>
                      {delDia.length > 0 && (
                        <>
                          <span className="rounded-md bg-accent-soft px-1.5 py-0.5 font-mono text-[11px] font-medium text-accent">
                            {delDia.length} {delDia.length === 1 ? 'orden' : 'órdenes'}
                          </span>
                          <span className="flex gap-1" aria-hidden="true">
                            {delDia.slice(0, 6).map((o) => (
                              <span key={o.id} className={`h-1.5 w-1.5 rounded-full ${PUNTO_GRUPO[grupoCronograma(o.etapa)].clase}`} />
                            ))}
                          </span>
                        </>
                      )}
                    </button>
                  )
                })}
              </div>
            )}
          </div>
        </div>
      </div>

      {eligiendo && (
        <SeleccionObjetivoModal
          abierto
          onCerrar={() => setEligiendo(false)}
          onElegir={(o) => {
            setEligiendo(false)
            setObjetivo(o)
          }}
        />
      )}
      {objetivo && (
        <ProgramarModal
          abierto
          objetivo={objetivo}
          onCerrar={() => setObjetivo(undefined)}
          onGuardado={(orden) => {
            setObjetivo(undefined)
            avisar(`Orden ${orden.resumen?.codigo} programada.`)
            ordenes.recargar()
          }}
        />
      )}
    </>
  )
}
