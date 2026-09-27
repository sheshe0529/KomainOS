import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, CalendarPlus, Pencil, Power, PowerOff, Users, Wrench } from 'lucide-react'
import { gruposApi } from '@/api/inventario'
import type { FichaGrupoRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { Dato, Tarjeta } from '@/components/common/Tarjeta'
import { ConfiguracionModal } from '@/components/inventario/ConfiguracionModal'
import { GrupoFormulario } from '@/components/inventario/GrupoFormulario'
import { IntegrantesModal } from '@/components/inventario/IntegrantesModal'
import { OrdenesDelObjetivo } from '@/components/planificacion/OrdenesDelObjetivo'
import { ProgramarModal } from '@/components/planificacion/ProgramarModal'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { useCatalogos } from '@/hooks/useCatalogos'
import { useConsulta } from '@/hooks/useConsulta'
import { useTonoCriticidad } from '@/hooks/useTonoCriticidad'
import { ESTADO_GRUPO, ESTADO_SERVIDOR, ETIQUETA_MODALIDAD, ETIQUETA_MODO, textoVentana } from '@/utils/etiquetas'
import { formatearDuracion } from '@/utils/formato'
import { textoDeError } from '@/utils/errores'

type Dialogo = 'editar' | 'integrantes' | 'configurar' | 'programar' | null

/** Detalle de un grupo de mantenimiento (HU15, RF21, RF76). */
export function FichaGrupoPage() {
  const tonoCriticidad = useTonoCriticidad()
  const { id } = useParams()
  const idGrupo = Number(id)
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const { avisar } = useAvisos()
  const [dialogo, setDialogo] = useState<Dialogo>(null)
  const [cambiandoEstado, setCambiandoEstado] = useState(false)
  const [versionOrdenes, setVersionOrdenes] = useState(0)

  const ficha = useConsulta(() => gruposApi.ficha(idGrupo), [idGrupo])
  const catalogos = useCatalogos()
  const g = ficha.datos

  function actualizar(nueva: FichaGrupoRespuesta, mensaje: string) {
    ficha.reemplazar(nueva)
    setDialogo(null)
    avisar(mensaje)
  }

  async function cambiarEstado(activar: boolean) {
    setCambiandoEstado(true)
    try {
      actualizar(await gruposApi.cambiarEstado(idGrupo, activar), activar ? 'Grupo activado.' : 'Grupo desactivado.')
    } catch (e) {
      avisar(textoDeError(e), 'error')
    } finally {
      setCambiandoEstado(false)
    }
  }

  if (ficha.cargando && !g) return <Cargando texto="Cargando grupo…" />
  if (ficha.error && !g) return <MensajeError error={ficha.error} onReintentar={ficha.recargar} />
  if (!g) return null

  const estado = g.estado ? ESTADO_GRUPO[g.estado] : undefined
  const inactivo = g.estado === 'INACTIVO'
  const criticidad = catalogos.datos?.criticidades.find((c) => c.id === g.criticidad?.id)

  return (
    <div className="flex flex-col gap-6">
      <Link to="/grupos" className="inline-flex items-center gap-1 self-start text-sm text-ink-soft hover:text-ink">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Volver a grupos
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-2xl font-semibold text-ink">{g.nombre}</h2>
          {g.descripcion && <p className="mt-1 text-sm text-ink-soft">{g.descripcion}</p>}
          <div className="mt-3 flex flex-wrap gap-2">
            {estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}
            {g.criticidad && <StatusPill tone={tonoCriticidad(g.criticidad.prioridad)} label={`Criticidad ${g.criticidad.nombre}`} />}
            {g.configuracion?.modalidadPlanificacion && (
              <StatusPill tone="neutral" label={`Modalidad: ${ETIQUETA_MODALIDAD[g.configuracion.modalidadPlanificacion]}`} />
            )}
          </div>
        </div>
        {esAdmin && (
          <div className="flex flex-wrap gap-2">
            <Boton icono={Pencil} onClick={() => setDialogo('editar')}>
              Editar
            </Boton>
            {!inactivo && (
              <>
                <Boton icono={Users} onClick={() => setDialogo('integrantes')}>
                  Integrantes
                </Boton>
                <Boton icono={Wrench} variante="primario" onClick={() => setDialogo('configurar')}>
                  {g.configuracion ? 'Configuración' : 'Configurar mantenimiento'}
                </Boton>
              </>
            )}
            {g.estado === 'ACTIVO' && (
              <Boton icono={CalendarPlus} onClick={() => setDialogo('programar')}>
                Programar
              </Boton>
            )}
            <Boton
              icono={inactivo ? Power : PowerOff}
              variante="fantasma"
              cargando={cambiandoEstado}
              onClick={() => cambiarEstado(inactivo)}
            >
              {inactivo ? 'Activar' : 'Desactivar'}
            </Boton>
          </div>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Tarjeta titulo="Características comunes" className="lg:col-span-2">
          <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-3">
            <Dato etiqueta="Responsable" valor={g.responsable?.nombre} />
            <Dato etiqueta="Entorno" valor={g.entorno?.nombre} />
            <Dato etiqueta="Sistema operativo" valor={g.sistemaOperativo?.nombre} />
            <Dato
              etiqueta="Criticidad efectiva"
              valor={g.criticidad ? `${g.criticidad.nombre} (la más alta de sus integrantes)` : 'Sin integrantes'}
            />
            <Dato
              etiqueta="Modo de ejecución"
              valor={g.configuracion?.modoEjecucion ? ETIQUETA_MODO[g.configuracion.modoEjecucion] : undefined}
            />
            <Dato
              etiqueta="Frecuencias"
              valor={
                g.configuracion
                  ? `Revisión cada ${g.configuracion.frecuenciaRevisionDias} d · mantenimiento cada ${g.configuracion.frecuenciaMantenimientoDias} d`
                  : 'Sin configuración'
              }
            />
          </dl>
        </Tarjeta>

        <Tarjeta titulo="Ventana permisiva del grupo">
          <p className="mb-3 text-xs text-ink-soft">Intersección de las ventanas de todos sus integrantes.</p>
          {g.ventanaEfectiva && g.ventanaEfectiva.length > 0 ? (
            <ul className="flex flex-col gap-2">
              {g.ventanaEfectiva.map((v, i) => (
                <li key={i} className="flex items-center justify-between rounded-lg bg-panel-muted px-3 py-2 text-sm">
                  <span className="text-ink">{textoVentana(v)}</span>
                  <span className="font-mono text-xs text-ink-faint">{formatearDuracion(v.duracionMinutos)}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-ink-soft">
              {g.integrantes?.length ? 'Los integrantes no comparten ningún intervalo: el grupo no puede planificarse.' : 'Sin integrantes.'}
            </p>
          )}
        </Tarjeta>
      </div>

      <Tarjeta titulo={`Integrantes (${g.integrantes?.length ?? 0})`}>
        {g.integrantes && g.integrantes.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead>
                <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <th className="py-2 pr-4 font-medium">Servidor</th>
                  <th className="py-2 pr-4 font-medium">Sistema operativo</th>
                  <th className="py-2 pr-4 font-medium">Criticidad</th>
                  <th className="py-2 pr-4 font-medium">Estado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {g.integrantes.map((s) => (
                  <tr key={s.id}>
                    <td className="py-2 pr-4">
                      <Link to={`/servidores/${s.id}`} className="font-mono text-ink hover:text-accent">
                        {s.hostname}
                      </Link>
                      <span className="ml-2 font-mono text-xs text-ink-faint">{s.direccionIp}</span>
                    </td>
                    <td className="py-2 pr-4 text-ink-soft">{s.versionSistemaOperativo?.nombre}</td>
                    <td className="py-2 pr-4">
                      <StatusPill tone={tonoCriticidad(s.criticidad?.prioridad)} label={s.criticidad?.nombre ?? ''} />
                    </td>
                    <td className="py-2 pr-4">
                      {s.estado && <StatusPill tone={ESTADO_SERVIDOR[s.estado].tono} label={ESTADO_SERVIDOR[s.estado].etiqueta} />}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-ink-soft">El grupo aún no tiene integrantes.</p>
        )}
      </Tarjeta>

      <OrdenesDelObjetivo idGrupo={idGrupo} version={versionOrdenes} />

      {dialogo === 'programar' && (
        <ProgramarModal
          abierto
          objetivo={{ idGrupo, nombre: g.nombre ?? '' }}
          onCerrar={() => setDialogo(null)}
          onGuardado={(orden) => {
            setDialogo(null)
            setVersionOrdenes((v) => v + 1)
            avisar(`Orden ${orden.resumen?.codigo} programada.`)
          }}
        />
      )}
      {dialogo === 'editar' && (
        <GrupoFormulario
          abierto
          grupo={g}
          onCerrar={() => setDialogo(null)}
          onGuardado={(nuevo) => actualizar(nuevo, 'Grupo actualizado.')}
        />
      )}
      {dialogo === 'integrantes' && (
        <IntegrantesModal
          abierto
          nombreGrupo={g.nombre ?? ''}
          actuales={(g.integrantes ?? []).map((s) => s.id!)}
          onCerrar={() => setDialogo(null)}
          onGuardar={async (ids) => actualizar(await gruposApi.reemplazarIntegrantes(idGrupo, ids), 'Integrantes actualizados.')}
        />
      )}
      {dialogo === 'configurar' && (
        <ConfiguracionModal
          abierto
          conModoEjecucion
          titulo={`Configuración de mantenimiento de ${g.nombre}`}
          actual={g.configuracion}
          recomendadas={{ revision: criticidad?.frecuenciaRevisionDias, mantenimiento: criticidad?.frecuenciaMantenimientoDias }}
          onCerrar={() => setDialogo(null)}
          onGuardar={async (datos) => {
            actualizar(
              await gruposApi.configurar(idGrupo, {
                frecuenciaRevisionDias: datos.frecuenciaRevisionDias,
                frecuenciaMantenimientoDias: datos.frecuenciaMantenimientoDias,
                modalidadPlanificacion: datos.modalidadPlanificacion,
                modoEjecucion: datos.modoEjecucion ?? 'SECUENCIAL',
              }),
              'Configuración del grupo guardada.',
            )
            setVersionOrdenes((v) => v + 1)
          }}
        />
      )}
    </div>
  )
}
