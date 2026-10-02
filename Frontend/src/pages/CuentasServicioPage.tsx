import { useState } from 'react'
import { Ban, Eye, KeyRound, Pencil, Plus, Server, Star, StarOff } from 'lucide-react'
import { credencialesApi, cuentasServicioApi } from '@/api/credenciales'
import type { CuentaServicioRespuesta } from '@/api/types'
import { PageHeader } from '@/components/common/PageHeader'
import { AsignarServidoresModal } from '@/components/credenciales/AsignarServidoresModal'
import { CredencialFormulario } from '@/components/credenciales/CredencialFormulario'
import { RevelarSecretoModal } from '@/components/credenciales/RevelarSecretoModal'
import { RevocarCredencialModal } from '@/components/credenciales/RevocarCredencialModal'
import { SecretoModal } from '@/components/credenciales/SecretoModal'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { ESTADO_CREDENCIAL, ETIQUETA_AUTENTICACION } from '@/utils/etiquetas'
import { formatearFecha } from '@/utils/formato'
import { textoDeError } from '@/utils/errores'

type Dialogo =
  | { tipo: 'nueva' }
  | { tipo: 'datos' | 'secreto' | 'revocar' | 'revelar' | 'asignar'; cuenta: CuentaServicioRespuesta }
  | null

function textoUso(c: CuentaServicioRespuesta): string {
  const partes = []
  if (c.servidores) partes.push(`${c.servidores} servidor${c.servidores === 1 ? '' : 'es'}`)
  if (c.grupos) partes.push(`${c.grupos} grupo${c.grupos === 1 ? '' : 's'}`)
  return partes.length > 0 ? partes.join(' · ') : 'Sin asignar'
}

