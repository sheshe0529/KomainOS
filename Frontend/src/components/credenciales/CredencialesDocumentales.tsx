import { useState } from 'react'
import { Ban, Eye, KeyRound, Pencil, Plus } from 'lucide-react'
import { credencialesApi } from '@/api/credenciales'
import type { FamiliaSistemaOperativo } from '@/api/dominio'
import type { CredencialRespuesta } from '@/api/types'
import { Tarjeta } from '@/components/common/Tarjeta'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { ESTADO_CREDENCIAL, ETIQUETA_AUTENTICACION } from '@/utils/etiquetas'
import { formatearFecha, formatearFechaHora } from '@/utils/formato'
import { CredencialFormulario } from './CredencialFormulario'
import { RevelarSecretoModal } from './RevelarSecretoModal'
import { RevocarCredencialModal } from './RevocarCredencialModal'
import { SecretoModal } from './SecretoModal'

type Dialogo =
  | { tipo: 'nueva' }
  | { tipo: 'datos' | 'secreto' | 'revocar' | 'revelar'; credencial: CredencialRespuesta }
  | null

interface CredencialesDocumentalesProps {
  idServidor: number
  hostname: string
  familia?: FamiliaSistemaOperativo
  /** Un servidor dado de baja conserva sus credenciales como historial, pero no admite nuevas */
  editable: boolean
}

/** Accesos al servidor documentados en el inventario, de uso informativo y solo para el administrador (RF04, RF08) */
export function CredencialesDocumentales({ idServidor, hostname, familia, editable }: CredencialesDocumentalesProps) {
  const { avisar } = useAvisos()
  const credenciales = useConsulta(() => credencialesApi.documentales(idServidor), [idServidor])
  const [dialogo, setDialogo] = useState<Dialogo>(null)

  function listo(mensaje: string) {
    setDialogo(null)
    credenciales.recargar()
    avisar(mensaje)
  }

  const lista = credenciales.datos ?? []

  return (
    <Tarjeta
      titulo="Credenciales documentales"
      acciones={
        editable && (
          <Boton icono={Plus} variante="fantasma" onClick={() => setDialogo({ tipo: 'nueva' })}>
            Agregar
          </Boton>
        )
      }
    >
      <p className="mb-4 text-xs text-ink-faint">
        Accesos al servidor registrados con fines informativos. Los secretos se guardan cifrados y solo se muestran unos
        segundos tras confirmar su contraseña.
      </p>
      <MensajeError error={credenciales.error} onReintentar={credenciales.recargar} />
      {credenciales.cargando && !credenciales.datos ? (
        <Cargando />
      ) : lista.length === 0 ? (
        <p className="rounded-lg border border-dashed border-line px-4 py-6 text-center text-sm text-ink-soft">
          Sin credenciales registradas para este servidor.
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[680px] text-left text-sm">
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                <th className="py-2 pr-4 font-medium">Credencial</th>
                <th className="py-2 pr-4 font-medium">Usuario</th>
                <th className="py-2 pr-4 font-medium">Mecanismo</th>
                <th className="py-2 pr-4 font-medium">Secreto</th>
                <th className="py-2 pr-4 font-medium">Estado</th>
                <th className="py-2 text-right font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {lista.map((c) => {
                const vigente = c.estado === 'VIGENTE'
                const estado = c.estado ? ESTADO_CREDENCIAL[c.estado] : undefined
                return (
                  <tr key={c.id} className={vigente ? '' : 'text-ink-soft'}>
                    <td className="py-3 pr-4">
                      <p className={`font-medium ${vigente ? 'text-ink' : ''}`}>{c.nombre}</p>
                      {c.descripcion && <p className="line-clamp-2 max-w-xs text-xs text-ink-faint">{c.descripcion}</p>}
                    </td>
                    <td className="py-3 pr-4 font-mono">{c.usuarioAcceso}</td>
                    <td className="py-3 pr-4">{c.tipoAutenticacion ? ETIQUETA_AUTENTICACION[c.tipoAutenticacion] : '—'}</td>
                    <td className="py-3 pr-4 text-xs text-ink-soft">
                      <span className="font-mono text-ink">v{c.numeroVersion}</span> · {formatearFecha(c.fechaSecreto)}
                    </td>
                    <td className="py-3 pr-4">
                      {estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}
                      {c.fechaRevocacion && (
                        <span className="mt-1 block text-xs text-ink-faint">{formatearFechaHora(c.fechaRevocacion)}</span>
                      )}
                    </td>
                    <td className="py-3">
                      {vigente && (
                        <div className="flex justify-end gap-1">
                          <BotonIcono icono={Eye} etiqueta="Ver secreto" onClick={() => setDialogo({ tipo: 'revelar', credencial: c })} />
                          {editable && (
                            <>
                              <BotonIcono icono={Pencil} etiqueta="Editar datos" onClick={() => setDialogo({ tipo: 'datos', credencial: c })} />
                              <BotonIcono icono={KeyRound} etiqueta="Nuevo secreto" onClick={() => setDialogo({ tipo: 'secreto', credencial: c })} />
                              <BotonIcono icono={Ban} etiqueta="Revocar" onClick={() => setDialogo({ tipo: 'revocar', credencial: c })} />
                            </>
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {dialogo?.tipo === 'nueva' && (
        <CredencialFormulario
          abierto
          titulo={`Nueva credencial de ${hostname}`}
          descripcion="Documenta un acceso al servidor. No la usa la ejecución de mantenimientos: para eso están las cuentas de servicio."
          familia={familia}
          ayudaNombre="Por ejemplo: Administrador local, Acceso root, Consola de aplicación."
          onCerrar={() => setDialogo(null)}
          onGuardar={async (datos) => {
            await credencialesApi.registrarDocumental(idServidor, datos)
            listo('Credencial registrada.')
          }}
        />
      )}
      {dialogo?.tipo === 'datos' && (
        <CredencialFormulario
          abierto
          titulo={`Editar ${dialogo.credencial.nombre}`}
          descripcion="Para cambiar la contraseña o la llave use «Nuevo secreto»."
          credencial={dialogo.credencial}
          onCerrar={() => setDialogo(null)}
          onGuardar={async ({ nombre, usuarioAcceso, descripcion }) => {
            await credencialesApi.actualizarDatos(dialogo.credencial.id!, { nombre, usuarioAcceso, descripcion })
            listo('Datos de la credencial actualizados.')
          }}
        />
      )}
      {dialogo?.tipo === 'secreto' && (
        <SecretoModal
          abierto
          credencial={dialogo.credencial}
          familia={familia}
          onCerrar={() => setDialogo(null)}
          onGuardar={async (datos) => {
            const nueva = await credencialesApi.actualizarSecreto(dialogo.credencial.id!, datos)
            listo(`Secreto actualizado: versión ${nueva.numeroVersion}.`)
          }}
        />
      )}
      {dialogo?.tipo === 'revocar' && (
        <RevocarCredencialModal
          abierto
          credencial={dialogo.credencial}
          onCerrar={() => setDialogo(null)}
          onConfirmar={async (motivo) => {
            await credencialesApi.revocar(dialogo.credencial.id!, motivo || undefined)
            listo('Credencial revocada; su historial se conserva.')
          }}
        />
      )}
      {dialogo?.tipo === 'revelar' && (
        <RevelarSecretoModal
          abierto
          credencial={dialogo.credencial}
          onCerrar={() => setDialogo(null)}
          onRevelar={(contrasena) => credencialesApi.revelar(dialogo.credencial.id!, contrasena)}
        />
      )}
    </Tarjeta>
  )
}
