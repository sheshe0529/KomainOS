import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  Play,
  Plus,
  Database,
  ClipboardList,
  Activity,
  ShieldCheck,
  ClipboardCheck,
  ShieldAlert,
  Archive,
  ChevronRight,
} from 'lucide-react'
import { automatizacionApi } from '@/api/automatizacion'
import type { ObjetivoProgramacion } from '@/components/planificacion/ProgramarModal'
import { SeleccionObjetivoModal } from '@/components/planificacion/SeleccionObjetivoModal'
import { PageHeader } from '@/components/common/PageHeader'
import { IndicadoresMantenimiento } from '@/components/automatizacion/IndicadoresMantenimiento'
import { ModoAutomatizacion } from '@/components/automatizacion/ModoAutomatizacion'
import { TablaAutomatizacion, celda } from '@/components/automatizacion/TablaAutomatizacion'
import { Boton } from '@/components/ui/Boton'
import { Campo, AreaTexto, Selector } from '@/components/ui/Campo'
import { Modal } from '@/components/ui/Modal'
import { MensajeError } from '@/components/ui/MensajeError'
import { useConsulta } from '@/hooks/useConsulta'
import { useCatalogos } from '@/hooks/useCatalogos'
import { useSesion } from '@/auth/sesion-context'
import { useAvisos } from '@/components/ui/avisos-context'
import { ETIQUETA_ETAPA, ETIQUETA_ESTADO_ORDEN } from '@/utils/etiquetas'
import { formatearFechaHora } from '@/utils/formato'
const ETAPAS = Object.entries(ETIQUETA_ETAPA)
const PRESENTACION = {
  PLANIFICACION: { icono: ClipboardList, detalle: 'Próximo ciclo calculado' },
  EVALUACION: { icono: Activity, detalle: 'Métricas y MOP en curso' },
  AUTORIZACION: { icono: ShieldCheck, detalle: 'Esperando aprobación' },
  EJECUCION: { icono: Play, detalle: 'Tareas en el servidor' },
  VALIDACION: { icono: ClipboardCheck, detalle: 'A la espera del responsable' },
  INCIDENCIA: { icono: ShieldAlert, detalle: 'Requiere atención' },
  CIERRE: { icono: Archive, detalle: 'Ciclo finalizado' },
}
export function AutomatizacionPage() {
  const { tieneRol } = useSesion()
  const admin = tieneRol('ADMINISTRADOR')
  const navigate = useNavigate()
  const { avisar } = useAvisos()
  const [vista, setVista] = useState<'sala' | 'cola' | 'indicadores'>('sala')
  const [pagina, setPagina] = useState(0)
  const [etapa, setEtapa] = useState('')
  const [entorno, setEntorno] = useState('')
  const [criticidad, setCriticidad] = useState('')
  const [error, setError] = useState<unknown>()
  const [trabajando, setTrabajando] = useState(false)
  const [eligiendo, setEligiendo] = useState(false)
  const [objetivo, setObjetivo] = useState<ObjetivoProgramacion>()
  const [motivo, setMotivo] = useState('')
  const [fuera, setFuera] = useState(false)
  const catalogos = useCatalogos()
  const sala = useConsulta(
    () => automatizacionApi.sala(pagina, etapa, entorno, criticidad),
    [pagina, etapa, entorno, criticidad],
  )
  const cola = useConsulta(() => automatizacionApi.cola(pagina), [pagina])
  const indicadores = useConsulta(automatizacionApi.indicadores, [])
  useEffect(() => {
    const timer = window.setInterval(() => {
      sala.recargar()
      cola.recargar()
      indicadores.recargar()
    }, 5000)
    return () => window.clearInterval(timer)
  }, [sala.recargar, cola.recargar, indicadores.recargar])
  async function ejecutar(accion: () => Promise<unknown>, mensaje: string) {
    setTrabajando(true)
    setError(undefined)
    try {
      await accion()
      avisar(mensaje)
      sala.recargar()
      cola.recargar()
      indicadores.recargar()
    } catch (e) {
      setError(e)
    } finally {
      setTrabajando(false)
    }
  }
  return (
    <>
      <PageHeader
        title="Sala de operaciones"
        description="Supervise el ciclo de mantenimiento, sus aprobaciones y la cola por servidor."
        actions={
          admin && (
            <>
              <Boton
                icono={Database}
                cargando={trabajando}
                onClick={() =>
                  ejecutar(
                    automatizacionApi.demostracion,
                    'Datos de demostración disponibles en scripts, tareas y políticas.',
                  )
                }
              >
                Cargar demostración
              </Boton>
              <Boton
                icono={Play}
                cargando={trabajando}
                onClick={() => ejecutar(automatizacionApi.procesar, 'Ciclo de automatización procesado.')}
              >
                Procesar ciclo
              </Boton>
              <Boton variante="primario" icono={Plus} onClick={() => setEligiendo(true)}>
                Lanzamiento inmediato
              </Boton>
            </>
          )
        }
      />
      <ModoAutomatizacion />
      <MensajeError error={error ?? sala.error ?? cola.error} />
      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        {ETAPAS.map(([id, nombre]) => {
          const { icono: Icono, detalle } = PRESENTACION[id as keyof typeof PRESENTACION]
          return (
            <button
              key={id}
              type="button"
              aria-pressed={etapa === id}
              onClick={() => {
                setEtapa(etapa === id ? '' : id)
                setVista('sala')
                setPagina(0)
              }}
              className={`rounded-2xl border p-5 text-left shadow-sm shadow-black/5 transition-colors hover:border-accent/40 ${etapa === id ? 'border-accent bg-accent-soft' : 'border-line bg-panel'}`}
            >
              <span className="mb-4 flex items-start justify-between">
                <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent-soft text-accent">
                  <Icono className="h-5 w-5" aria-hidden="true" />
                </span>
                <ChevronRight className="h-4 w-4 text-ink-faint" aria-hidden="true" />
              </span>
              <span className="text-sm text-ink-soft">{nombre}</span>
              <strong className="mt-2 block text-2xl font-semibold tabular-nums">{indicadores.datos?.[id] ?? 0}</strong>
              <span className="mt-1 block text-xs text-ink-faint">{detalle}</span>
            </button>
          )
        })}
      </div>
      <div className="mb-4 flex flex-wrap gap-2">
        <Boton
          variante={vista === 'sala' ? 'primario' : 'secundario'}
          onClick={() => {
            setVista('sala')
            setPagina(0)
          }}
        >
          Sala de operaciones
        </Boton>
        <Boton
          variante={vista === 'cola' ? 'primario' : 'secundario'}
          onClick={() => {
            setVista('cola')
            setPagina(0)
          }}
        >
          Cola por servidor
        </Boton>
        <Boton variante={vista === 'indicadores' ? 'primario' : 'secundario'} onClick={() => setVista('indicadores')}>
          Indicadores de gestión
        </Boton>
        {admin && vista === 'cola' && (
          <Boton
            icono={Play}
            onClick={() => ejecutar(automatizacionApi.ejecutar, 'Cola despachada según capacidad y exclusión.')}
          >
            Ejecutar cola
          </Boton>
        )}
      </div>
      {vista === 'indicadores' ? (
        <IndicadoresMantenimiento />
      ) : vista === 'sala' ? (
        <>
          <div className="mb-4 grid gap-3 sm:grid-cols-3">
            <Campo etiqueta="Etapa">
              <Selector
                value={etapa}
                onChange={(e) => {
                  setEtapa(e.target.value)
                  setPagina(0)
                }}
              >
                <option value="">Todas</option>
                {ETAPAS.map(([id, n]) => (
                  <option key={id} value={id}>
                    {n}
                  </option>
                ))}
              </Selector>
            </Campo>
            <Campo etiqueta="Entorno">
              <Selector
                value={entorno}
                onChange={(e) => {
                  setEntorno(e.target.value)
                  setPagina(0)
                }}
              >
                <option value="">Todos</option>
                {catalogos.datos?.entornos.map((e) => (
                  <option key={e.id} value={e.id}>
                    {e.nombre}
                  </option>
                ))}
              </Selector>
            </Campo>
            <Campo etiqueta="Criticidad">
              <Selector
                value={criticidad}
                onChange={(e) => {
                  setCriticidad(e.target.value)
                  setPagina(0)
                }}
              >
                <option value="">Todas</option>
                {catalogos.datos?.criticidades.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.nombre}
                  </option>
                ))}
              </Selector>
            </Campo>
          </div>
          <TablaAutomatizacion
            cabeceras={['Orden', 'Objetivo', 'Estado', 'Inicio programado', 'Criticidad']}
            pagina={sala.datos}
            onPagina={setPagina}
            fila={(o) => (
              <tr key={o.id}>
                <td className={celda}>
                  <Link className="font-mono text-accent hover:underline" to={`/ordenes/${o.id}`}>
                    {o.codigo}
                  </Link>
                </td>
                <td className={celda}>{o.objetivo?.nombre}</td>
                <td className={celda}>{o.estado ? ETIQUETA_ESTADO_ORDEN[o.estado] : ''}</td>
                <td className={celda}>{formatearFechaHora(o.inicioProgramado)}</td>
                <td className={celda}>{o.criticidad?.nombre}</td>
              </tr>
            )}
          />
        </>
      ) : (
        <TablaAutomatizacion
          cabeceras={['Posición', 'Orden', 'Servidor', 'Prioridad', 'Estado', 'Inicio real']}
          pagina={cola.datos}
          onPagina={setPagina}
          fila={(d) => (
            <tr key={d.idDetalle}>
              <td className={celda}>{d.posicion}</td>
              <td className={celda}>
                <Link className="text-accent" to={`/ordenes/${d.idOrden}`}>
                  {d.orden}
                </Link>
              </td>
              <td className={`${celda} font-mono`}>
                {d.servidor}
                {d.piloto && ' · Piloto'}
              </td>
              <td className={celda}>{d.prioridad}</td>
              <td className={celda}>{d.estado?.replaceAll('_', ' ')}</td>
              <td className={celda}>{formatearFechaHora(d.inicioReal)}</td>
            </tr>
          )}
        />
      )}
      {eligiendo && (
        <SeleccionObjetivoModal
          abierto
          onCerrar={() => setEligiendo(false)}
          onElegir={(o) => {
            setEligiendo(false)
            setObjetivo(o)
            setMotivo('')
            setFuera(false)
          }}
        />
      )}
      {objetivo && (
        <Modal
          abierto
          titulo={`Lanzamiento inmediato · ${objetivo.nombre}`}
          ancho="lg"
          onCerrar={() => !trabajando && setObjetivo(undefined)}
          pie={
            <>
              <Boton disabled={trabajando} onClick={() => setObjetivo(undefined)}>
                Cancelar
              </Boton>
              <Boton
                variante="primario"
                cargando={trabajando}
                disabled={!motivo.trim()}
                onClick={() =>
                  ejecutar(async () => {
                    const o = await automatizacionApi.inmediata({ ...objetivo, motivo, confirmarFueraVentana: fuera })
                    navigate(`/ordenes/${o.id}`)
                  }, 'Evaluación del lanzamiento iniciada.')
                }
              >
                Confirmar lanzamiento
              </Boton>
            </>
          }
        >
          <div className="flex flex-col gap-4">
            <MensajeError error={error} />
            <p className="text-sm text-ink-soft">
              La orden tendrá prioridad alta. La ejecución exige las aprobaciones de la política y conserva la exclusión
              por servidor.
            </p>
            <Campo etiqueta="Motivo" obligatorio>
              <AreaTexto value={motivo} onChange={(e) => setMotivo(e.target.value)} />
            </Campo>
            <label className="flex items-start gap-2 text-sm">
              <input
                type="checkbox"
                checked={fuera}
                onChange={(e) => setFuera(e.target.checked)}
                className="mt-1 accent-[var(--color-accent)]"
              />
              Autorizo expresamente la excepción si el inicio queda fuera de la ventana permisiva.
            </label>
          </div>
        </Modal>
      )}
    </>
  )
}
