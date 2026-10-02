import { useState } from 'react'
import { PageHeader } from '@/components/common/PageHeader'
import { CriticidadesTab } from '@/components/catalogos/CriticidadesTab'
import { EntornosTab } from '@/components/catalogos/EntornosTab'
import { SistemasOperativosTab } from '@/components/catalogos/SistemasOperativosTab'

const PESTANAS = [
  { id: 'criticidad', etiqueta: 'Criticidad' },
  { id: 'entornos', etiqueta: 'Entornos' },
  { id: 'sistemas', etiqueta: 'Sistemas operativos' },
] as const

type Pestana = (typeof PESTANAS)[number]['id']

export function CatalogosPage() {
  const [pestana, setPestana] = useState<Pestana>('criticidad')

  return (
    <>
      <PageHeader title="Catálogos" description="Parámetros base que rigen el inventario y la planificación del mantenimiento." />
      <div role="tablist" aria-label="Catálogos" className="mb-6 flex flex-wrap gap-2">
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
      {pestana === 'criticidad' && <CriticidadesTab />}
      {pestana === 'entornos' && <EntornosTab />}
      {pestana === 'sistemas' && <SistemasOperativosTab />}
    </>
  )
}
