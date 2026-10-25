import { automatizacionApi } from '@/api/automatizacion'
import { useConsulta } from '@/hooks/useConsulta'
export function ModoAutomatizacion() {
  const modo = useConsulta(automatizacionApi.modo, [])
  if (!modo.datos) return null
  return (
    <div className="mb-5 rounded-lg border border-line bg-panel-muted p-3 text-sm text-ink-soft">
      <strong className="text-ink">{modo.datos.simulado ? 'Modo simulado' : 'Ansible real activo'}</strong> ·{' '}
      {modo.datos.simulado
        ? 'Los análisis y las ejecuciones devuelven resultados estáticos. No se conectará a los servidores.'
        : 'Las intervenciones se ejecutan sobre los servidores configurados.'}{' '}
      {modo.datos.procesoHabilitado
        ? 'El ciclo automático se revisa cada 30 segundos.'
        : 'El procesamiento automático está desactivado.'}
    </div>
  )
}