/** Cuentas con las que Ansible se conecta a los servidores (RF04-RF08, HU04) */
export function CuentasServicioPage() {
  const { avisar } = useAvisos()
  const cuentas = useConsulta(() => cuentasServicioApi.listar(), [])
  const [dialogo, setDialogo] = useState<Dialogo>(null)

  const lista = cuentas.datos ?? []
  const predeterminada = lista.find((c) => c.predeterminada)

  function listo(mensaje: string) {
    setDialogo(null)
    cuentas.recargar()
    avisar(mensaje)
  }

  async function cambiarPredeterminada(c: CuentaServicioRespuesta | null) {
    try {
      cuentas.reemplazar(await cuentasServicioApi.definirPredeterminada(c?.id ?? null))
      avisar(c ? `${c.nombre} es ahora la cuenta predeterminada.` : 'El sistema quedó sin cuenta predeterminada.')
    } catch (e) {
      avisar(textoDeError(e), 'error')
    }
  }

  return (
    <>
      <PageHeader
        title="Cuentas de servicio"
        description="Credenciales con las que se ejecutan los mantenimientos. Cada servidor y grupo usa la cuenta de su configuración y, si no tiene, la predeterminada."
        actions={
          <Boton variante="primario" icono={Plus} onClick={() => setDialogo({ tipo: 'nueva' })}>
            Nueva cuenta
          </Boton>
        }
      />
      <MensajeError error={cuentas.error} onReintentar={cuentas.recargar} />
      {cuentas.datos && !predeterminada && (
        <p className="mb-4 rounded-lg border border-warning/30 bg-warning-soft px-4 py-3 text-sm text-warning">
          No hay cuenta predeterminada: las configuraciones sin cuenta propia no tendrán con qué conectarse. Marque una con la
          estrella.
        </p>
      )}
      {cuentas.cargando && !cuentas.datos ? (
        <Cargando />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-line bg-panel">
          <table className="w-full min-w-[860px] text-left text-sm">
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                <th className="px-4 py-3 font-medium">Cuenta</th>
                <th className="px-4 py-3 font-medium">Usuario</th>
                <th className="px-4 py-3 font-medium">Mecanismo</th>
                <th className="px-4 py-3 font-medium">Secreto</th>
                <th className="px-4 py-3 font-medium">Uso</th>
                <th className="px-4 py-3 font-medium">Estado</th>
                <th className="px-4 py-3 text-right font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {lista.map((c) => {
                const vigente = c.estado === 'VIGENTE'
                const estado = c.estado ? ESTADO_CREDENCIAL[c.estado] : undefined
                return (
                  <tr key={c.id} className={`hover:bg-panel-muted ${vigente ? '' : 'text-ink-soft'}`}>
                    <td className="px-4 py-3">
                      <p className={`flex items-center gap-2 font-medium ${vigente ? 'text-ink' : ''}`}>
                        {c.nombre}
                        {c.predeterminada && (
                          <span className="inline-flex items-center gap-1 rounded-full bg-accent-soft px-2 py-0.5 text-[11px] font-medium text-accent">
                            <Star className="h-3 w-3 fill-current" aria-hidden="true" /> Predeterminada
                          </span>
                        )}
                      </p>
                      {c.descripcion && <p className="line-clamp-2 max-w-xs text-xs text-ink-faint">{c.descripcion}</p>}
                    </td>
                    <td className="px-4 py-3 font-mono">{c.usuarioAcceso}</td>
                    <td className="px-4 py-3">{c.tipoAutenticacion ? ETIQUETA_AUTENTICACION[c.tipoAutenticacion] : '—'}</td>
                    <td className="px-4 py-3 text-xs text-ink-soft">
                      <span className="font-mono text-ink">v{c.numeroVersion}</span> · {formatearFecha(c.fechaSecreto)}
                    </td>
                    <td className="px-4 py-3 text-ink-soft">{textoUso(c)}</td>
                    <td className="px-4 py-3">{estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}</td>
                    <td className="px-4 py-3">
                      {vigente && (
                        <div className="flex justify-end gap-1">
                          <BotonIcono icono={Eye} etiqueta="Ver secreto" onClick={() => setDialogo({ tipo: 'revelar', cuenta: c })} />
                          <BotonIcono icono={Server} etiqueta="Asignar a servidores" onClick={() => setDialogo({ tipo: 'asignar', cuenta: c })} />
                          {c.predeterminada ? (
                            <BotonIcono icono={StarOff} etiqueta="Quitar como predeterminada" onClick={() => cambiarPredeterminada(null)} />
                          ) : (
                            <BotonIcono icono={Star} etiqueta="Usar como predeterminada" onClick={() => cambiarPredeterminada(c)} />
                          )}
                          <BotonIcono icono={Pencil} etiqueta="Editar datos" onClick={() => setDialogo({ tipo: 'datos', cuenta: c })} />
                          <BotonIcono icono={KeyRound} etiqueta="Nuevo secreto" onClick={() => setDialogo({ tipo: 'secreto', cuenta: c })} />
                          <BotonIcono icono={Ban} etiqueta="Revocar" onClick={() => setDialogo({ tipo: 'revocar', cuenta: c })} />
                        </div>
                      )}
                    </td>
                  </tr>
                )
              })}
              {lista.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-10 text-center text-ink-soft">
                    Aún no hay cuentas de servicio registradas.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {dialogo?.tipo === 'nueva' && (
        <CredencialFormulario
          abierto
          titulo="Nueva cuenta de servicio"
          descripcion="Para SSH admite contraseña o llave privada, para WinRM solo usuario y contraseña."
          clase="cuenta"
          ayudaNombre="Nombre único con el que se elige en las configuraciones."
          onCerrar={() => setDialogo(null)}
          onGuardar={async (datos) => {
            await cuentasServicioApi.registrar(datos)
            listo('Cuenta de servicio registrada.')
          }}
        />
      )}
      {dialogo?.tipo === 'datos' && (
        <CredencialFormulario
          abierto
          titulo={`Editar ${dialogo.cuenta.nombre}`}
          descripcion="Para cambiar la contraseña o la llave use «Nuevo secreto»."
          clase="cuenta"
          credencial={dialogo.cuenta}
          onCerrar={() => setDialogo(null)}
          onGuardar={async ({ nombre, usuarioAcceso, descripcion }) => {
            await credencialesApi.actualizarDatos(dialogo.cuenta.id!, { nombre, usuarioAcceso, descripcion })
            listo('Datos de la cuenta actualizados.')
          }}
        />
      )}
      {dialogo?.tipo === 'secreto' && (
        <SecretoModal
          abierto
          credencial={dialogo.cuenta}
          onCerrar={() => setDialogo(null)}
          onGuardar={async (datos) => {
            const nueva = await credencialesApi.actualizarSecreto(dialogo.cuenta.id!, datos)
            listo(`Secreto actualizado: versión ${nueva.numeroVersion}.`)
          }}
        />
      )}
      {dialogo?.tipo === 'revocar' && (
        <RevocarCredencialModal
          abierto
          credencial={dialogo.cuenta}
          onCerrar={() => setDialogo(null)}
          onConfirmar={async (motivo) => {
            await credencialesApi.revocar(dialogo.cuenta.id!, motivo || undefined)
            listo('Cuenta revocada; su historial se conserva.')
          }}
        />
      )}
      {dialogo?.tipo === 'revelar' && (
        <RevelarSecretoModal
          abierto
          credencial={dialogo.cuenta}
          onCerrar={() => setDialogo(null)}
          onRevelar={(contrasena) => credencialesApi.revelar(dialogo.cuenta.id!, contrasena)}
        />
      )}
      {dialogo?.tipo === 'asignar' && (
        <AsignarServidoresModal
          abierto
          cuenta={dialogo.cuenta}
          cuentas={lista}
          onCerrar={() => setDialogo(null)}
          onAsignar={async (ids) => {
            const resultado = await cuentasServicioApi.asignar(dialogo.cuenta.id!, ids)
            listo(resultado.mensaje ?? 'Cuenta asignada.')
          }}
        />
      )}
    </>
  )
}
