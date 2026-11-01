import { planificacionApi } from '@/api/planificacion'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Paginacion } from '@/components/ui/Paginacion'
import { useConsulta } from '@/hooks/useConsulta'
import { usePaginacion } from '@/hooks/usePaginacion'
import { OrdenesTabla } from './OrdenesTabla'

interface OrdenesDelObjetivoProps {
  idServidor?: number
  idGrupo?: number
  /** Cambiarlo fuerza la recarga (por ejemplo, después de programar) */
  version?: number
}

/** Órdenes de un servidor o de un grupo, la más reciente primero */
export function OrdenesDelObjetivo({ idServidor, idGrupo, version = 0 }: OrdenesDelObjetivoProps) {
  const { pagina, tamano, setPagina, setTamano } = usePaginacion('ordenes-objetivo')
  const ordenes = useConsulta(
    () => planificacionApi.ordenes({ idServidor, idGrupo, pagina, tamano }),
    [idServidor, idGrupo, version, pagina, tamano],
  )

  return (
    <>
      <MensajeError error={ordenes.error} onReintentar={ordenes.recargar} />
      {ordenes.cargando && !ordenes.datos ? (
        <Cargando />
      ) : (
        <div className="overflow-hidden rounded-lg border border-line">
          <OrdenesTabla
            ordenes={ordenes.datos?.contenido ?? []}
            sinObjetivo={idServidor !== undefined}
            vacio="Aún no tiene órdenes de mantenimiento."
          />
          <Paginacion
            pagina={pagina}
            totalPaginas={ordenes.datos?.totalPaginas ?? 0}
            totalElementos={ordenes.datos?.totalElementos ?? 0}
            onCambiar={setPagina}
            tamano={tamano}
            onTamano={setTamano}
          />
        </div>
      )}
    </>
  )
}
