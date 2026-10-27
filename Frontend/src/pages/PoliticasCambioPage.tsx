import { useRef, useState } from 'react'
import { Bell, BellOff, Check, LoaderCircle } from 'lucide-react'
import { Link } from 'react-router-dom'
import { politicasApi } from '@/api/automatizacion'
import { catalogosApi } from '@/api/catalogos'
import type { PoliticaCambioPeticion, PoliticaCambioRespuesta } from '@/api/types'
import { PageHeader } from '@/components/common/PageHeader'
import { Selector } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Cargando } from '@/components/ui/Cargando'
import { useConsulta } from '@/hooks/useConsulta'
import { useCatalogos } from '@/hooks/useCatalogos'
import { useSesion } from '@/auth/sesion-context'

type Afectacion = PoliticaCambioPeticion['nivelAfectacion']
type Destinatario = PoliticaCambioPeticion['notificaciones'][number]
type Modalidad = 'AUTOMATICA' | 'RESPONSABLE' | 'ADMINISTRADOR' | 'AMBOS' | 'DESACTIVADA'
const COLUMNAS: { id: Afectacion; nombre: string }[] = [
  { id: 'SIN_AFECTACION', nombre: 'Sin afectación' },
  { id: 'PARCIAL', nombre: 'Parcial' },
  { id: 'TOTAL', nombre: 'Total' },
]
const ROLES: { id: Destinatario; nombre: string }[] = [
  { id: 'RESPONSABLE', nombre: 'Responsable' },
  { id: 'ADMINISTRADOR', nombre: 'Administrador' },
  { id: 'OPERADOR', nombre: 'Operador' },
]
function modalidad(r?: PoliticaCambioRespuesta): Modalidad | '' {
  if (!r) return ''
  if (!r.activo) return 'DESACTIVADA'
  if (r.requiereAdministrador && r.requiereResponsable) return 'AMBOS'
  return r.requiereResponsable ? 'RESPONSABLE' : r.requiereAdministrador ? 'ADMINISTRADOR' : 'AUTOMATICA'
}
function CeldaPolitica({
  regla,
  idEntorno,
  afectacion,
  editable,
  nombre,
  onGuardado,
  onRecargar,
}: {
  regla?: PoliticaCambioRespuesta
  idEntorno: number
  afectacion: Afectacion
  editable: boolean
  nombre: string
  onGuardado: (r: PoliticaCambioRespuesta) => void
  onRecargar: () => void
}) {
  const [guardando, setGuardando] = useState(false)
  const [guardada, setGuardada] = useState(false)
  const [error, setError] = useState<unknown>()
  const ultimos = useRef<Destinatario[]>(['ADMINISTRADOR', 'RESPONSABLE'])
  const destinatarios = regla?.notificaciones ?? []
  const notificar = destinatarios.length > 0
  async function guardar(cambios: Partial<PoliticaCambioPeticion>) {
    setGuardando(true)
    setGuardada(false)
    setError(undefined)
    try {
      const nueva = await politicasApi.guardar(regla?.id, {
        idEntorno,
        nivelAfectacion: afectacion,
        activo: regla?.activo ?? true,
        requiereAdministrador: !!regla?.requiereAdministrador,
        requiereResponsable: !!regla?.requiereResponsable,
        notificaciones: destinatarios,
        versionEsperada: regla?.fechaActualizacion,
        ...cambios,
      })
      onGuardado(nueva)
      setGuardada(true)
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }
  function cambiar(valor: Modalidad) {
    guardar(
      valor === 'DESACTIVADA'
        ? { activo: false }
        : {
            activo: true,
            requiereResponsable: valor === 'RESPONSABLE' || valor === 'AMBOS',
            requiereAdministrador: valor === 'ADMINISTRADOR' || valor === 'AMBOS',
          },
    )
  }
  return (
    <div className="flex flex-col gap-2">
      <Selector
        aria-label={`Autorización · ${nombre}`}
        value={modalidad(regla)}
        disabled={!editable || guardando}
        variante="pastilla"
        onChange={(e) => cambiar(e.target.value as Modalidad)}
      >
        <option value="" disabled>
          Sin configurar
        </option>
        <option value="AUTOMATICA">Automática</option>
        <option value="RESPONSABLE">Responsable</option>
        <option value="ADMINISTRADOR">Administrador</option>
        <option value="AMBOS">Responsable y administrador</option>
        {regla && <option value="DESACTIVADA">Desactivada</option>}
      </Selector>
      <div
        className={`flex items-center justify-between gap-2 rounded-full px-3 py-1.5 text-xs ${notificar ? 'bg-accent-soft text-accent' : 'bg-panel-muted text-ink-faint'}`}
      >
        <span className="flex items-center gap-1.5">
          {notificar ? <Bell className="h-3.5 w-3.5" /> : <BellOff className="h-3.5 w-3.5" />}Notificar
        </span>
        <button
          type="button"
          role="switch"
          aria-label={`Notificar · ${nombre}`}
          aria-checked={notificar}
          disabled={!editable || guardando || !regla?.activo}
          onClick={() => {
            if (notificar) ultimos.current = destinatarios
            guardar({ notificaciones: notificar ? [] : ultimos.current })
          }}
          className={`relative h-4 w-8 shrink-0 rounded-full transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${notificar ? 'bg-accent' : 'bg-line'}`}
        >
          <span
            className={`absolute left-0.5 top-0.5 h-3 w-3 rounded-full bg-accent-ink transition-transform ${notificar ? 'translate-x-4' : ''}`}
          />
        </button>
      </div>
      {notificar && (
        <details className="px-1 text-xs text-ink-soft">
          <summary className="cursor-pointer">Destinatarios ({destinatarios.length})</summary>
          <div className="mt-2 flex flex-wrap gap-x-3 gap-y-2">
            {ROLES.map((r) => (
              <label key={r.id} className="flex items-center gap-1.5">
                <input
                  type="checkbox"
                  disabled={!editable || guardando}
                  checked={destinatarios.includes(r.id)}
                  onChange={(e) =>
                    guardar({
                      notificaciones: e.target.checked
                        ? [...destinatarios, r.id]
                        : destinatarios.filter((x) => x !== r.id),
                    })
                  }
                />
                {r.nombre}
              </label>
            ))}
          </div>
        </details>
      )}
      {!regla && <p className="px-1 text-xs text-ink-faint">Seleccione una autorización para crear la regla.</p>}
      {(guardando || guardada) && (
        <p role="status" className="flex items-center gap-1 px-1 text-xs text-ink-soft">
          {guardando ? (
            <>
              <LoaderCircle className="h-3 w-3 animate-spin" />
              Guardando…
            </>
          ) : (
            <>
              <Check className="h-3 w-3" />
              Guardado
            </>
          )}
        </p>
      )}
      <MensajeError
        error={error}
        onReintentar={() => {
          setError(undefined)
          onRecargar()
        }}
      />
    </div>
  )
}
export function PoliticasCambioPage() {
  const { tieneRol } = useSesion()
  const lista = useConsulta(() => politicasApi.listar(), [])
  const catalogos = useCatalogos()
  const parametros = useConsulta(catalogosApi.parametros, [])
  const entornos =
    catalogos.datos?.entornos.filter((e) => e.activo || lista.datos?.some((r) => r.idEntorno === e.id)) ?? []
  const actuales = useRef<PoliticaCambioRespuesta[]>([])
  actuales.current = lista.datos ?? []
  function actualizar(r: PoliticaCambioRespuesta) {
    const nuevas = [...actuales.current.filter((x) => x.id !== r.id), r]
    actuales.current = nuevas
    lista.reemplazar(nuevas)
  }
  return (
    <>
      <PageHeader
        title="Políticas de cambio"
        description="Reglas de autorización por entorno y afectación. Cada celda se configura y guarda de forma independiente."
      />
      <MensajeError error={lista.error ?? catalogos.error} onReintentar={lista.recargar} />
      {(lista.cargando || catalogos.cargando) && !lista.datos ? (
        <Cargando texto="Cargando matriz de políticas…" />
      ) : (
        <>
          <h3 className="mb-2 font-medium">Política de gestión del cambio</h3>
          <div className="-mx-2 overflow-x-auto pb-2">
            <table
              className="w-full min-w-[900px] table-fixed border-separate border-spacing-x-2 border-spacing-y-3 text-left text-sm"
              aria-label="Matriz de políticas de cambio"
            >
              <colgroup>
                <col className="w-36" />
                {COLUMNAS.map((c) => (
                  <col key={c.id} />
                ))}
              </colgroup>
              <thead>
                <tr>
                  <th>
                    <span className="sr-only">Entorno</span>
                  </th>
                  {COLUMNAS.map((c) => (
                    <th key={c.id} scope="col" className="px-3 text-center text-xs font-medium text-ink-faint">
                      {c.nombre}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {entornos.map((e) => (
                  <tr key={e.id}>
                    <th scope="row" className="px-2 font-medium">
                      {e.nombre}
                      {!e.activo && (
                        <span className="mt-1 block text-xs font-normal text-ink-faint">Entorno inactivo</span>
                      )}
                    </th>
                    {COLUMNAS.map((c) => (
                      <td
                        key={c.id}
                        className="rounded-2xl border border-line bg-panel p-3 align-top shadow-sm shadow-black/5"
                      >
                        <CeldaPolitica
                          regla={lista.datos?.find((r) => r.idEntorno === e.id && r.nivelAfectacion === c.id)}
                          idEntorno={e.id!}
                          afectacion={c.id}
                          nombre={`${e.nombre} · ${c.nombre}`}
                          editable={tieneRol('ADMINISTRADOR') && !!e.activo}
                          onGuardado={actualizar}
                          onRecargar={lista.recargar}
                        />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {entornos.length === 0 && (
            <p className="rounded-xl border border-line bg-panel p-6 text-sm text-ink-soft">
              Cree un entorno en Catálogos para configurar sus políticas de cambio.
            </p>
          )}
        </>
      )}
      <p className="mt-3 text-xs leading-relaxed text-ink-soft">
        Cuando se requieren ambas aprobaciones, responde primero el responsable y después el administrador. El plazo es
        compartido. Los cambios se aplican a las nuevas solicitudes de autorización.
      </p>
      <section className="mt-7" aria-label="Parámetros de ejecución">
        <div className="mb-3 flex items-center justify-between gap-3">
          <h3 className="font-medium">Parámetros de ejecución</h3>
          <Link to="/configuracion/parametros" className="text-sm text-accent hover:underline">
            {tieneRol('ADMINISTRADOR') ? 'Editar parámetros' : 'Ver parámetros'}
          </Link>
        </div>
        <MensajeError error={parametros.error} />
        <dl className="grid grid-cols-2 gap-5 rounded-2xl border border-line bg-panel p-6 shadow-sm shadow-black/5 lg:grid-cols-4">
          {[
            ['Concurrencia máxima', parametros.datos?.maxEjecucionesConcurrentes, 'servidores'],
            ['Duración máx. por tarea', parametros.datos?.maxDuracionTareaMinutos, 'min'],
            ['Duración máx. por MOP', parametros.datos?.maxDuracionMopMinutos, 'min'],
            ['Racha estable', parametros.datos?.minCiclosRachaEstable, 'ciclos'],
          ].map(([etiqueta, valor, unidad]) => (
            <div key={String(etiqueta)}>
              <dt className="text-xs text-ink-faint">{etiqueta}</dt>
              <dd className="mt-1 font-mono text-lg font-semibold">
                {valor ?? '—'} <span className="text-sm">{unidad}</span>
              </dd>
            </div>
          ))}
        </dl>
      </section>
    </>
  )
}
