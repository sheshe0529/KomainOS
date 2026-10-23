import { useState } from 'react'
import { FlaskConical } from 'lucide-react'
import { umbralesApi } from '@/api/umbrales'
import type { ReferenciaSimple, UmbralServidorRespuesta, ValoresUmbralRespuesta } from '@/api/types'
import { Tarjeta } from '@/components/common/Tarjeta'
import { ListaChips } from '@/components/umbrales/ListaChips'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { useConsulta } from '@/hooks/useConsulta'
import { describirValores, ETIQUETA_ORIGEN } from '@/utils/umbrales'
import { ProbarUmbralModal } from './ProbarUmbralModal'

interface UmbralesServidorTarjetaProps {
  idServidor: number
  grupos: ReferenciaSimple[]
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
 * tiene su propia excepción, se indica aparte: solo aplica en las órdenes de ese grupo (R2.4)
 */
export function UmbralesServidorTarjeta({ idServidor, grupos }: UmbralesServidorTarjetaProps) {
  const umbrales = useConsulta(() => umbralesApi.delServidor(idServidor), [idServidor])
  const [probando, setProbando] = useState<UmbralServidorRespuesta | null>(null)

  return (
    <Tarjeta titulo="Umbrales aplicables">
      <MensajeError error={umbrales.error} onReintentar={umbrales.recargar} />
      {umbrales.cargando && !umbrales.datos ? (
        <Cargando />
      ) : umbrales.datos?.length === 0 ? (
        <p className="text-sm text-ink-soft">Sin umbrales activos.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                <th className="py-2 pr-4 font-medium">Umbral</th>
                <th className="py-2 pr-4 font-medium">Vigente</th>
                <th className="py-2 pr-4 font-medium">Origen</th>
                <th className="py-2 pr-4 font-medium">Global</th>
                <th className="py-2">
                  <span className="sr-only">Probar</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {umbrales.datos?.map((u) => (
                <tr key={u.umbral?.id} className="align-top">
                  <td className="py-2.5 pr-4">
                    <span className="font-medium text-ink">{u.umbral?.nombre}</span>
                    <div className="text-xs text-ink-faint">{u.metrica?.nombre}</div>
                  </td>
                  <td className="py-2.5 pr-4">
                    <Valores valores={u.aplicado} unidad={u.metrica?.unidad} />
                    {u.enGrupos?.map((g) => (
                      <div key={g.grupo?.id} className="mt-1 text-xs text-ink-soft" title="Solo en las órdenes de ese grupo">
                        En {g.grupo?.nombre}: <Valores valores={g.valores} unidad={u.metrica?.unidad} />
                      </div>
                    ))}
                  </td>
                  <td className="py-2.5 pr-4" title={u.excepcion?.motivo}>
                    <span className={u.origen === 'GLOBAL' ? 'text-ink-faint' : 'text-ink'}>{u.origen ? ETIQUETA_ORIGEN[u.origen] : ''}</span>
                    {u.excepcion?.objetivo?.nombre && u.origen !== 'SERVIDOR' && (
                      <span className="block text-xs text-ink-faint">{u.excepcion.objetivo.nombre}</span>
                    )}
                  </td>
                  <td className="py-2.5 pr-4">
                    <Valores valores={u.global} unidad={u.metrica?.unidad} atenuado />
                  </td>
                  <td className="py-2.5 text-right">
                    <Boton icono={FlaskConical} onClick={() => setProbando(u)}>
                      Probar
                    </Boton>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {probando && (
        <ProbarUmbralModal idServidor={idServidor} umbral={probando} grupos={grupos} onCerrar={() => setProbando(null)} />
      )}
    </Tarjeta>
  )
}
