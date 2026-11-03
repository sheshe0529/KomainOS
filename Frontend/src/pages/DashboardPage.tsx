import { PageHeader } from '@/components/common/PageHeader'
import { IndicadoresMantenimiento } from '@/components/automatizacion/IndicadoresMantenimiento'

/**
 * Dashboard de gestión (RF64-RF67): los indicadores del periodo y los gráficos de su evolución. Las órdenes del día
 * se atienden en la sala de operaciones
 */
export function DashboardPage() {
  return (
    <>
      <PageHeader title="Dashboard" description="Indicadores de gestión del mantenimiento y su evolución en el tiempo." />
      <IndicadoresMantenimiento />
    </>
  )
}
