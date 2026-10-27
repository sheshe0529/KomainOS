import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ChevronRight, Plus } from 'lucide-react'
import { scriptsApi } from '@/api/automatizacion'
import type { TipoScript } from '@/api/dominio'
import type { ImplementacionResumen } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { ScriptFormulario } from '@/components/automatizacion/ScriptFormulario'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { CampoBusqueda } from '@/components/ui/CampoBusqueda'
import { Selector } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Paginacion } from '@/components/ui/Paginacion'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { useTextoDiferido } from '@/hooks/useTextoDiferido'
import { ETIQUETA_TIPO_SCRIPT } from '@/utils/automatizacion'
import { formatearFecha } from '@/utils/formato'

const TIPOS: { valor: TipoScript | ''; etiqueta: string }[] = [
  { valor: '', etiqueta: 'Todos' },
  { valor: 'ANALISIS', etiqueta: 'Análisis' },
  { valor: 'MANTENIMIENTO', etiqueta: 'Mantenimiento' },
]

/** Una implementación sin versión habilitada no puede fijarse en una MOP: se marca para que se note */
function Compatibilidad({ implementacion: i }: { implementacion: ImplementacionResumen }) {
  const sinVigente = !i.versionVigente
  return (
    <span
      title={sinVigente ? 'Todas sus versiones están deshabilitadas' : `${i.totalVersiones} versiones publicadas`}
      className={`inline-flex items-center gap-1.5 rounded-md border px-2 py-0.5 text-xs ${
        sinVigente ? 'border-warning/40 bg-warning-soft text-warning' : 'border-line bg-panel-muted text-ink'
      }`}
    >
      {i.sistemaOperativo}
      <span className="font-mono text-ink-soft">{sinVigente ? 'sin versión' : `v${i.versionVigente}`}</span>
    </span>
  )
}

