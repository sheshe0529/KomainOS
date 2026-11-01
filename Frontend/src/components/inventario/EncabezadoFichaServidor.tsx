import { ArchiveX, CalendarClock, CalendarPlus, Pencil, RotateCcw, Wrench } from 'lucide-react'
import type { FichaServidorRespuesta } from '@/api/types'
import { PageHeader } from '@/components/common/PageHeader'
import { Boton } from '@/components/ui/Boton'
import { StatusPill } from '@/components/ui/StatusPill'
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
  const estado = s.estado ? ESTADO_SERVIDOR[s.estado] : undefined
  const dadoDeBaja = s.estado === 'DADO_DE_BAJA'

  return (
    <PageHeader
      className=""
      volver={{ a: '/servidores', texto: 'Volver al inventario' }}
      mono
      title={s.hostname}
      description={s.dns ?? s.direccionIp}
      meta={
        <>
          {estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}
          <StatusPill tone="neutral" label={`Criticidad ${s.criticidad?.nombre ?? ''}`} />
          <StatusPill tone="neutral" label={s.entorno?.nombre ?? ''} />
          {s.configuracion?.modalidadPlanificacion && (
            <StatusPill tone="neutral" label={`Modalidad: ${ETIQUETA_MODALIDAD[s.configuracion.modalidadPlanificacion]}`} />
          )}
        </>
      }
      actions={
        <>
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
        </>
      }
    />
  )
}
