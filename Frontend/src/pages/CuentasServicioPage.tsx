import { useState } from 'react'
import { Ban, Eye, KeyRound, Pencil, Plus, Server, Star, StarOff } from 'lucide-react'
import { credencialesApi, cuentasServicioApi } from '@/api/credenciales'
import type { FinalidadCuentaServicio } from '@/api/credenciales'
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
import { Paginacion } from '@/components/ui/Paginacion'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { usePaginacionLocal } from '@/hooks/usePaginacion'
import { ESTADO_CREDENCIAL, ETIQUETA_AUTENTICACION, ETIQUETA_METODO_ELEVACION } from '@/utils/etiquetas'
import { formatearFecha } from '@/utils/formato'
import { textoDeError } from '@/utils/errores'
import { FINALIDADES_CUENTA } from '@/utils/cuentasServicio'

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
  const [cambiandoPredeterminada, setCambiandoPredeterminada] = useState(false)

  const lista = cuentas.datos ?? []
  const tabla = usePaginacionLocal(lista, 'cuentas-servicio')

  function listo(mensaje: string) {
    setDialogo(null)
    cuentas.recargar()
    avisar(mensaje)
  }

  async function cambiarPredeterminada(c: CuentaServicioRespuesta | null, finalidad: FinalidadCuentaServicio) {
    setCambiandoPredeterminada(true)
    try {
      cuentas.reemplazar(await cuentasServicioApi.definirPredeterminada(c?.id ?? null, finalidad))
      const etiqueta = finalidad === 'EVALUACION' ? 'evaluación' : 'mantenimiento'
      avisar(c ? `${c.nombre} es ahora la cuenta predeterminada de ${etiqueta}.` : `El sistema quedó sin cuenta predeterminada de ${etiqueta}.`)
    } catch (e) {
      avisar(textoDeError(e), 'error')
    } finally {
      setCambiandoPredeterminada(false)
    }
  }

  return (
    <>
      <PageHeader
        title="Cuentas de servicio"
        description="Cada servidor y grupo puede evaluar con una cuenta y ejecutar el mantenimiento con otra. Cada finalidad tiene su propia cuenta predeterminada."
        actions={
          <Boton variante="primario" icono={Plus} onClick={() => setDialogo({ tipo: 'nueva' })}>
            Nueva cuenta
          </Boton>
        }
      />
      <MensajeError error={cuentas.error} onReintentar={cuentas.recargar} />
      {cuentas.datos && FINALIDADES_CUENTA.filter(({ campoPredeterminada }) => !lista.some((cuenta) => cuenta[campoPredeterminada])).map(({ finalidad, etiqueta }) => (
        <p key={finalidad} className="mb-4 rounded-lg border border-warning/30 bg-warning-soft px-4 py-3 text-sm text-warning">
          {etiqueta}: no hay cuenta predeterminada. Las configuraciones sin cuenta propia para esta finalidad no tendrán con qué conectarse.
        </p>
      ))}
      {cuentas.cargando && !cuentas.datos ? (
        <Cargando />
      ) : (
        <div className="overflow-hidden rounded-xl border border-line bg-panel">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1000px] text-left text-sm">
              <thead>
                <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <th className="px-4 py-3 font-medium">Cuenta</th>
                  <th className="px-4 py-3 font-medium">Usuario</th>
                  <th className="px-4 py-3 font-medium">Mecanismo</th>
                  <th className="px-4 py-3 font-medium">Secreto</th>
                  <th className="px-4 py-3 font-medium">Uso</th>
                  <th className="px-4 py-3 font-medium">Predeterminada para</th>
                  <th className="px-4 py-3 font-medium">Estado</th>
                  <th className="px-4 py-3 text-right font-medium">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {tabla.filas.map((c) => {
                  const vigente = c.estado === 'VIGENTE'
                  const estado = c.estado ? ESTADO_CREDENCIAL[c.estado] : undefined
                  const finalidadesPredeterminadas = FINALIDADES_CUENTA.filter(({ campoPredeterminada }) => c[campoPredeterminada])
                    .map(({ etiqueta }) => etiqueta.toLowerCase())
                  return (
                    <tr key={c.id} className={`hover:bg-panel-muted ${vigente ? '' : 'text-ink-soft'}`}>
                      <td className="px-4 py-3">
                        <p className={`flex items-center gap-2 font-medium ${vigente ? 'text-ink' : ''}`}>
                          {c.nombre}
                        </p>
                        {finalidadesPredeterminadas.length > 0 && (
                          <em className="mt-0.5 block text-xs text-ink-faint">
                            predeterminada del sistema para {finalidadesPredeterminadas.join(' y ')}
                          </em>
                        )}
                        {c.descripcion && <p className="line-clamp-2 max-w-xs text-xs text-ink-faint">{c.descripcion}</p>}
                      </td>
                      <td className="px-4 py-3 font-mono">{c.usuarioAcceso}</td>
                      <td className="px-4 py-3">
                        {c.tipoAutenticacion ? ETIQUETA_AUTENTICACION[c.tipoAutenticacion] : '—'}
                        {c.metodoElevacion && c.metodoElevacion !== 'NINGUNO' && (
                          <span className="block text-xs text-ink-soft">
                            Elevación: {ETIQUETA_METODO_ELEVACION[c.metodoElevacion]}
                            {c.conContrasenaElevacion && ' con contraseña'}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-xs text-ink-soft">
                        {c.fechaSecreto ? `Actualizado ${formatearFecha(c.fechaSecreto)}` : '—'}
                      </td>
                      <td className="px-4 py-3 text-ink-soft">{textoUso(c)}</td>
                      <td className="px-4 py-3">
                        {vigente ? (
                          <div className="flex flex-col items-start gap-1">
                            {FINALIDADES_CUENTA.map(({ finalidad, etiqueta, campoPredeterminada }) => (
                              <Boton
                                key={finalidad}
                                variante="fantasma"
                                className={`px-2 py-1 text-xs ${c[campoPredeterminada] ? 'text-accent' : ''}`}
                                icono={c[campoPredeterminada] ? StarOff : Star}
                                title={`${etiqueta}: ${c[campoPredeterminada] ? 'quitar cuenta predeterminada' : 'usar como predeterminada'}`}
                                aria-pressed={!!c[campoPredeterminada]}
                                disabled={cambiandoPredeterminada}
                                onClick={() => cambiarPredeterminada(c[campoPredeterminada] ? null : c, finalidad)}
                              >
                                {etiqueta}
                              </Boton>
                            ))}
                          </div>
                        ) : '—'}
                      </td>
                      <td className="px-4 py-3">{estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}</td>
                      <td className="px-4 py-3">
                        {vigente && (
                          <div className="flex justify-end gap-1">
                            <BotonIcono icono={Eye} etiqueta="Ver secreto" onClick={() => setDialogo({ tipo: 'revelar', cuenta: c })} />
                            <BotonIcono icono={Server} etiqueta="Asignar a servidores" onClick={() => setDialogo({ tipo: 'asignar', cuenta: c })} />
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
                    <td colSpan={8} className="px-4 py-10 text-center text-ink-soft">
                      Aún no hay cuentas de servicio registradas.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <Paginacion {...tabla.controles} />
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
          descripcion="Use «Nuevo secreto» para cambiar el acceso o la elevación con su."
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
            await credencialesApi.actualizarSecreto(dialogo.cuenta.id!, datos)
            listo('Secreto actualizado.')
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
          onAsignar={async (ids, finalidad) => {
            const resultado = await cuentasServicioApi.asignar(dialogo.cuenta.id!, ids, finalidad)
            listo(resultado.mensaje ?? 'Cuenta asignada.')
          }}
        />
      )}
    </>
  )
}
