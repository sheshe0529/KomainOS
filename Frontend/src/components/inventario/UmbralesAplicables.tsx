import { useState } from 'react'
import type { ReactNode } from 'react'
import { Gauge } from 'lucide-react'
import { umbralesApi } from '@/api/umbrales'
import type { ServidorResumenRespuesta, UmbralServidorRespuesta, ValoresUmbralRespuesta } from '@/api/types'
import { Dato } from '@/components/common/Tarjeta'
import { CELDA, ENCABEZADO } from '@/components/automatizacion/ciclo/estilosTabla'
import { ListaChips } from '@/components/umbrales/ListaChips'
import { Boton } from '@/components/ui/Boton'
import { Campo, Selector } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { BotonDetalle, FilaDetalle } from '@/components/ui/FilaDetalle'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { Paginacion } from '@/components/ui/Paginacion'
import { useDetallePanel, VistaDetalle } from '@/components/ui/PanelConsulta'
import { useConsulta } from '@/hooks/useConsulta'
import { usePaginacionLocal } from '@/hooks/usePaginacion'
import { describirMetrica, describirValores, ETIQUETA_ORIGEN, umbralEnGrupo } from '@/utils/umbrales'

function Valores({ valores, unidad, atenuado }: { valores?: ValoresUmbralRespuesta; unidad?: string; atenuado?: boolean }) {
  if (valores?.lista) {
    return (
      <span className={atenuado ? 'text-ink-faint' : 'text-ink'}>
        <span className="mr-2">{valores.lista.nombre}</span>
        <ListaChips elementos={valores.lista.elementos} maximo={5} atenuado={atenuado} />
      </span>
    )
  }
  return <span className={`font-mono tabular-nums ${atenuado ? 'text-ink-faint' : 'text-ink'}`}>{describirValores(valores, unidad)}</span>
}

/** De dónde sale lo que rige: el alcance y, si es una excepción, su objetivo */
function Origen({ u }: { u: UmbralServidorRespuesta }) {
  return (
    <>
      <span className={u.origen === 'GLOBAL' ? 'text-ink-faint' : 'text-ink'}>{u.origen ? ETIQUETA_ORIGEN[u.origen] : ''}</span>
      {u.excepcion?.objetivo?.nombre && u.origen !== 'SERVIDOR' && (
        <span className="block text-xs text-ink-faint">{u.excepcion.objetivo.nombre}</span>
      )}
    </>
  )
}

