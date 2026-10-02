import { ArchiveX, CalendarClock, CalendarPlus, Pencil, RotateCcw, Wrench } from 'lucide-react'
import type { FichaServidorRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { StatusPill } from '@/components/ui/StatusPill'
import { useTonoCriticidad } from '@/hooks/useTonoCriticidad'
import { ESTADO_SERVIDOR, ETIQUETA_MODALIDAD } from '@/utils/etiquetas'

export type DialogoFichaServidor = 'editar' | 'configurar' | 'ventanas' | 'baja' | 'programar'

interface EncabezadoFichaServidorProps {
  servidor: FichaServidorRespuesta
  esAdmin: boolean
  /** El responsable edita la ventana de sus servidores (RF19) */
  puedeEditarVentana: boolean
  catalogosListos: boolean
  reactivando: boolean
  onAbrir: (dialogo: DialogoFichaServidor) => void
  onReactivar: () => void
}

/** Las acciones visibles dependen del rol y del estado del servidor */
export function EncabezadoFichaServidor({
  servidor: s,
  esAdmin,
  puedeEditarVentana,
  catalogosListos,
  reactivando,
  onAbrir,
  onReactivar,
}: EncabezadoFichaServidorProps) {
  const tonoCriticidad = useTonoCriticidad()
  const estado = s.estado ? ESTADO_SERVIDOR[s.estado] : undefined
  const dadoDeBaja = s.estado === 'DADO_DE_BAJA'

  return (
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h2 className="font-mono text-2xl font-semibold text-ink">{s.hostname}</h2>
        <p className="mt-1 text-sm text-ink-soft">{s.dns ?? s.direccionIp}</p>
        <div className="mt-3 flex flex-wrap gap-2">
          {estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}
          <StatusPill tone={tonoCriticidad(s.criticidad?.prioridad)} label={`Criticidad ${s.criticidad?.nombre ?? ''}`} />
          <StatusPill tone="neutral" label={s.entorno?.nombre ?? ''} />
          {s.configuracion?.modalidadPlanificacion && (
            <StatusPill tone="neutral" label={`Modalidad: ${ETIQUETA_MODALIDAD[s.configuracion.modalidadPlanificacion]}`} />
          )}
        </div>
      </div>
      <div className="flex flex-wrap gap-2">
        {esAdmin && !dadoDeBaja && (
          <>
            <Boton icono={Pencil} onClick={() => onAbrir('editar')} disabled={!catalogosListos}>
              Editar
            </Boton>
            <Boton icono={Wrench} variante="primario" onClick={() => onAbrir('configurar')}>
              {s.configuracion ? 'Configuración' : 'Configurar mantenimiento'}
            </Boton>
          </>
        )}
        {puedeEditarVentana && !dadoDeBaja && (
          <Boton icono={CalendarClock} onClick={() => onAbrir('ventanas')}>
            Ventana permisiva
          </Boton>
        )}
        {esAdmin && s.estado === 'ACTIVO' && (
          <Boton icono={CalendarPlus} onClick={() => onAbrir('programar')}>
            Programar
          </Boton>
        )}
        {esAdmin && !dadoDeBaja && !s.bajaPendiente && (
          <Boton icono={ArchiveX} variante="fantasma" onClick={() => onAbrir('baja')}>
            Dar de baja
          </Boton>
        )}
        {esAdmin && dadoDeBaja && (
          <Boton icono={RotateCcw} variante="primario" onClick={onReactivar} cargando={reactivando}>
            Reactivar
          </Boton>
        )}
      </div>
    </div>
  )
}
