import { useState } from 'react'
import { GraficosAutomatizacion } from './GraficosAutomatizacion'
import { automatizacionApi } from '@/api/automatizacion'
import { Boton } from '@/components/ui/Boton'
import { Campo, Entrada } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Cargando } from '@/components/ui/Cargando'
import { useConsulta } from '@/hooks/useConsulta'
import { formatearFechaHora } from '@/utils/formato'

export function IndicadoresMantenimiento() {
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [periodo, setPeriodo] = useState<{ desde?: string; hasta?: string }>({})
  const informe = useConsulta(() => automatizacionApi.kpis(periodo.desde, periodo.hasta), [periodo])

  function aplicar() {
    const inicio = desde ? new Date(`${desde}T00:00:00`) : undefined
    const fin = hasta ? new Date(`${hasta}T00:00:00`) : undefined
    fin?.setDate(fin.getDate() + 1)
    setPeriodo({ desde: inicio?.toISOString(), hasta: fin?.toISOString() })
  }

  return (
    <section aria-label="Indicadores de gestión">
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <Campo etiqueta="Desde">
          <Entrada type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
        </Campo>
        <Campo etiqueta="Hasta (inclusive)">
          <Entrada type="date" value={hasta} min={desde} onChange={(e) => setHasta(e.target.value)} />
        </Campo>
        <Boton onClick={aplicar} disabled={!!(desde && hasta && desde > hasta)}>
          Aplicar periodo
        </Boton>
        <Boton onClick={informe.recargar}>Actualizar indicadores</Boton>
      </div>
      <MensajeError error={informe.error} />
      {informe.cargando && !informe.datos && <Cargando texto="Calculando indicadores…" />}
      {informe.datos && (
        <>
          <p className="mb-4 text-xs text-ink-soft">
            Periodo: {formatearFechaHora(informe.datos.desde)} a {formatearFechaHora(informe.datos.hasta)} (fin
            exclusivo). Actualizado: {formatearFechaHora(informe.datos.fechaActualizacion)}.
          </p>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {informe.datos.indicadores?.map((k) => (
              <article key={k.codigo} className="rounded-xl border border-line bg-panel p-5">
                <p className="text-xs text-ink-faint">{k.codigo}</p>
                <h3 className="mt-1 font-medium">{k.nombre}</h3>
                <p className="my-3 text-3xl font-semibold tabular-nums">
                  {k.valor == null ? '—' : k.valor.toLocaleString('es-PE', { maximumFractionDigits: 1 })}
                  <span className="ml-1 text-base font-normal text-ink-soft">{k.unidad}</span>
                </p>
                {k.valor != null && k.unidad === '%' && (
                  <div
                    className="mb-3 h-2 overflow-hidden rounded-full bg-panel-muted"
                    role="meter"
                    aria-label={k.nombre}
                    aria-valuenow={k.valor}
                    aria-valuemin={0}
                    aria-valuemax={100}
                  >
                    <div
                      className={`h-full rounded-full ${k.valor >= (k.meta ?? 0) ? 'bg-success' : 'bg-warning'}`}
                      style={{ width: `${Math.min(100, k.valor)}%` }}
                    />
                  </div>
                )}
                <p className="text-xs text-ink-soft">
                  {k.denominador
                    ? `${k.numerador?.toLocaleString('es-PE', { maximumFractionDigits: 1 })} ${k.unidad === 'h' ? 'horas acumuladas en' : 'de'} ${k.denominador} registros`
                    : 'Sin datos en el periodo'}
                </p>
                <p className="mt-1 text-xs text-ink-faint">
                  {k.meta == null ? 'Meta según SLA de la organización' : `Meta: ≥ ${k.meta} %`}
                </p>
              </article>
            ))}
          </div>
          <GraficosAutomatizacion />
          <div className="mt-4 flex flex-wrap gap-3 text-xs text-ink-soft">
            <span>Inventario actual:</span>
            {informe.datos.inventario?.map((e) => (
              <span key={e.estado}>
                {e.estado?.replaceAll('_', ' ')}: <strong>{e.cantidad}</strong>
              </span>
            ))}
          </div>
          <p className="mt-4 text-xs leading-relaxed text-ink-soft">
            El inventario refleja el estado actual. Los demás indicadores usan el periodo seleccionado (últimos 30 días
            por defecto). Cada servidor de una orden cuenta como un mantenimiento; se excluyen cancelaciones y rechazos
            del cumplimiento. Las validaciones vencidas no cuentan como respuestas. Un valor sin datos se muestra como
            —.
          </p>
        </>
      )}
    </section>
  )
}
