import { Link } from 'react-router-dom'
import { Activity, Archive, ClipboardCheck, ClipboardList, Play, ShieldAlert, ShieldCheck } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { operacionApi } from '@/api/automatizacion'
import type { EtapaOrden } from '@/api/dominio'
import { PageHeader } from '@/components/common/PageHeader'
import { IndicadoresMantenimiento } from '@/components/automatizacion/IndicadoresMantenimiento'
import { MensajeError } from '@/components/ui/MensajeError'
import { useConsulta } from '@/hooks/useConsulta'
import { ETIQUETA_ETAPA } from '@/utils/etiquetas'

const ETAPAS: { etapa: EtapaOrden; icono: LucideIcon }[] = [
  { etapa: 'PLANIFICACION', icono: ClipboardList },
  { etapa: 'EVALUACION', icono: Activity },
  { etapa: 'AUTORIZACION', icono: ShieldCheck },
  { etapa: 'EJECUCION', icono: Play },
  { etapa: 'VALIDACION', icono: ClipboardCheck },
  { etapa: 'INCIDENCIA', icono: ShieldAlert },
  { etapa: 'CIERRE', icono: Archive },
]

/**
 * Dashboard de gestión (RF64-RF66): cómo va el mantenimiento en el periodo y su evolución en el tiempo. La sala de
 * operaciones queda para atender las órdenes del día
 */
export function DashboardPage() {
  const etapas = useConsulta(() => operacionApi.indicadores(), [])

  return (
    <>
      <PageHeader
        title="Dashboard"
        description="Indicadores de gestión del mantenimiento y su evolución en el tiempo."
      />

      <section aria-label="Órdenes por etapa" className="mb-8">
        <h3 className="mb-3 text-sm font-semibold text-ink">Órdenes por etapa ahora</h3>
        <MensajeError error={etapas.error} onReintentar={etapas.recargar} />
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 xl:grid-cols-7">
          {ETAPAS.map(({ etapa, icono: Icono }) => (
            <Link
              key={etapa}
              to={`/automatizacion?etapa=${etapa}`}
              title={`Ver en la sala de operaciones las órdenes en ${ETIQUETA_ETAPA[etapa].toLowerCase()}`}
              className="rounded-xl border border-line bg-panel p-4 transition-colors hover:border-ink-faint hover:bg-panel-muted"
            >
              <span className="flex items-center gap-2 text-xs text-ink-soft">
                <Icono className="h-4 w-4" aria-hidden="true" />
                {ETIQUETA_ETAPA[etapa]}
              </span>
              <strong className="mt-2 block font-mono text-2xl font-semibold tabular-nums text-ink">
                {etapas.datos?.[etapa] ?? 0}
              </strong>
            </Link>
          ))}
        </div>
      </section>

      <IndicadoresMantenimiento />
    </>
  )
}
