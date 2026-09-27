import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArchiveX, ArrowLeft, CalendarClock, CalendarPlus, Pencil, RotateCcw, Wrench } from 'lucide-react'
import { servidoresApi } from '@/api/inventario'
import type { FichaServidorRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { Dato, Tarjeta } from '@/components/common/Tarjeta'
import { BajaModal } from '@/components/inventario/BajaModal'
import { ConfiguracionModal } from '@/components/inventario/ConfiguracionModal'
import { ServidorFormulario } from '@/components/inventario/ServidorFormulario'
import { VentanasModal } from '@/components/inventario/VentanasModal'
import { OrdenesDelObjetivo } from '@/components/planificacion/OrdenesDelObjetivo'
import { ProgramarModal } from '@/components/planificacion/ProgramarModal'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { useCatalogos, useResponsables } from '@/hooks/useCatalogos'
import { useConsulta } from '@/hooks/useConsulta'
import { useTonoCriticidad } from '@/hooks/useTonoCriticidad'
import { ESTADO_SERVIDOR, ETIQUETA_FAMILIA, ETIQUETA_MODALIDAD, textoVentana } from '@/utils/etiquetas'
import { formatearDuracion, formatearFechaHora } from '@/utils/formato'
import { textoDeError } from '@/utils/errores'

type Dialogo = 'editar' | 'configurar' | 'ventanas' | 'baja' | 'programar' | null

/** Ficha del servidor (RF14, HU10). */
export function FichaServidorPage() {
  const tonoCriticidad = useTonoCriticidad()
  const { id } = useParams()
  const idServidor = Number(id)
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  // RF19: el responsable edita la ventana de sus servidores; si puede ver la
  // ficha es porque el servidor es suyo (el backend responde 404 si no).
  const puedeEditarVentana = tieneRol('ADMINISTRADOR', 'RESPONSABLE')
  const { avisar } = useAvisos()
  const [dialogo, setDialogo] = useState<Dialogo>(null)
  const [reactivando, setReactivando] = useState(false)
  const [versionOrdenes, setVersionOrdenes] = useState(0)

  const ficha = useConsulta(() => servidoresApi.ficha(idServidor), [idServidor])
  const catalogos = useCatalogos()
  const responsables = useResponsables(esAdmin)

  const s = ficha.datos

  function actualizar(nueva: FichaServidorRespuesta, mensaje: string) {
    ficha.reemplazar(nueva)
    setDialogo(null)
    avisar(mensaje)
  }

  async function reactivar() {
    setReactivando(true)
    try {
      actualizar(await servidoresApi.reactivar(idServidor), 'Servidor reactivado: complete su nueva configuración de mantenimiento.')
    } catch (e) {
      avisar(textoDeError(e), 'error')
    } finally {
      setReactivando(false)
    }
  }

  if (ficha.cargando && !s) return <Cargando texto="Cargando ficha del servidor…" />
  if (ficha.error && !s) {
    return (
      <div className="flex flex-col gap-4">
        <Link to="/servidores" className="inline-flex items-center gap-1 text-sm text-ink-soft hover:text-ink">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Volver al inventario
        </Link>
        <MensajeError error={ficha.error} onReintentar={ficha.recargar} />
      </div>
    )
  }
  if (!s) return null

  const estado = s.estado ? ESTADO_SERVIDOR[s.estado] : undefined
  const dadoDeBaja = s.estado === 'DADO_DE_BAJA'
  const criticidad = catalogos.datos?.criticidades.find((c) => c.id === s.criticidad?.id)

  return (
    <div className="flex flex-col gap-6">
      <Link to="/servidores" className="inline-flex items-center gap-1 self-start text-sm text-ink-soft hover:text-ink">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Volver al inventario
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="font-mono text-2xl font-semibold text-ink">{s.hostname}</h2>
          <p className="mt-1 text-sm text-ink-soft">{s.dns ?? s.direccionIp}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            {estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}
            <StatusPill tone={tonoCriticidad(s.criticidad?.prioridad)} label={`Criticidad ${s.criticidad?.nombre ?? ''}`} />
            <StatusPill tone="neutral" label={s.entorno?.nombre ?? ''} />
            {s.configuracion?.modalidadPlanificacion && (
              <StatusPill tone="neutral" label={`Modalidad: ${ETIQUETA_MODALIDAD[s.configuracion.modalidadPlanificacion]}`} />
            )}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {esAdmin && !dadoDeBaja && (
            <>
              <Boton icono={Pencil} onClick={() => setDialogo('editar')} disabled={!catalogos.datos}>
                Editar
              </Boton>
              <Boton icono={Wrench} variante="primario" onClick={() => setDialogo('configurar')}>
                {s.configuracion ? 'Configuración' : 'Configurar mantenimiento'}
              </Boton>
            </>
          )}
          {puedeEditarVentana && !dadoDeBaja && (
            <Boton icono={CalendarClock} onClick={() => setDialogo('ventanas')}>
              Ventana permisiva
            </Boton>
          )}
          {esAdmin && s.estado === 'ACTIVO' && (
            <Boton icono={CalendarPlus} onClick={() => setDialogo('programar')}>
              Programar
            </Boton>
          )}
          {esAdmin && !dadoDeBaja && !s.bajaPendiente && (
            <Boton icono={ArchiveX} variante="fantasma" onClick={() => setDialogo('baja')}>
              Dar de baja
            </Boton>
          )}
          {esAdmin && dadoDeBaja && (
            <Boton icono={RotateCcw} variante="primario" onClick={reactivar} cargando={reactivando}>
              Reactivar
            </Boton>
          )}
        </div>
      </div>

      {s.bajaPendiente && (
        <p className="rounded-lg border border-warning/30 bg-warning-soft px-4 py-3 text-sm text-warning">
          Baja solicitada el {formatearFechaHora(s.bajaPendiente.fechaSolicitud)}: se aplicará al finalizar el mantenimiento en
          curso. Motivo: {s.bajaPendiente.motivo}
        </p>
      )}
      {s.estado === 'PENDIENTE_DE_CONFIGURACION' && (
        <p className="rounded-lg border border-warning/30 bg-warning-soft px-4 py-3 text-sm text-warning">
          El servidor está pendiente de configuración y no genera mantenimientos hasta definir su configuración de mantenimiento.
        </p>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <Tarjeta titulo="Datos generales" className="lg:col-span-2">
          <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-3">
            <Dato etiqueta="IP" valor={s.direccionIp} mono />
            <Dato etiqueta="Datacenter" valor={s.datacenter} />
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

        <Tarjeta titulo="Configuración de mantenimiento">
          {s.configuracion ? (
            <dl className="grid gap-4">
              <Dato etiqueta="Frecuencia de revisión" valor={`Cada ${s.configuracion.frecuenciaRevisionDias} días`} />
              <Dato etiqueta="Frecuencia de mantenimiento" valor={`Cada ${s.configuracion.frecuenciaMantenimientoDias} días`} />
              <Dato
                etiqueta="Modalidad de planificación"
                valor={s.configuracion.modalidadPlanificacion ? ETIQUETA_MODALIDAD[s.configuracion.modalidadPlanificacion] : undefined}
              />
              <Dato etiqueta="Cuenta de servicio" valor={s.configuracion.usaCuentaPredeterminada ? 'Predeterminada del sistema' : `#${s.configuracion.idCuentaServicio}`} />
            </dl>
          ) : (
            <p className="text-sm text-ink-soft">Sin configuración de mantenimiento.</p>
          )}

          <h4 className="mb-2 mt-5 text-xs font-semibold uppercase tracking-wide text-ink-faint">Ventana permisiva</h4>
          {s.ventanas && s.ventanas.length > 0 ? (
            <ul className="flex flex-col gap-2">
              {s.ventanas.map((v, i) => (
                <li key={i} className="flex items-center justify-between rounded-lg bg-panel-muted px-3 py-2 text-sm">
                  <span className="text-ink">{textoVentana(v)}</span>
                  <span className="font-mono text-xs text-ink-faint">{formatearDuracion(v.duracionMinutos)}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-ink-soft">Sin ventana definida: el servidor no puede planificarse.</p>
          )}
        </Tarjeta>
      </div>

      <OrdenesDelObjetivo idServidor={idServidor} version={versionOrdenes} />

      {dialogo === 'programar' && (
        <ProgramarModal
          abierto
          objetivo={{ idServidor, nombre: s.hostname ?? '' }}
          onCerrar={() => setDialogo(null)}
          onGuardado={(orden) => {
            setDialogo(null)
            setVersionOrdenes((v) => v + 1)
            avisar(`Orden ${orden.resumen?.codigo} programada.`)
          }}
        />
      )}
      {dialogo === 'editar' && catalogos.datos && (
        <ServidorFormulario
          abierto
          servidor={s}
          catalogos={catalogos.datos}
          responsables={responsables.datos ?? []}
          onCerrar={() => setDialogo(null)}
          onGuardado={(nuevo) => actualizar(nuevo, 'Datos del servidor actualizados.')}
        />
      )}
      {dialogo === 'configurar' && (
        <ConfiguracionModal
          abierto
          titulo={`Configuración de mantenimiento de ${s.hostname}`}
          actual={s.configuracion}
          recomendadas={{ revision: criticidad?.frecuenciaRevisionDias, mantenimiento: criticidad?.frecuenciaMantenimientoDias }}
          onCerrar={() => setDialogo(null)}
          onGuardar={async (datos) => {
            actualizar(
              await servidoresApi.configurar(idServidor, {
                frecuenciaRevisionDias: datos.frecuenciaRevisionDias,
                frecuenciaMantenimientoDias: datos.frecuenciaMantenimientoDias,
                modalidadPlanificacion: datos.modalidadPlanificacion,
              }),
              'Configuración de mantenimiento guardada.',
            )
            // En modalidad automática el backend genera el primer ciclo antes de
            // responder (RF27): basta con recargar el historial.
            setVersionOrdenes((v) => v + 1)
          }}
        />
      )}
      {dialogo === 'ventanas' && (
        <VentanasModal
          abierto
          hostname={s.hostname ?? ''}
          actuales={s.ventanas ?? []}
          onCerrar={() => setDialogo(null)}
          onGuardar={async (ventanas) =>
            {
              actualizar(await servidoresApi.reemplazarVentanas(idServidor, ventanas), 'Ventana permisiva actualizada.')
              setVersionOrdenes((v) => v + 1)
            }
          }
        />
      )}
      {dialogo === 'baja' && (
        <BajaModal
          abierto
          hostname={s.hostname ?? ''}
          onCerrar={() => setDialogo(null)}
          onConfirmar={async (motivo) => {
            const resultado = await servidoresApi.darDeBaja(idServidor, { motivo })
            if (resultado.servidor) ficha.reemplazar(resultado.servidor)
            setDialogo(null)
            setVersionOrdenes((v) => v + 1)
            avisar(resultado.mensaje ?? 'Solicitud de baja registrada.', resultado.aplicada ? 'exito' : 'info')
          }}
        />
      )}
    </div>
  )
}