/** Catálogo de scripts (RF22): compatibilidad por sistema operativo y versión vigente de cada implementación */
export function ScriptsPage() {
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const navigate = useNavigate()
  const { avisar } = useAvisos()

  const [busqueda, setBusqueda] = useState('')
  const texto = useTextoDiferido(busqueda)
  const [tipo, setTipo] = useState<TipoScript | ''>('')
  const [estado, setEstado] = useState('true')
  const [pagina, setPagina] = useState(0)
  const [creando, setCreando] = useState(false)

  const scripts = useConsulta(
    () => scriptsApi.listar({ texto, tipo, activo: estado === '' ? undefined : estado === 'true' }, pagina),
    [texto, tipo, estado, pagina],
  )
  const filas = scripts.datos?.contenido ?? []
  const conFiltros = !!(texto || tipo || estado !== 'true')
  const ruta = (id?: number) => `/automatizacion/scripts/${id}`

  return (
    <>
      <PageHeader
        title="Scripts"
        description="Análisis que recogen métricas y mantenimientos que las corrigen, escritos para cada sistema operativo."
        actions={
          esAdmin && (
            <Boton variante="primario" icono={Plus} onClick={() => setCreando(true)}>
              Nuevo script
            </Boton>
          )
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <CampoBusqueda
          valor={busqueda}
          onCambiar={(t) => {
            setBusqueda(t)
            setPagina(0)
          }}
          placeholder="Buscar por nombre o descripción"
          aria-label="Buscar scripts"
        />
        <div role="radiogroup" aria-label="Tipo de script" className="flex gap-1 rounded-lg bg-panel-muted p-1">
          {TIPOS.map((t) => (
            <button
              key={t.valor}
              type="button"
              role="radio"
              aria-checked={tipo === t.valor}
              onClick={() => {
                setTipo(t.valor)
                setPagina(0)
              }}
              className={`rounded-md px-3 py-1 text-sm transition-colors ${
                tipo === t.valor ? 'bg-panel font-medium text-ink shadow-sm' : 'text-ink-soft hover:text-ink'
              }`}
            >
              {t.etiqueta}
            </button>
          ))}
        </div>
        <Selector
          aria-label="Filtrar por estado"
          className="!w-auto"
          value={estado}
          onChange={(e) => {
            setEstado(e.target.value)
            setPagina(0)
          }}
        >
          <option value="true">Activos</option>
          <option value="false">Inactivos</option>
          <option value="">Activos e inactivos</option>
        </Selector>
      </div>

      <MensajeError error={scripts.error} onReintentar={scripts.recargar} />

      {scripts.cargando && !scripts.datos ? (
        <Cargando />
      ) : filas.length === 0 ? (
        <div className="rounded-xl border border-line bg-panel px-5 py-10 text-center text-sm text-ink-soft">
          {conFiltros ? (
            'Ningún script coincide con los filtros.'
          ) : (
            <>
              Aún no hay scripts.{' '}
              {esAdmin && 'Cree el primero o cargue los de demostración desde la sala de operaciones.'}
            </>
          )}
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border border-line bg-panel">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead>
                <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <th className="px-5 py-3 font-medium">Script</th>
                  <th className="px-3 py-3 font-medium">Tipo</th>
                  <th className="px-3 py-3 font-medium">Sistemas operativos · versión vigente</th>
                  <th className="px-3 py-3 font-medium">Última publicación</th>
                  <th className="px-3 py-3 font-medium">Estado</th>
                  <th className="w-10 px-3 py-3">
                    <span className="sr-only">Detalle</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {filas.map((s) => {
                  const implementaciones = s.implementaciones ?? []
                  const ultima = implementaciones
                    .map((i) => i.fechaUltimaVersion)
                    .filter(Boolean)
                    .sort()
                    .at(-1)
                  return (
                    <tr
                      key={s.id}
                      onClick={() => navigate(ruta(s.id))}
                      className={`cursor-pointer hover:bg-panel-muted ${s.activo ? '' : 'text-ink-faint'}`}
                    >
                      <td className="max-w-sm px-5 py-3">
                        <Link
                          to={ruta(s.id)}
                          onClick={(e) => e.stopPropagation()}
                          className="font-medium text-ink hover:text-accent hover:underline"
                        >
                          {s.nombre}
                        </Link>
                        {s.descripcion && <p className="line-clamp-2 text-xs text-ink-soft">{s.descripcion}</p>}
                      </td>
                      <td className="px-3 py-3 text-ink-soft">{ETIQUETA_TIPO_SCRIPT[s.tipo ?? 'MANTENIMIENTO']}</td>
                      <td className="px-3 py-3">
                        {implementaciones.length === 0 ? (
                          <StatusPill tone="warning" label="Sin implementaciones" />
                        ) : (
                          <div className="flex flex-wrap gap-1.5">
                            {implementaciones.map((i) => (
                              <Compatibilidad key={i.id} implementacion={i} />
                            ))}
                          </div>
                        )}
                      </td>
                      <td className="px-3 py-3 text-ink-soft">{formatearFecha(ultima)}</td>
                      <td className="px-3 py-3">
                        <StatusPill tone={s.activo ? 'success' : 'neutral'} label={s.activo ? 'Activo' : 'Inactivo'} />
                      </td>
                      <td className="px-3 py-3 text-ink-faint">
                        <ChevronRight className="h-4 w-4" aria-hidden="true" />
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <Paginacion
            pagina={pagina}
            totalPaginas={scripts.datos?.totalPaginas ?? 0}
            totalElementos={scripts.datos?.totalElementos ?? 0}
            onCambiar={setPagina}
          />
        </div>
      )}

      {creando && (
        <ScriptFormulario
          onCerrar={() => setCreando(false)}
          onGuardado={(s) => {
            setCreando(false)
            avisar(`Script «${s.nombre}» creado. Agregue ahora su código para un sistema operativo.`)
            navigate(ruta(s.id))
          }}
        />
      )}
    </>
  )
}
