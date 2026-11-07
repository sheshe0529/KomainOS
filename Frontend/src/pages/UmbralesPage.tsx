import { useLayoutEffect, useRef, useState } from 'react'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { MetricasTab } from '@/components/umbrales/MetricasTab'
import { ReglaDePrecedencia } from '@/components/umbrales/ReglaDePrecedencia'
import { UmbralesTab } from '@/components/umbrales/UmbralesTab'
import { ListasTab } from '@/components/catalogos/ListasTab'

const PESTANAS = [
  { id: 'umbrales', etiqueta: 'Umbrales', Panel: UmbralesTab },
  { id: 'metricas', etiqueta: 'Métricas', Panel: MetricasTab },
  { id: 'listas', etiqueta: 'Listas de referencia', Panel: ListasTab },
] as const

type Pestana = (typeof PESTANAS)[number]['id']

/** El operador consulta y el administrador define (RF16). Cada servidor muestra en su ficha los valores que le rigen */
export function UmbralesPage() {
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const [{ activa, visitas }, setPestanas] = useState<{ activa: Pestana; visitas: Record<Pestana, number> }>({
    activa: 'umbrales',
    visitas: { umbrales: 1, metricas: 0, listas: 0 },
  })
  const posiciones = useRef<Partial<Record<Pestana, number>>>({})

  useLayoutEffect(() => {
    const top = posiciones.current[activa]
    if (top !== undefined) window.scrollTo({ top })
  }, [activa])

  function elegir(siguiente: Pestana) {
    if (siguiente === activa) return
    posiciones.current[activa] = window.scrollY
    setPestanas(({ visitas }) => ({
      activa: siguiente,
      visitas: { ...visitas, [siguiente]: visitas[siguiente] + 1 },
    }))
  }

  return (
    <>
      <PageHeader title="Umbrales" />
      <div role="tablist" aria-label="Umbrales" className="mb-6 flex flex-wrap gap-2">
        {PESTANAS.map((p) => (
          <button
            key={p.id}
            id={`tab-umbrales-${p.id}`}
            role="tab"
            type="button"
            aria-selected={activa === p.id}
            aria-controls={`panel-umbrales-${p.id}`}
            onClick={() => elegir(p.id)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
              activa === p.id ? 'bg-accent text-accent-ink' : 'bg-panel-muted text-ink-soft hover:text-ink'
            }`}
          >
            {p.etiqueta}
          </button>
        ))}
      </div>
      {PESTANAS.map(({ id, Panel }) => (
        <div key={id} id={`panel-umbrales-${id}`} role="tabpanel" aria-labelledby={`tab-umbrales-${id}`} hidden={activa !== id}>
          {visitas[id] > 0 && (
            <>
              {id === 'umbrales' && <div className="mb-4"><ReglaDePrecedencia /></div>}
              <Panel esAdmin={esAdmin} visita={visitas[id]} />
            </>
          )}
        </div>
      ))}
    </>
  )
}
