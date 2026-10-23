import { useState } from 'react'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { MetricasTab } from '@/components/umbrales/MetricasTab'
import { ReglaDePrecedencia } from '@/components/umbrales/ReglaDePrecedencia'
import { UmbralesTab } from '@/components/umbrales/UmbralesTab'

const PESTANAS = [
  { id: 'umbrales', etiqueta: 'Umbrales' },
  { id: 'metricas', etiqueta: 'Métricas' },
] as const

type Pestana = (typeof PESTANAS)[number]['id']

/** El operador consulta y el administrador define (RF16). Cada servidor muestra en su ficha los valores que le rigen */
export function UmbralesPage() {
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const [pestana, setPestana] = useState<Pestana>('umbrales')

  return (
    <>
      <PageHeader title="Umbrales" />
      <div className="mb-4">
        <ReglaDePrecedencia />
      </div>
      <div role="tablist" aria-label="Umbrales" className="mb-6 flex flex-wrap gap-2">
        {PESTANAS.map((p) => (
          <button
            key={p.id}
            role="tab"
            type="button"
            aria-selected={pestana === p.id}
            onClick={() => setPestana(p.id)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
              pestana === p.id ? 'bg-accent text-accent-ink' : 'bg-panel-muted text-ink-soft hover:text-ink'
            }`}
          >
            {p.etiqueta}
          </button>
        ))}
      </div>
      {pestana === 'umbrales' && <UmbralesTab esAdmin={esAdmin} />}
      {pestana === 'metricas' && <MetricasTab esAdmin={esAdmin} />}
    </>
  )
}
