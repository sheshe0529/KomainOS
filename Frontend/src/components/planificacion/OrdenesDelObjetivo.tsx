import { Link } from 'react-router-dom'
import { planificacionApi } from '@/api/planificacion'
import { Tarjeta } from '@/components/common/Tarjeta'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { useConsulta } from '@/hooks/useConsulta'
import { OrdenesTabla } from './OrdenesTabla'

interface OrdenesDelObjetivoProps {
  idServidor?: number
  idGrupo?: number
  /** Cambiarlo fuerza la recarga (por ejemplo, después de programar) */
  version?: number
}

export function OrdenesDelObjetivo({ idServidor, idGrupo, version = 0 }: OrdenesDelObjetivoProps) {
  const ordenes = useConsulta(() => planificacionApi.ordenes({ idServidor, idGrupo, tamano: 10 }), [idServidor, idGrupo, version])
  const total = ordenes.datos?.totalElementos ?? 0

  return (
    <Tarjeta
      titulo="Historial de órdenes"
      acciones={
        total > 10 && (
          <Link to="/ordenes" className="text-sm text-accent hover:underline">
            Ver todas ({total})
          </Link>
        )
      }
    >
      <MensajeError error={ordenes.error} onReintentar={ordenes.recargar} />
      {ordenes.cargando && !ordenes.datos ? (
        <Cargando />
      ) : (
        <div className="-mx-5 -mb-5">
          <OrdenesTabla ordenes={ordenes.datos?.contenido ?? []} sinObjetivo={idServidor !== undefined} vacio="Aún no tiene órdenes de mantenimiento." />
        </div>
      )}
    </Tarjeta>
  )
}
