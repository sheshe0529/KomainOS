import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { KeyRound, Save } from 'lucide-react'
import { catalogosApi } from '@/api/catalogos'
import type { ParametrosSistemaPeticion, ParametrosSistemaRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Campo, Entrada } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { useConsulta } from '@/hooks/useConsulta'
import { formatearFechaHora } from '@/utils/formato'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'

type Formulario = Record<keyof ParametrosSistemaPeticion, string>

const CAMPOS: { campo: keyof ParametrosSistemaPeticion; etiqueta: string; unidad: string; ayuda: string }[] = [
  {
    campo: 'maxEjecucionesConcurrentes',
    etiqueta: 'Concurrencia máxima',
    unidad: 'servidores',
    ayuda: 'Servidores en ejecución simultánea. La planificación la respeta; no aplica a lanzamientos inmediatos.',
  },
  {
    campo: 'maxDuracionTareaMinutos',
    etiqueta: 'Duración máxima por tarea',
    unidad: 'min',
    ayuda: 'Ninguna tarea del catálogo puede estimarse por encima de este valor.',
  },
  {
    campo: 'maxDuracionMopMinutos',
    etiqueta: 'Duración máxima por MOP',
    unidad: 'min',
    ayuda: 'Es el tiempo que la planificación reserva por servidor en cada mantenimiento.',
  },
  {
    campo: 'minCiclosRachaEstable',
    etiqueta: 'Racha estable',
    unidad: 'ciclos',
    ayuda: 'Mantenimientos consecutivos conformes para ampliar la periodicidad.',
  },
  {
    campo: 'minutosExpiracionToken',
    etiqueta: 'Vigencia de la sesión',
    unidad: 'min',
    ayuda: 'Al vencer, el usuario debe iniciar sesión nuevamente.',
  },
]

function aFormulario(p?: ParametrosSistemaRespuesta): Formulario {
  return {
    maxEjecucionesConcurrentes: String(p?.maxEjecucionesConcurrentes ?? ''),
    maxDuracionTareaMinutos: String(p?.maxDuracionTareaMinutos ?? ''),
    maxDuracionMopMinutos: String(p?.maxDuracionMopMinutos ?? ''),
    minCiclosRachaEstable: String(p?.minCiclosRachaEstable ?? ''),
    minutosExpiracionToken: String(p?.minutosExpiracionToken ?? ''),
  }
}

/** El operador los consulta y el administrador los modifica (RF68) */
export function ParametrosPage() {
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const { avisar } = useAvisos()
  const parametros = useConsulta(() => catalogosApi.parametros(), [])
  const [f, setF] = useState<Formulario | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  const valores = f ?? aFormulario(parametros.datos)

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    try {
      const actualizados = await catalogosApi.actualizarParametros({
        maxEjecucionesConcurrentes: Number(valores.maxEjecucionesConcurrentes),
        maxDuracionTareaMinutos: Number(valores.maxDuracionTareaMinutos),
        maxDuracionMopMinutos: Number(valores.maxDuracionMopMinutos),
        minCiclosRachaEstable: Number(valores.minCiclosRachaEstable),
        minutosExpiracionToken: Number(valores.minutosExpiracionToken),
      })
      parametros.reemplazar(actualizados)
      setF(null)
      avisar('Parámetros de ejecución actualizados.')
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  if (parametros.cargando && !parametros.datos) return <Cargando />

  return (
    <>
      <PageHeader
        title="Parámetros de ejecución"
        description={`Parámetros globales del sistema. Última actualización: ${formatearFechaHora(parametros.datos?.fechaActualizacion)}`}
      />
      <MensajeError error={parametros.error} onReintentar={parametros.recargar} />
      <form onSubmit={guardar} className="flex flex-col gap-6 rounded-xl border border-line bg-panel p-6">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
        <div className="grid gap-6 md:grid-cols-2">
          {CAMPOS.map(({ campo, etiqueta, unidad, ayuda }) => (
            <Campo key={campo} etiqueta={`${etiqueta} (${unidad})`} ayuda={ayuda} error={errorDeCampo(error, campo)}>
              <Entrada
                type="number"
                min={1}
                value={valores[campo]}
                disabled={!esAdmin}
                onChange={(e) => setF({ ...valores, [campo]: e.target.value })}
                required
              />
            </Campo>
          ))}
        </div>
        {esAdmin && (
          <div className="flex justify-end gap-2">
            {f && (
              <Boton onClick={() => setF(null)} disabled={guardando}>
                Descartar cambios
              </Boton>
            )}
            <Boton type="submit" variante="primario" icono={Save} cargando={guardando} disabled={!f}>
              Guardar parámetros
            </Boton>
          </div>
        )}
      </form>
      <section className="mt-6 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-line bg-panel p-6">
        <div className="flex items-start gap-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-accent-soft text-accent">
            <KeyRound className="h-5 w-5" aria-hidden="true" />
          </span>
          <div>
            <h3 className="text-sm font-semibold text-ink">Cuenta de servicio predeterminada</h3>
            <p className="mt-0.5 text-sm text-ink-soft">
              {parametros.datos?.cuentaServicioPredeterminada?.nombre ? (
                <span className="font-medium text-ink">{parametros.datos.cuentaServicioPredeterminada.nombre}</span>
              ) : (
                <span className="text-warning">No definida</span>
              )}
              . La usan las configuraciones de mantenimiento que no tienen una cuenta propia.
            </p>
          </div>
        </div>
        {esAdmin && (
          <Link to="/configuracion/cuentas-servicio" className="text-sm font-medium text-accent hover:underline">
            Gestionar cuentas de servicio
          </Link>
        )}
      </section>
    </>
  )
}
