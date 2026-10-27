import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Pencil, Plus } from 'lucide-react'
import { catalogosApi } from '@/api/catalogos'
import { scriptsApi, tareasApi } from '@/api/automatizacion'
import { umbralesApi } from '@/api/umbrales'
import type { TareaRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { TareaFormulario } from '@/components/automatizacion/TareaFormulario'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Paginacion } from '@/components/ui/Paginacion'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { AFECTACION } from '@/utils/automatizacion'
import { formatearDuracion } from '@/utils/formato'

/** Tareas de mantenimiento (RF23, RF24): qué script corre, cuándo se activa y cuánto afecta al servicio */
export function TareasPage() {
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const { avisar } = useAvisos()

  const [pagina, setPagina] = useState(0)
  const [editando, setEditando] = useState<TareaRespuesta | 'nueva'>()
  const tareas = useConsulta(() => tareasApi.listar(pagina), [pagina])
  const scripts = useConsulta(() => scriptsApi.todos(), [])
  const umbrales = useConsulta(() => umbralesApi.umbrales(), [])
  const parametros = useConsulta(() => catalogosApi.parametros(), [])

  const nombresUmbral = new Map((umbrales.datos ?? []).map((u) => [u.id, u.nombre]))
  const filas = tareas.datos?.contenido ?? []
  const sinScriptsMantenimiento = scripts.datos && !scripts.datos.some((s) => s.tipo === 'MANTENIMIENTO')

  return (
    <>
      <PageHeader
        title="Tareas de mantenimiento"
        description="Cada tarea ejecuta un script cuando se activa alguno de sus umbrales; su afectación decide quién autoriza."
        actions={
          esAdmin && (
            <Boton
              variante="primario"
              icono={Plus}
              disabled={!scripts.datos || !umbrales.datos}
              onClick={() => setEditando('nueva')}
            >
              Nueva tarea
            </Boton>
          )
        }
      />

      {esAdmin && sinScriptsMantenimiento && (
        <p className="mb-4 rounded-lg bg-warning-soft px-4 py-3 text-sm text-warning">
          No hay scripts de mantenimiento.{' '}
          <Link to="/automatizacion/scripts" className="font-medium underline">
            Cree uno
          </Link>{' '}
          antes de registrar tareas.
        </p>
      )}

      <MensajeError error={tareas.error ?? scripts.error ?? umbrales.error} onReintentar={tareas.recargar} />

      {tareas.cargando && !tareas.datos ? (
        <Cargando />
      ) : filas.length === 0 ? (
        <p className="rounded-xl border border-line bg-panel px-5 py-10 text-center text-sm text-ink-soft">
          Sin tareas registradas.
        </p>
      ) : (
        <div className="overflow-hidden rounded-xl border border-line bg-panel">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[880px] text-left text-sm">
              <thead>
                <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <th className="px-5 py-3 font-medium">Tarea</th>
                  <th className="px-3 py-3 font-medium">Script · compatible con</th>
                  <th className="px-3 py-3 font-medium">Afectación</th>
                  <th className="px-3 py-3 text-right font-medium">Prioridad</th>
                  <th className="px-3 py-3 font-medium">Duración</th>
                  <th className="px-3 py-3 font-medium">Se activa con</th>
                  <th className="px-3 py-3 font-medium">Estado</th>
                  {esAdmin && (
                    <th className="w-12 px-3 py-3">
                      <span className="sr-only">Editar</span>
                    </th>
                  )}
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {filas.map((t) => {
                  const afectacion = AFECTACION[t.nivelAfectacion ?? 'SIN_AFECTACION']
                  const nombres = (t.idsUmbrales ?? []).map((id) => nombresUmbral.get(id) ?? `#${id}`)
                  return (
                    <tr key={t.id} className={`align-top ${t.activo ? '' : 'text-ink-faint'}`}>
                      <td className="max-w-xs px-5 py-3">
                        <span className="font-medium text-ink">{t.nombre}</span>
                        {t.descripcion && <p className="line-clamp-2 text-xs text-ink-soft">{t.descripcion}</p>}
                      </td>
                      <td className="px-3 py-3">
                        <Link to={`/automatizacion/scripts/${t.idScript}`} className="text-accent hover:underline">
                          {t.script}
                        </Link>
                        {!t.scriptActivo && <span className="ml-2 text-xs text-warning">inactivo</span>}
                        <div className="mt-1 flex flex-wrap gap-1">
                          {(t.sistemasCompatibles ?? []).length === 0 ? (
                            <span className="text-xs text-warning">Sin versión habilitada</span>
                          ) : (
                            t.sistemasCompatibles?.map((so) => (
                              <span key={so} className="rounded bg-panel-muted px-1.5 py-0.5 text-xs text-ink-soft">
                                {so}
                              </span>
                            ))
                          )}
                        </div>
                      </td>
                      <td className="px-3 py-3">
                        <StatusPill tone={afectacion.tono} label={afectacion.etiqueta} />
                      </td>
                      <td className="px-3 py-3 text-right font-mono tabular-nums">{t.prioridad}</td>
                      <td className="px-3 py-3 whitespace-nowrap">{formatearDuracion(t.duracionEstimadaMinutos)}</td>
                      <td className="max-w-[14rem] px-3 py-3 text-xs text-ink-soft" title={nombres.join('\n')}>
                        {nombres.length === 0 ? (
                          <span className="text-warning">Ningún umbral</span>
                        ) : (
                          <>
                            {nombres.slice(0, 2).join(', ')}
                            {nombres.length > 2 && ` y ${nombres.length - 2} más`}
                          </>
                        )}
                      </td>
                      <td className="px-3 py-3">
                        <StatusPill tone={t.activo ? 'success' : 'neutral'} label={t.activo ? 'Activa' : 'Inactiva'} />
                      </td>
                      {esAdmin && (
                        <td className="px-3 py-3">
                          <BotonIcono
                            icono={Pencil}
                            etiqueta={`Editar ${t.nombre}`}
                            disabled={!scripts.datos || !umbrales.datos}
                            onClick={() => setEditando(t)}
                          />
                        </td>
                      )}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <Paginacion
            pagina={pagina}
            totalPaginas={tareas.datos?.totalPaginas ?? 0}
            totalElementos={tareas.datos?.totalElementos ?? 0}
            onCambiar={setPagina}
          />
        </div>
      )}

      {editando && scripts.datos && umbrales.datos && (
        <TareaFormulario
          tarea={editando === 'nueva' ? undefined : editando}
          scripts={scripts.datos}
          umbrales={umbrales.datos}
          maxDuracionMinutos={parametros.datos?.maxDuracionTareaMinutos}
          onCerrar={() => setEditando(undefined)}
          onGuardado={(t) => {
            setEditando(undefined)
            avisar(`Tarea «${t.nombre}» guardada.`)
            tareas.recargar()
          }}
        />
      )}
    </>
  )
}
