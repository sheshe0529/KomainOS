import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ChevronRight, Plus } from 'lucide-react'
import { scriptsApi } from '@/api/automatizacion'
import type { TipoScript } from '@/api/dominio'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { ScriptFormulario } from '@/components/automatizacion/ScriptFormulario'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Selector } from '@/components/ui/Campo'
import { CampoBusqueda } from '@/components/ui/CampoBusqueda'
import { Cargando } from '@/components/ui/Cargando'
import { ENLACE_FILA } from '@/components/ui/estilos'
import { FilaNavegable } from '@/components/ui/FilaNavegable'
import { MensajeError } from '@/components/ui/MensajeError'
import { Paginacion } from '@/components/ui/Paginacion'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { usePaginacion } from '@/hooks/usePaginacion'
import { useTextoDiferido } from '@/hooks/useTextoDiferido'
import { ETIQUETA_TIPO_SCRIPT } from '@/utils/automatizacion'
import { formatearFecha } from '@/utils/formato'

const TIPOS: { valor: TipoScript | ''; etiqueta: string }[] = [
  { valor: '', etiqueta: 'Todos' },
  { valor: 'ANALISIS', etiqueta: 'Análisis' },
  { valor: 'MANTENIMIENTO', etiqueta: 'Mantenimiento' },
]

const LARGO_DESCRIPCION = 100

/** Los primeros caracteres de la descripción; el texto completo queda en el título de la celda */
function resumirDescripcion(texto?: string): string {
  if (!texto) return '—'
  return texto.length > LARGO_DESCRIPCION ? `${texto.slice(0, LARGO_DESCRIPCION).trimEnd()}…` : texto
}

/** Catálogo de scripts (RF22): qué hace cada uno y quién lo registró; la compatibilidad se ve en su ficha */
export function ScriptsPage() {
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const navigate = useNavigate()
  const { avisar } = useAvisos()

  const [busqueda, setBusqueda] = useState('')
  const texto = useTextoDiferido(busqueda)
  const [tipo, setTipo] = useState<TipoScript | ''>('')
  const [estado, setEstado] = useState('true')
  const { pagina, tamano, setPagina, setTamano } = usePaginacion('scripts')
  const [creando, setCreando] = useState(false)

  const scripts = useConsulta(
    () => scriptsApi.listar({ texto, tipo, activo: estado === '' ? undefined : estado === 'true' }, pagina, tamano),
    [texto, tipo, estado, pagina, tamano],
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
              {esAdmin && 'Cree el primero: el editor ofrece plantillas de análisis y de mantenimiento.'}
            </>
          )}
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border border-line bg-panel">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px] text-left text-sm">
              <thead>
                <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <th className="px-5 py-3 font-medium">Script</th>
                  <th className="px-3 py-3 font-medium">Tipo</th>
                  <th className="px-3 py-3 font-medium">Descripción</th>
                  <th className="px-3 py-3 font-medium">Creado por</th>
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
                    <FilaNavegable key={s.id} a={ruta(s.id)} className={s.activo ? '' : 'text-ink-faint'}>
                      <td className="px-5 py-3">
                        <Link to={ruta(s.id)} className={ENLACE_FILA}>
                          {s.nombre}
                        </Link>
                        {implementaciones.length === 0 && (
                          <span className="mt-0.5 block text-xs text-warning">Sin código todavía</span>
                        )}
                      </td>
                      <td className="px-3 py-3 whitespace-nowrap text-ink-soft">{ETIQUETA_TIPO_SCRIPT[s.tipo ?? 'MANTENIMIENTO']}</td>
                      <td className="max-w-md px-3 py-3 text-ink-soft" title={s.descripcion}>
                        {resumirDescripcion(s.descripcion)}
                      </td>
                      <td className="px-3 py-3 whitespace-nowrap text-ink-soft">{s.creadoPor ?? '—'}</td>
                      <td className="px-3 py-3 whitespace-nowrap text-ink-soft">{formatearFecha(ultima)}</td>
                      <td className="px-3 py-3">
                        <StatusPill tone={s.activo ? 'success' : 'neutral'} label={s.activo ? 'Activo' : 'Inactivo'} />
                      </td>
                      <td className="px-3 py-3 text-ink-faint">
                        <ChevronRight className="h-4 w-4" aria-hidden="true" />
                      </td>
                    </FilaNavegable>
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
            tamano={tamano}
            onTamano={setTamano}
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
