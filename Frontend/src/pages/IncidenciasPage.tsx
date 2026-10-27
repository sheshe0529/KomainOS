import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ChevronRight } from 'lucide-react'
import { incidenciasApi } from '@/api/automatizacion'
import type { EstadoIncidencia, OrigenIncidencia } from '@/api/dominio'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { Dato } from '@/components/common/Tarjeta'
import { SalidaTerminal } from '@/components/automatizacion/SalidaTerminal'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Selector } from '@/components/ui/Campo'
import { CampoBusqueda } from '@/components/ui/CampoBusqueda'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { Paginacion } from '@/components/ui/Paginacion'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { useTextoDiferido } from '@/hooks/useTextoDiferido'
import { ESTADO_INCIDENCIA, ORIGEN_INCIDENCIA } from '@/utils/automatizacion'
import { errorDeCampo } from '@/utils/errores'
import { formatearFechaHora } from '@/utils/formato'

const codigo = (id?: number) => `INC-${String(id ?? 0).padStart(4, '0')}`

interface DetalleIncidenciaProps {
  id: number
  onCerrar: () => void
  onGuardado: (mensaje: string) => void
}

/** RF61: el cierre exige diagnóstico y resultado; al cerrar la última, la orden continúa a su cierre */
function DetalleIncidencia({ id, onCerrar, onGuardado }: DetalleIncidenciaProps) {
  const { tieneRol } = useSesion()
  const incidencia = useConsulta(() => incidenciasApi.obtener(id), [id])
  const [diagnostico, setDiagnostico] = useState('')
  const [acciones, setAcciones] = useState('')
  const [resultado, setResultado] = useState('')
  const [cerrar, setCerrar] = useState(false)
  const [error, setError] = useState<unknown>()
  const [guardando, setGuardando] = useState(false)

  const i = incidencia.datos
  const editable = tieneRol('ADMINISTRADOR') && !!i && i.estado !== 'CERRADA'

  useEffect(() => {
    if (!i) return
    setDiagnostico(i.diagnostico ?? '')
    setAcciones(i.acciones ?? '')
    setResultado(i.resultado ?? '')
  }, [i])

  async function guardar() {
    setGuardando(true)
    setError(undefined)
    try {
      await incidenciasApi.atender(id, { diagnostico, acciones, resultado, cerrar })
      onGuardado(cerrar ? `${codigo(id)} cerrada.` : `Atención de ${codigo(id)} registrada.`)
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      ancho="xl"
      titulo={`Incidencia ${codigo(id)}`}
      descripcion={i?.origen ? ORIGEN_INCIDENCIA[i.origen] : undefined}
      onCerrar={() => !guardando && onCerrar()}
      pie={
        <>
          <Boton disabled={guardando} onClick={onCerrar}>
            {editable ? 'Cancelar' : 'Cerrar'}
          </Boton>
          {editable && (
            <Boton
              variante="primario"
              cargando={guardando}
              disabled={!diagnostico.trim() || !resultado.trim()}
              onClick={guardar}
            >
              {cerrar ? 'Cerrar incidencia' : 'Guardar atención'}
            </Boton>
          )}
        </>
      }
    >
      <MensajeError error={incidencia.error} onReintentar={incidencia.recargar} />
      {incidencia.cargando && !i && <Cargando />}
      {i && (
        <div className="flex flex-col gap-5">
          <div className="flex flex-wrap items-center gap-2">
            {i.estado && <StatusPill tone={ESTADO_INCIDENCIA[i.estado].tono} label={ESTADO_INCIDENCIA[i.estado].etiqueta} />}
          </div>
          <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Dato
              etiqueta="Orden"
              valor={
                <Link to={`/ordenes/${i.idOrden}`} className="font-mono text-accent hover:underline">
                  {i.orden}
                </Link>
              }
            />
            <Dato
              etiqueta="Servidor"
              valor={
                <Link to={`/servidores/${i.idServidor}`} className="font-mono text-accent hover:underline">
                  {i.servidor}
                </Link>
              }
            />
            <Dato etiqueta="Abierta" valor={formatearFechaHora(i.fechaApertura)} />
            <Dato etiqueta={i.fechaCierre ? 'Cerrada' : 'Atendida por'} valor={i.fechaCierre ? formatearFechaHora(i.fechaCierre) : i.atendidaPor} />
          </dl>

          {i.error && (
            <div>
              <h4 className="mb-2 text-sm font-medium text-ink">Error registrado</h4>
              <SalidaTerminal error={i.error} nombre={`incidencia_${i.id}`} />
            </div>
          )}

          {editable ? (
            <div className="flex flex-col gap-4">
              <MensajeError error={error} />
              <Campo etiqueta="Diagnóstico" obligatorio error={errorDeCampo(error, 'diagnostico')} ayuda="Causa identificada del fallo.">
                <AreaTexto rows={3} value={diagnostico} maxLength={10000} onChange={(e) => setDiagnostico(e.target.value)} />
              </Campo>
              <Campo etiqueta="Acciones realizadas" error={errorDeCampo(error, 'acciones')}>
                <AreaTexto rows={3} value={acciones} maxLength={10000} onChange={(e) => setAcciones(e.target.value)} />
              </Campo>
              <Campo etiqueta="Resultado" obligatorio error={errorDeCampo(error, 'resultado')}>
                <AreaTexto rows={2} value={resultado} maxLength={1000} onChange={(e) => setResultado(e.target.value)} />
              </Campo>
              <label className="flex items-start gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={cerrar}
                  onChange={(e) => setCerrar(e.target.checked)}
                  className="mt-0.5 h-4 w-4 accent-[var(--color-accent)]"
                />
                <span>
                  <span className="font-medium text-ink">Cerrar la incidencia</span>
                  <span className="block text-xs text-ink-soft">
                    Las tareas pendientes no se reanudan: al cerrar la última incidencia, la orden pasa a su cierre.
                  </span>
                </span>
              </label>
            </div>
          ) : (
            <dl className="grid gap-4">
              <Dato etiqueta="Diagnóstico" valor={i.diagnostico} />
              <Dato etiqueta="Acciones realizadas" valor={i.acciones} />
              <Dato etiqueta="Resultado" valor={i.resultado} />
            </dl>
          )}
        </div>
      )}
    </Modal>
  )
}

/** Gestión de incidencias (RF61, HU28): fallas de ejecución, tiempo excedido y validaciones no conformes */
export function IncidenciasPage() {
  const { avisar } = useAvisos()
  const [busquedaUrl, setBusquedaUrl] = useSearchParams()
  const [busqueda, setBusqueda] = useState('')
  const texto = useTextoDiferido(busqueda)
  const [estado, setEstado] = useState<EstadoIncidencia | ''>('')
  const [origen, setOrigen] = useState<OrigenIncidencia | ''>('')
  const [pagina, setPagina] = useState(0)

  const lista = useConsulta(
    () => incidenciasApi.listar({ estado, origen, texto }, pagina),
    [estado, origen, texto, pagina],
  )
  const filas = lista.datos?.contenido ?? []
  const abierta = Number(busquedaUrl.get('incidencia')) || undefined

  const abrir = (id?: number) => {
    const p = new URLSearchParams(busquedaUrl)
    if (id) p.set('incidencia', String(id))
    else p.delete('incidencia')
    setBusquedaUrl(p, { replace: !id })
  }

  return (
    <>
      <PageHeader
        title="Incidencias"
        description="Fallas detectadas al ejecutar o validar un mantenimiento. La orden no se cierra hasta atenderlas."
      />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <CampoBusqueda
          valor={busqueda}
          onCambiar={(t) => {
            setBusqueda(t)
            setPagina(0)
          }}
          placeholder="Buscar por servidor, orden o error"
          aria-label="Buscar incidencias"
        />
        <Selector
          aria-label="Filtrar por estado"
          className="!w-auto"
          value={estado}
          onChange={(e) => {
            setEstado(e.target.value as EstadoIncidencia | '')
            setPagina(0)
          }}
        >
          <option value="">Todos los estados</option>
          {(Object.keys(ESTADO_INCIDENCIA) as EstadoIncidencia[]).map((e) => (
            <option key={e} value={e}>
              {ESTADO_INCIDENCIA[e].etiqueta}
            </option>
          ))}
        </Selector>
        <Selector
          aria-label="Filtrar por origen"
          className="!w-auto"
          value={origen}
          onChange={(e) => {
            setOrigen(e.target.value as OrigenIncidencia | '')
            setPagina(0)
          }}
        >
          <option value="">Todos los orígenes</option>
          {(Object.keys(ORIGEN_INCIDENCIA) as OrigenIncidencia[]).map((o) => (
            <option key={o} value={o}>
              {ORIGEN_INCIDENCIA[o]}
            </option>
          ))}
        </Selector>
      </div>

      <MensajeError error={lista.error} onReintentar={lista.recargar} />

      {lista.cargando && !lista.datos ? (
        <Cargando />
      ) : filas.length === 0 ? (
        <p className="rounded-xl border border-line bg-panel px-5 py-10 text-center text-sm text-ink-soft">
          No hay incidencias con estos filtros.
        </p>
      ) : (
        <div className="overflow-hidden rounded-xl border border-line bg-panel">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-left text-sm">
              <thead>
                <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <th className="px-5 py-3 font-medium">Incidencia</th>
                  <th className="px-3 py-3 font-medium">Servidor</th>
                  <th className="px-3 py-3 font-medium">Origen</th>
                  <th className="px-3 py-3 font-medium">Estado</th>
                  <th className="px-3 py-3 font-medium">Abierta</th>
                  <th className="px-3 py-3 font-medium">Orden</th>
                  <th className="w-10 px-3 py-3">
                    <span className="sr-only">Detalle</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {filas.map((i) => (
                  <tr key={i.id} onClick={() => abrir(i.id)} className="cursor-pointer align-top hover:bg-panel-muted">
                    <td className="max-w-sm px-5 py-3">
                      <span className="font-mono font-medium text-ink">{codigo(i.id)}</span>
                      {i.error && <p className="line-clamp-2 text-xs text-ink-soft">{i.error}</p>}
                    </td>
                    <td className="px-3 py-3 font-mono">{i.servidor}</td>
                    <td className="px-3 py-3 text-ink-soft">{i.origen ? ORIGEN_INCIDENCIA[i.origen] : '—'}</td>
                    <td className="px-3 py-3">
                      {i.estado && <StatusPill tone={ESTADO_INCIDENCIA[i.estado].tono} label={ESTADO_INCIDENCIA[i.estado].etiqueta} />}
                    </td>
                    <td className="px-3 py-3 whitespace-nowrap text-ink-soft">{formatearFechaHora(i.fechaApertura)}</td>
                    <td className="px-3 py-3">
                      <Link
                        to={`/ordenes/${i.idOrden}`}
                        onClick={(e) => e.stopPropagation()}
                        className="font-mono text-accent hover:underline"
                      >
                        {i.orden}
                      </Link>
                    </td>
                    <td className="px-3 py-3 text-ink-faint">
                      <ChevronRight className="h-4 w-4" aria-hidden="true" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Paginacion
            pagina={pagina}
            totalPaginas={lista.datos?.totalPaginas ?? 0}
            totalElementos={lista.datos?.totalElementos ?? 0}
            onCambiar={setPagina}
          />
        </div>
      )}

      {abierta && (
        <DetalleIncidencia
          id={abierta}
          onCerrar={() => abrir(undefined)}
          onGuardado={(mensaje) => {
            avisar(mensaje)
            abrir(undefined)
            lista.recargar()
          }}
        />
      )}
    </>
  )
}
