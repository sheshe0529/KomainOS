import { useState } from 'react'
import { ChevronDown, Gauge } from 'lucide-react'
import type { ServidorResumenRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Campo, Selector } from '@/components/ui/Campo'
import { TablaUmbrales } from './UmbralesServidorTarjeta'

/** Consulta un integrante a la vez: sus excepciones pueden tener prioridad sobre las del grupo (RF16). */
export function UmbralesGrupoTarjeta({ idGrupo, integrantes }: { idGrupo: number; integrantes: ServidorResumenRespuesta[] }) {
  const [abierta, setAbierta] = useState(false)
  const [seleccionado, setSeleccionado] = useState<number>()
  const idServidor = integrantes.find((s) => s.id === seleccionado)?.id ?? integrantes[0]?.id

  return (
    <section className="rounded-xl border border-line bg-panel p-5" aria-label="Umbrales aplicables">
      <div className="flex flex-wrap items-center gap-3">
        <div className="min-w-0 flex-1 basis-60">
          <h3 className="text-base font-semibold text-ink">Umbrales aplicables</h3>
          <p className="text-sm text-ink-soft">Los límites con los que se evalúa cada integrante dentro de este grupo y de dónde provienen.</p>
        </div>
        <Boton icono={abierta ? ChevronDown : Gauge} aria-expanded={abierta} onClick={() => setAbierta(!abierta)} className="ml-auto">
          {abierta ? 'Ocultar umbrales' : 'Ver umbrales aplicables'}
        </Boton>
      </div>
      {abierta && (
        <div className="mt-4 flex flex-col gap-4">
          {idServidor === undefined ? (
            <p className="text-sm text-ink-soft">Agregue integrantes al grupo para consultar sus umbrales aplicables.</p>
          ) : (
            <>
              <Campo etiqueta="Integrante" ayuda="Una excepción del servidor tiene prioridad sobre la del grupo." className="max-w-md">
                <Selector aria-label="Integrante" value={idServidor} onChange={(e) => setSeleccionado(Number(e.target.value))}>
                  {integrantes.map((s) => <option key={s.id} value={s.id}>{s.hostname}</option>)}
                </Selector>
              </Campo>
              <TablaUmbrales key={idServidor} idServidor={idServidor} idGrupo={idGrupo} />
            </>
          )}
        </div>
      )}
    </section>
  )
}
