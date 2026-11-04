import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Search } from 'lucide-react'
import type { FichaServidorRespuesta, ReferenciaSimple } from '@/api/types'
import { Dato, Tarjeta } from '@/components/common/Tarjeta'
import { Modal } from '@/components/ui/Modal'
import { StatusPill } from '@/components/ui/StatusPill'
import { ESTADO_SERVIDOR, ETIQUETA_FAMILIA } from '@/utils/etiquetas'
import { formatearFechaHora } from '@/utils/formato'

/** Un servidor puede integrar muchos grupos: el dato muestra cuántos y el recuadro los lista con un buscador */
function GruposServidor({ grupos, hostname }: { grupos: ReferenciaSimple[]; hostname?: string }) {
  const [abierto, setAbierto] = useState(false)
  const [texto, setTexto] = useState('')
  if (grupos.length === 0) return <>Sin grupos</>

  const buscado = texto.trim().toLowerCase()
  const visibles = grupos.filter((g) => !buscado || (g.nombre ?? '').toLowerCase().includes(buscado))

  return (
    <>
      <button
        type="button"
        aria-haspopup="dialog"
        onClick={() => setAbierto(true)}
        className="font-medium text-accent hover:underline"
      >
        {grupos.length === 1 ? '1 grupo' : `${grupos.length} grupos`}
      </button>
      {abierto && (
        <Modal
          abierto
          titulo="Grupos del servidor"
          descripcion={`Grupos de mantenimiento que integra ${hostname ?? 'el servidor'}.`}
          onCerrar={() => {
            setAbierto(false)
            setTexto('')
          }}
        >
          <div className="flex flex-col gap-3">
            <div className="flex items-center gap-2 rounded-lg border border-line px-3 py-2">
              <Search className="h-4 w-4 shrink-0 text-ink-faint" aria-hidden="true" />
              <input
                type="search"
                autoComplete="off"
                value={texto}
                onChange={(e) => setTexto(e.target.value)}
                placeholder="Buscar grupo"
                aria-label="Buscar grupo"
                className="w-full bg-transparent text-sm text-ink placeholder:text-ink-faint focus:outline-none"
              />
            </div>
            {visibles.length === 0 ? (
              <p className="text-sm text-ink-soft">Ningún grupo coincide con la búsqueda.</p>
            ) : (
              <ul className="flex flex-col divide-y divide-line rounded-lg border border-line">
                {visibles.map((g) => (
                  <li key={g.id}>
                    <Link to={`/grupos/${g.id}`} className="block px-4 py-2.5 text-sm text-ink hover:bg-panel-muted hover:text-accent">
                      {g.nombre}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Modal>
      )}
    </>
  )
}

export function DatosGeneralesServidor({ servidor: s, className = '' }: { servidor: FichaServidorRespuesta; className?: string }) {
  const direcciones = s.direccionesIp?.length ? s.direccionesIp : [{ direccion: s.direccionIp, principal: true }]
  const estado = s.estado ? ESTADO_SERVIDOR[s.estado] : undefined

  return (
    <Tarjeta titulo="Datos generales" className={className}>
      <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-3">
        <Dato etiqueta="Estado" valor={estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />} />
        <Dato etiqueta="Criticidad" valor={s.criticidad?.nombre} />
        <Dato etiqueta="Entorno" valor={s.entorno?.nombre} />
        {/* Nombre completo con el dominio, con el que se resuelve el servidor en la red */}
        <Dato etiqueta="Nombre DNS" valor={s.dns} mono />
        <Dato
          etiqueta={direcciones.length > 1 ? 'Direcciones IP' : 'Dirección IP'}
          valor={
            <span className="flex flex-col gap-0.5 font-mono">
              {direcciones.map((d) => (
                <span key={d.direccion} className="flex items-center gap-1.5">
                  {d.direccion}
                  {d.principal && direcciones.length > 1 && (
                    <span className="rounded bg-accent-soft px-1 font-sans text-[10px] font-medium text-accent">principal</span>
                  )}
                </span>
              ))}
            </span>
          }
        />
        <Dato etiqueta="VDC" valor={s.vdc} />
        <Dato etiqueta="Servidor físico" valor={s.servidorFisico} />
        <Dato etiqueta="VLAN" valor={s.vlan} />
        <Dato etiqueta="Clúster" valor={s.cluster} />
        <Dato etiqueta="Plataforma" valor={s.plataforma} />
        <Dato etiqueta="Sistema operativo" valor={`${s.sistemaOperativo?.nombre ?? ''} ${s.versionSistemaOperativo?.nombre ?? ''}`} />
        <Dato etiqueta="Canal remoto" valor={s.familiaSistemaOperativo ? ETIQUETA_FAMILIA[s.familiaSistemaOperativo] : undefined} />
        <Dato etiqueta="Responsable" valor={s.responsable?.nombre} />
        <Dato etiqueta="Grupos" valor={<GruposServidor grupos={s.grupos ?? []} hostname={s.hostname} />} />
        <Dato etiqueta="Alta en inventario" valor={formatearFechaHora(s.fechaAlta)} />
        <Dato etiqueta="Última actualización" valor={formatearFechaHora(s.fechaActualizacion)} />
      </dl>
      {s.descripcion && (
        <div className="mt-4 border-t border-line pt-4">
          <Dato etiqueta="Descripción" valor={s.descripcion} />
        </div>
      )}
    </Tarjeta>
  )
}
