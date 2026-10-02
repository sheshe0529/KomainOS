import { Link } from 'react-router-dom'
import type { FichaServidorRespuesta } from '@/api/types'
import { Dato, Tarjeta } from '@/components/common/Tarjeta'
import { ETIQUETA_FAMILIA } from '@/utils/etiquetas'
import { formatearFechaHora } from '@/utils/formato'

export function DatosGeneralesServidor({ servidor: s, className = '' }: { servidor: FichaServidorRespuesta; className?: string }) {
  const direcciones = s.direccionesIp?.length ? s.direccionesIp : [{ direccion: s.direccionIp, principal: true }]

  return (
    <Tarjeta titulo="Datos generales" className={className}>
      <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-3">
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
        <Dato
          etiqueta="Grupos"
          valor={
            s.grupos && s.grupos.length > 0 ? (
              <span className="flex flex-wrap gap-x-2">
                {s.grupos.map((g) => (
                  <Link key={g.id} to={`/grupos/${g.id}`} className="text-accent hover:underline">
                    {g.nombre}
                  </Link>
                ))}
              </span>
            ) : (
              'Sin grupos'
            )
          }
        />
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
