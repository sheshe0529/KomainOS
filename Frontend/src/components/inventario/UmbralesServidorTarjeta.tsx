import { useState } from 'react'
import { ChevronDown, Gauge } from 'lucide-react'
import { umbralesApi } from '@/api/umbrales'
import type { ValoresUmbralRespuesta } from '@/api/types'
import { ListaChips } from '@/components/umbrales/ListaChips'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Paginacion } from '@/components/ui/Paginacion'
import { useConsulta } from '@/hooks/useConsulta'
import { usePaginacionLocal } from '@/hooks/usePaginacion'
import { describirValores, ETIQUETA_ORIGEN } from '@/utils/umbrales'

interface UmbralesServidorTarjetaProps {
  idServidor: number
}

function TablaUmbrales({ idServidor }: { idServidor: number }) {
  const umbrales = useConsulta(() => umbralesApi.delServidor(idServidor), [idServidor])
  const { filas, controles } = usePaginacionLocal(umbrales.datos ?? [], 'umbrales-servidor')

  if (umbrales.cargando && !umbrales.datos) return <Cargando />
  if (umbrales.error) return <MensajeError error={umbrales.error} onReintentar={umbrales.recargar} />
  if (umbrales.datos?.length === 0) return <p className="text-sm text-ink-soft">Sin umbrales activos.</p>

  return (
    <div className="overflow-hidden rounded-lg border border-line">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[640px] text-left text-sm">
          <thead>
            <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
              <th className="px-4 py-2 font-medium">Umbral</th>
              <th className="px-3 py-2 font-medium">Vigente</th>
              <th className="px-3 py-2 font-medium">Origen</th>
              <th className="px-3 py-2 font-medium">Global</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {filas.map((u) => (
              <tr key={u.umbral?.id} className="align-top transition-colors hover:bg-panel-muted">
                <td className="px-4 py-2.5">
                  <span className="font-medium text-ink">{u.umbral?.nombre}</span>
                  <div className="text-xs text-ink-faint">{u.metrica?.nombre}</div>
                </td>
                <td className="px-3 py-2.5">
                  <Valores valores={u.aplicado} unidad={u.metrica?.unidad} />
                  {u.enGrupos?.map((g) => (
                    <div key={g.grupo?.id} className="mt-1 text-xs text-ink-soft" title="Solo en las órdenes de ese grupo">
                      En {g.grupo?.nombre}: <Valores valores={g.valores} unidad={u.metrica?.unidad} />
                    </div>
                  ))}
                </td>
                <td className="px-3 py-2.5" title={u.excepcion?.motivo}>
                  <span className={u.origen === 'GLOBAL' ? 'text-ink-faint' : 'text-ink'}>{u.origen ? ETIQUETA_ORIGEN[u.origen] : ''}</span>
                  {u.excepcion?.objetivo?.nombre && u.origen !== 'SERVIDOR' && (
                    <span className="block text-xs text-ink-faint">{u.excepcion.objetivo.nombre}</span>
                  )}
                </td>
                <td className="px-3 py-2.5">
                  <Valores valores={u.global} unidad={u.metrica?.unidad} atenuado />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Paginacion {...controles} />
    </div>
  )
}

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

/**
 * Lo que rige para el servidor como mantenimiento individual, de dónde sale y el valor global. Si un grupo del servidor
 * tiene su propia excepción, se indica aparte: solo aplica en las órdenes de ese grupo (R2.4). Se consulta a pedido:
 * es información de referencia, no del día a día del servidor
 */
export function UmbralesServidorTarjeta({ idServidor }: UmbralesServidorTarjetaProps) {
  const [abierta, setAbierta] = useState(false)

  return (
    <section className="rounded-xl border border-line bg-panel p-5" aria-label="Umbrales aplicables">
      <div className="flex flex-wrap items-center gap-3">
        <div className="min-w-0 flex-1 basis-60">
          <h3 className="text-base font-semibold text-ink">Umbrales aplicables</h3>
          <p className="text-sm text-ink-soft">Los límites con los que se evalúa este servidor y de dónde provienen.</p>
        </div>
        <Boton icono={abierta ? ChevronDown : Gauge} aria-expanded={abierta} onClick={() => setAbierta(!abierta)} className="ml-auto">
          {abierta ? 'Ocultar umbrales' : 'Ver umbrales aplicables'}
        </Boton>
      </div>
      {abierta && (
        <div className="mt-4">
          <TablaUmbrales idServidor={idServidor} />
        </div>
      )}
    </section>
  )
}
