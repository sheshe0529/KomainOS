import type { ModalidadPlanificacion } from '@/api/dominio'
import { Boton } from '@/components/ui/Boton'
import { Campo, Selector } from '@/components/ui/Campo'
import { ETIQUETA_MODALIDAD } from '@/utils/etiquetas'
import type { ServidorPreparado } from './preparacion'

export function PlanificacionPaso({ servidores, onCambiar }: { servidores: ServidorPreparado[]; onCambiar: (s: ServidorPreparado[]) => void }) {
  function aplicar(modalidadPlanificacion: ModalidadPlanificacion, clave?: number) {
    onCambiar(servidores.map((s) => clave === undefined || clave === s.clave ? { ...s, mantenimiento: { ...s.mantenimiento, configuracion: { ...s.mantenimiento.configuracion, modalidadPlanificacion } } } : s))
  }
  return <div className="flex flex-col gap-5">
    <h4 className="font-semibold">Modalidad de planificación</h4>
    <div className="grid gap-3 sm:grid-cols-2">
      <div className="rounded-lg border border-line p-4"><h5 className="font-medium">Automática</h5><p className="mt-1 text-sm text-ink-soft">El sistema genera cada ciclo de mantenimiento y busca el primer intervalo disponible dentro de la ventana permisiva.</p></div>
      <div className="rounded-lg border border-line p-4"><h5 className="font-medium">Bajo demanda</h5><p className="mt-1 text-sm text-ink-soft">El administrador solicita la programación desde el cronograma cuando necesita un mantenimiento.</p></div>
    </div>
    {servidores.length > 1 && <div className="flex flex-wrap gap-2"><Boton onClick={() => aplicar('AUTOMATICA')}>Automática para todos</Boton><Boton onClick={() => aplicar('BAJO_DEMANDA')}>Bajo demanda para todos</Boton></div>}
    {servidores.map((s) => <Campo key={s.clave} etiqueta={s.hostname}><Selector value={s.mantenimiento.configuracion.modalidadPlanificacion} onChange={(e) => aplicar(e.target.value as ModalidadPlanificacion, s.clave)}>{(['AUTOMATICA', 'BAJO_DEMANDA'] as const).map((m) => <option key={m} value={m}>{ETIQUETA_MODALIDAD[m]}</option>)}</Selector></Campo>)}
  </div>
}