/** Un umbral: arriba qué mide y lo que rige, y debajo de dónde sale, el valor global y lo que cambia en cada grupo */
function DetalleUmbral({ u }: { u: UmbralServidorRespuesta }) {
  const unidad = u.metrica?.unidad
  return (
    <div className="flex flex-col gap-4">
      <h4 className="text-base font-semibold text-ink">{u.umbral?.nombre}</h4>
      <dl className="grid gap-4 sm:grid-cols-2">
        <Dato etiqueta="Métrica" valor={u.metrica?.nombre} />
        <Dato etiqueta="Tipo" valor={describirMetrica(u.metrica) || '—'} />
        <Dato etiqueta="Vigente" valor={<Valores valores={u.aplicado} unidad={unidad} />} />
        <Dato etiqueta="Origen" valor={<Origen u={u} />} />
        <Dato etiqueta="Global" valor={<Valores valores={u.global} unidad={unidad} atenuado />} />
        {u.excepcion?.motivo && <Dato etiqueta="Motivo de la excepción" valor={u.excepcion.motivo} />}
      </dl>
      {(u.enGrupos ?? []).length > 0 && (
        <section className="flex flex-col gap-2">
          <h5 className="text-sm font-semibold text-ink">En las órdenes de sus grupos</h5>
          <ul className="flex flex-col gap-1 text-sm text-ink-soft">
            {u.enGrupos?.map((g) => (
              <li key={g.grupo?.id}>
                {g.grupo?.nombre}: <Valores valores={g.valores} unidad={unidad} />
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

/** La tabla de umbrales de un servidor (como individual o dentro de un grupo); cada fila abre su detalle */
function ListaUmbrales({ idServidor, idGrupo, onAbrir }: {
  idServidor: number
  idGrupo?: number
  onAbrir: (u: UmbralServidorRespuesta) => void
}) {
  const umbrales = useConsulta(() => umbralesApi.delServidor(idServidor), [idServidor])
  const datos = idGrupo === undefined ? umbrales.datos ?? [] : (umbrales.datos ?? []).map((u) => umbralEnGrupo(u, idGrupo))
  const { filas, controles } = usePaginacionLocal(datos, idGrupo === undefined ? 'umbrales-servidor' : 'umbrales-grupo')

  if (umbrales.cargando && !umbrales.datos) return <Cargando />
  if (umbrales.error) return <MensajeError error={umbrales.error} onReintentar={umbrales.recargar} />
  if (umbrales.datos?.length === 0) return <p className="text-sm text-ink-soft">Sin umbrales activos.</p>

  return (
    <div className="overflow-hidden rounded-lg border border-line">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[480px] text-left text-sm">
          <thead>
            <tr className={ENCABEZADO}>
              <th className="px-3 py-2 font-medium">Umbral</th>
              <th className="px-3 py-2 font-medium">Vigente</th>
              <th className="px-3 py-2 font-medium">Origen</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {filas.map((u) => (
              <FilaDetalle key={u.umbral?.id} onAbrir={() => onAbrir(u)} className="align-top">
                <td className={CELDA}>
                  <BotonDetalle onAbrir={() => onAbrir(u)} etiqueta={`Ver el detalle del umbral ${u.umbral?.nombre}`}>
                    {u.umbral?.nombre}
                  </BotonDetalle>
                  <div className="text-xs text-ink-faint">{u.metrica?.nombre}</div>
                </td>
                <td className={CELDA}>
                  <Valores valores={u.aplicado} unidad={u.metrica?.unidad} />
                </td>
                <td className={CELDA} title={u.excepcion?.motivo}>
                  <Origen u={u} />
                </td>
              </FilaDetalle>
            ))}
          </tbody>
        </table>
      </div>
      <Paginacion {...controles} />
    </div>
  )
}

interface PanelUmbralesProps {
  /** Servidor o grupo cuyos umbrales se consultan, para el encabezado del panel */
  nombre?: string
  onCerrar: () => void
  /** En un servidor, sus umbrales como mantenimiento individual */
  idServidor?: number
  /** En un grupo, los de cada integrante dentro del grupo: la excepción del servidor tiene prioridad (RF16) */
  idGrupo?: number
  integrantes?: ServidorResumenRespuesta[]
}

/**
 * Umbrales aplicables en un panel lateral, como la actividad de una orden: cada umbral con lo que rige y de dónde sale;
 * al pulsarlo, su detalle con el valor global y lo que cambia en cada grupo
 */
function PanelUmbrales({ nombre, onCerrar, idServidor, idGrupo, integrantes = [] }: PanelUmbralesProps) {
  const detalle = useDetallePanel<UmbralServidorRespuesta>()
  const [seleccionado, setSeleccionado] = useState<number>()
  const grupal = idGrupo !== undefined
  const consultado = grupal ? integrantes.find((s) => s.id === seleccionado)?.id ?? integrantes[0]?.id : idServidor

  return (
    <Modal
      abierto
      posicion="derecha"
      ancho="xl"
      cerrarAlPulsarFuera
      titulo="Umbrales aplicables"
      descripcion={
        nombre && (
          <dl className="flex flex-wrap gap-x-5 gap-y-2 [overflow-wrap:anywhere]">
            <Dato etiqueta={grupal ? 'Grupo' : 'Servidor'} valor={nombre} mono={!grupal} />
          </dl>
        )
      }
      onCerrar={onCerrar}
    >
      <div hidden={detalle.seleccion !== undefined} className="flex flex-col gap-4">
        {grupal && consultado !== undefined && (
          <Campo etiqueta="Integrante" ayuda="Una excepción del servidor tiene prioridad sobre la del grupo." className="max-w-md">
            <Selector aria-label="Integrante" value={consultado} onChange={(e) => setSeleccionado(Number(e.target.value))}>
              {integrantes.map((s) => <option key={s.id} value={s.id}>{s.hostname}</option>)}
            </Selector>
          </Campo>
        )}
        {consultado === undefined ? (
          <p className="text-sm text-ink-soft">Agregue integrantes al grupo para consultar sus umbrales aplicables.</p>
        ) : (
          <ListaUmbrales key={consultado} idServidor={consultado} idGrupo={idGrupo} onAbrir={detalle.abrir} />
        )}
      </div>

      {detalle.seleccion && (
        <VistaDetalle onVolver={detalle.cerrar} refVolver={detalle.refVolver}>
          <DetalleUmbral u={detalle.seleccion} />
        </VistaDetalle>
      )}
    </Modal>
  )
}

/** La tarjeta de la ficha: qué es y el botón que abre el panel */
function TarjetaUmbrales({ descripcion, panel }: { descripcion: string; panel: (cerrar: () => void) => ReactNode }) {
  const [abierto, setAbierto] = useState(false)
  return (
    <section className="rounded-xl border border-line bg-panel p-5" aria-label="Umbrales aplicables">
      <div className="flex flex-wrap items-center gap-3">
        <div className="min-w-0 flex-1 basis-60">
          <h3 className="text-base font-semibold text-ink">Umbrales aplicables</h3>
          <p className="text-sm text-ink-soft">{descripcion}</p>
        </div>
        <Boton icono={Gauge} aria-haspopup="dialog" onClick={() => setAbierto(true)} className="ml-auto">
          Ver umbrales aplicables
        </Boton>
      </div>
      {abierto && panel(() => setAbierto(false))}
    </section>
  )
}

/**
 * Lo que rige para el servidor como mantenimiento individual, de dónde sale y el valor global. Si un grupo del servidor
 * tiene su propia excepción, se indica aparte: solo aplica en las órdenes de ese grupo (R2.4). Se consulta a pedido:
 * es información de referencia, no del día a día del servidor
 */
export function UmbralesServidorTarjeta({ idServidor, hostname }: { idServidor: number; hostname?: string }) {
  return (
    <TarjetaUmbrales
      descripcion="Los límites con los que se evalúa este servidor y de dónde provienen."
      panel={(cerrar) => <PanelUmbrales idServidor={idServidor} nombre={hostname} onCerrar={cerrar} />}
    />
  )
}

/** Consulta un integrante a la vez: sus excepciones pueden tener prioridad sobre las del grupo (RF16) */
export function UmbralesGrupoTarjeta({ idGrupo, nombre, integrantes }: {
  idGrupo: number
  nombre?: string
  integrantes: ServidorResumenRespuesta[]
}) {
  return (
    <TarjetaUmbrales
      descripcion="Los límites con los que se evalúa cada integrante dentro de este grupo y de dónde provienen."
      panel={(cerrar) => <PanelUmbrales idGrupo={idGrupo} integrantes={integrantes} nombre={nombre} onCerrar={cerrar} />}
    />
  )
}
