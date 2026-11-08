import { useCallback, useEffect, useState } from 'react'
import { GraficosAutomatizacion } from './GraficosAutomatizacion'
import { operacionApi } from '@/api/automatizacion'
import { Boton } from '@/components/ui/Boton'
import { Campo } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Cargando } from '@/components/ui/Cargando'
import { SelectorFecha } from '@/components/ui/SelectorFecha'
import { useConsulta } from '@/hooks/useConsulta'
import { formatearFechaHora } from '@/utils/formato'

const REFRESCO_MS = 60_000

export function IndicadoresMantenimiento() {
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [periodo, setPeriodo] = useState<{ desde?: string; hasta?: string }>({})
  const [version, setVersion] = useState(0)
  const [actualizacion, setActualizacion] = useState<{ ultima?: string; proxima?: string }>({})
  const informe = useConsulta(() => operacionApi.kpis(periodo.desde, periodo.hasta), [periodo, version])
  const graficos = useConsulta(() => operacionApi.graficos(periodo.desde, periodo.hasta), [periodo, version])
  const cargando = informe.cargando || graficos.cargando

  const actualizar = useCallback(() => {
    setActualizacion((anterior) => ({ ultima: anterior.ultima }))
    setVersion((v) => v + 1)
  }, [])

  // Espera ambas respuestas antes de programar la próxima consulta y conserva la última actualización completa.
  useEffect(() => {
    if (cargando) return
    const fechaIndicadores = informe.datos?.fechaActualizacion
    const fechaGraficos = graficos.datos?.fechaActualizacion
    const proxima = new Date(Date.now() + REFRESCO_MS).toISOString()
    setActualizacion((anterior) => {
      if (informe.error || graficos.error || !fechaIndicadores || !fechaGraficos) {
        return { ultima: anterior.ultima, proxima }
      }
      const ultima = Date.parse(fechaIndicadores) >= Date.parse(fechaGraficos) ? fechaIndicadores : fechaGraficos
      return { ultima, proxima }
    })
    const temporizador = window.setTimeout(actualizar, REFRESCO_MS)
    return () => window.clearTimeout(temporizador)
  }, [actualizar, cargando, informe.datos, graficos.datos, informe.error, graficos.error])

  function aplicar() {
    const inicio = desde ? new Date(`${desde}T00:00:00`) : undefined
    const fin = hasta ? new Date(`${hasta}T00:00:00`) : undefined
    fin?.setDate(fin.getDate() + 1)
    setPeriodo({ desde: inicio?.toISOString(), hasta: fin?.toISOString() })
  }

  return (
    <section aria-label="Indicadores de gestión">
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <Campo etiqueta="Desde" className="w-48">
          <SelectorFecha value={desde} max={hasta || undefined} onChange={setDesde} aria-label="Desde" />
        </Campo>
        <Campo etiqueta="Hasta (inclusive)" className="w-48">
          <SelectorFecha value={hasta} min={desde || undefined} onChange={setHasta} aria-label="Hasta" />
        </Campo>
        <Boton onClick={aplicar} disabled={!!(desde && hasta && desde > hasta)}>
          Aplicar periodo
        </Boton>
        <Boton onClick={actualizar} cargando={cargando}>Actualizar</Boton>
      </div>
      <MensajeError error={informe.error} />
      {informe.cargando && !informe.datos && <Cargando texto="Calculando indicadores…" />}
      {informe.datos && (
        <>
          <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5">
            {informe.datos.indicadores?.map((k) => (
              <article key={k.codigo} className="min-w-0 rounded-xl border border-line bg-panel p-3">
                <p className="text-xs text-ink-faint">{k.codigo}</p>
                <h3 className="mt-1 text-sm leading-snug font-medium">{k.nombre}</h3>
                <p className="my-2 font-mono text-2xl font-semibold tabular-nums">
                  {k.valor == null ? '—' : k.valor.toLocaleString('es-PE', { maximumFractionDigits: 1 })}
                  <span className="ml-1 text-xs font-normal text-ink-soft">{k.unidad}</span>
                </p>
                {k.valor != null && k.unidad === '%' && (
                  <div
                    className="mb-2 h-1.5 overflow-hidden rounded-full bg-panel-muted"
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
        </>
      )}
      {(informe.datos || graficos.datos || graficos.error != null) && <GraficosAutomatizacion informe={graficos} />}
      <dl className="mt-5 flex flex-wrap gap-x-6 gap-y-2 border-t border-line pt-3 text-xs text-ink-soft">
        <div className="flex flex-wrap gap-x-1.5">
          <dt>Última actualización</dt>
          <dd className="font-medium text-ink">{formatearFechaHora(actualizacion.ultima)}</dd>
        </div>
        <div className="flex flex-wrap gap-x-1.5">
          <dt>Próxima actualización</dt>
          <dd className="font-medium text-ink">{formatearFechaHora(cargando ? undefined : actualizacion.proxima)}</dd>
        </div>
      </dl>
    </section>
  )
}
