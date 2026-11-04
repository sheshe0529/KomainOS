import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { CalendarPlus, Pencil, Power, PowerOff, Users, Wrench } from 'lucide-react'
import { gruposApi } from '@/api/inventario'
import type { FichaGrupoRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { BitacoraMantenimiento } from '@/components/automatizacion/BitacoraMantenimiento'
import { ConectividadGrupo } from '@/components/automatizacion/ConectividadGrupo'
import { EnlaceVolver, PageHeader } from '@/components/common/PageHeader'
import { Dato, Tarjeta } from '@/components/common/Tarjeta'
import { ConfiguracionMantenimientoTarjeta } from '@/components/inventario/ConfiguracionMantenimientoTarjeta'
import { ConfiguracionModal } from '@/components/inventario/ConfiguracionModal'
import { GrupoFormulario } from '@/components/inventario/GrupoFormulario'
import { IntegrantesModal } from '@/components/inventario/IntegrantesModal'
import { VentanaPermisivaTarjeta } from '@/components/inventario/VentanaPermisivaTarjeta'
import { ProgramarModal } from '@/components/planificacion/ProgramarModal'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { ENLACE_FILA } from '@/components/ui/estilos'
import { FilaNavegable } from '@/components/ui/FilaNavegable'
import { MensajeError } from '@/components/ui/MensajeError'
import { Paginacion } from '@/components/ui/Paginacion'
import { StatusPill } from '@/components/ui/StatusPill'
import { useCatalogos } from '@/hooks/useCatalogos'
import { useConsulta } from '@/hooks/useConsulta'
import { usePaginacionLocal } from '@/hooks/usePaginacion'
import { ESTADO_GRUPO, ESTADO_SERVIDOR } from '@/utils/etiquetas'
import { textoDeError } from '@/utils/errores'

type Dialogo = 'editar' | 'integrantes' | 'configurar' | 'programar' | null

/** Como la ficha del servidor: qué es el grupo y cómo se configura, y lo que se le ha hecho */
const PESTANAS = [
  { id: 'datos', etiqueta: 'Datos del grupo' },
  { id: 'historial', etiqueta: 'Historial de ejecución' },
] as const

type Pestana = (typeof PESTANAS)[number]['id']

export function FichaGrupoPage() {
  const { id } = useParams()
  const idGrupo = Number(id)
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  // La pestaña va en la dirección para volver a ella; se reemplaza la entrada para que «Volver» no recorra las pestañas
  const [parametros, setParametros] = useSearchParams()
  const pestana: Pestana = parametros.get('vista') === 'historial' ? 'historial' : 'datos'
  const { avisar } = useAvisos()
  const [dialogo, setDialogo] = useState<Dialogo>(null)
  const [cambiandoEstado, setCambiandoEstado] = useState(false)
  const [versionOrdenes, setVersionOrdenes] = useState(0)

  const ficha = useConsulta(() => gruposApi.ficha(idGrupo), [idGrupo])
  const catalogos = useCatalogos()
  const g = ficha.datos
  const integrantes = usePaginacionLocal(g?.integrantes ?? [], 'integrantes-grupo')

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
  if (ficha.error && !g) {
    return (
      <div className="flex flex-col gap-4">
        <EnlaceVolver a="/grupos" texto="Volver a grupos" />
        <MensajeError error={ficha.error} onReintentar={ficha.recargar} />
      </div>
    )
  }
  if (!g) return null

  const estado = g.estado ? ESTADO_GRUPO[g.estado] : undefined
  const inactivo = g.estado === 'INACTIVO'
  const criticidad = catalogos.datos?.criticidades.find((c) => c.id === g.criticidad?.id)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        className=""
        volver={{ a: '/grupos', texto: 'Volver a grupos', atras: true }}
        title={g.nombre}
        actions={
          esAdmin && (
            <>
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
            </>
          )
        }
      />

      <div role="tablist" aria-label="Secciones de la ficha" className="flex flex-wrap gap-2">
        {PESTANAS.map((p) => (
          <button
            key={p.id}
            role="tab"
            type="button"
            aria-selected={pestana === p.id}
            onClick={() => setParametros(p.id === 'datos' ? {} : { vista: p.id }, { replace: true })}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
              pestana === p.id ? 'bg-accent text-accent-ink' : 'bg-panel-muted text-ink-soft hover:text-ink'
            }`}
          >
            {p.etiqueta}
          </button>
        ))}
      </div>

      {pestana === 'datos' && (
        <>
          <div className="grid gap-6 lg:grid-cols-3">
            <Tarjeta titulo="Características del grupo" className="lg:col-span-2">
              <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-3">
                <Dato etiqueta="Estado" valor={estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />} />
                <Dato
                  etiqueta="Criticidad efectiva"
                  valor={g.criticidad ? `${g.criticidad.nombre} (la más alta de sus integrantes)` : 'Sin integrantes'}
                />
                <Dato etiqueta="Responsable" valor={g.responsable?.nombre} />
                <Dato etiqueta="Entorno" valor={g.entorno?.nombre} />
                <Dato etiqueta="Sistema operativo" valor={g.sistemaOperativo?.nombre} />
              </dl>
              {g.descripcion && (
                <div className="mt-4 border-t border-line pt-4">
                  <Dato etiqueta="Descripción" valor={g.descripcion} />
                </div>
              )}
            </Tarjeta>
            <ConfiguracionMantenimientoTarjeta configuracion={g.configuracion} conModoEjecucion />
          </div>

          <VentanaPermisivaTarjeta
            titulo="Ventana permisiva del grupo"
            descripcion="Intersección de las ventanas de todos sus integrantes."
            ventanas={g.ventanaEfectiva ?? []}
            vacio={g.integrantes?.length ? 'Los integrantes no comparten ningún intervalo: el grupo no puede planificarse.' : 'Sin integrantes.'}
          />

          <Tarjeta titulo={`Integrantes (${g.integrantes?.length ?? 0})`}>
            {g.integrantes && g.integrantes.length > 0 ? (
              <>
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
                      {integrantes.filas.map((s) => (
                        <FilaNavegable key={s.id} a={`/servidores/${s.id}`}>
                          <td className="py-2 pr-4">
                            <Link to={`/servidores/${s.id}`} className={`font-mono ${ENLACE_FILA}`}>
                              {s.hostname}
                            </Link>
                            <span className="ml-2 font-mono text-xs text-ink-faint">{s.direccionIp}</span>
                          </td>
                          <td className="py-2 pr-4 text-ink-soft">{s.versionSistemaOperativo?.nombre}</td>
                          <td className="py-2 pr-4 text-ink-soft">{s.criticidad?.nombre}</td>
                          <td className="py-2 pr-4">
                            {s.estado && <StatusPill tone={ESTADO_SERVIDOR[s.estado].tono} label={ESTADO_SERVIDOR[s.estado].etiqueta} />}
                          </td>
                        </FilaNavegable>
                      ))}
                    </tbody>
                  </table>
                </div>
                <div className="-mx-5 -mb-5 mt-2">
                  <Paginacion {...integrantes.controles} />
                </div>
              </>
            ) : (
              <p className="text-sm text-ink-soft">El grupo aún no tiene integrantes.</p>
            )}
          </Tarjeta>

          <ConectividadGrupo idGrupo={idGrupo} />
        </>
      )}

      {pestana === 'historial' && (
        <BitacoraMantenimiento idGrupo={idGrupo} integrantes={g.integrantes ?? []} version={versionOrdenes} />
      )}

      {dialogo === 'programar' && (
        <ProgramarModal
          abierto
          objetivo={{ idGrupo, nombre: g.nombre ?? '' }}
          onCerrar={() => setDialogo(null)}
          onGuardado={(orden) => {
            setDialogo(null)
            setVersionOrdenes((v) => v + 1)
            // La orden nueva se ve en la bitácora
            setParametros({ vista: 'historial' }, { replace: true })
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
          actuales={g.integrantes ?? []}
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
          familia={g.integrantes?.[0]?.familiaSistemaOperativo}
          onCerrar={() => setDialogo(null)}
          onGuardar={async (datos) => {
            actualizar(
              await gruposApi.configurar(idGrupo, {
                frecuenciaRevisionDias: datos.frecuenciaRevisionDias,
                frecuenciaMantenimientoDias: datos.frecuenciaMantenimientoDias,
                modalidadPlanificacion: datos.modalidadPlanificacion,
                modoEjecucion: datos.modoEjecucion ?? 'SECUENCIAL',
                idCuentaServicio: datos.idCuentaServicio,
                versionEsperada: g.configuracion?.fechaActualizacion,
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
