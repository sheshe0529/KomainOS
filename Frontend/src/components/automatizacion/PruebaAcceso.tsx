import { useState } from 'react'
import { PlugZap } from 'lucide-react'
import { analisisApi } from '@/api/automatizacion'
import { cuentasServicioApi } from '@/api/credenciales'
import type { ConexionRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Selector } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { detalleFallo, FALLO } from '@/utils/automatizacion'
import { rotuloCuentaServicio } from '@/utils/cuentasServicio'
import { ResultadoFallo } from './ResultadoFallo'

interface PruebaAccesoProps {
  idServidor?: number
  idGrupo?: number
  /** Texto a la izquierda de los controles, en la misma fila */
  descripcion?: string
}

/** Para qué usa el servidor la cuenta probada; una candidata todavía no tiene uso */
function uso(r: ConexionRespuesta): string | undefined {
  const finalidades = r.finalidades ?? []
  if (finalidades.length === 0) return undefined
  if (finalidades.length > 1) return 'Evaluación y mantenimiento'
  return finalidades[0] === 'EVALUACION' ? 'Evaluación' : 'Mantenimiento'
}

/**
 * Sin privilegios de administrador la prueba es correcta: KomainOS ejecuta lo que la cuenta tiene configurado (DEC-57).
 * Para el mantenimiento conviene revisarlo; para la evaluación es el mínimo privilegio esperado (DEC-53)
 */
function advertenciaPermisos(r: ConexionRespuesta): string | undefined {
  if (!r.correcto || r.administrador !== false) return undefined
  const finalidades = r.finalidades ?? []
  return finalidades.length === 1 && finalidades[0] === 'EVALUACION'
    ? 'El análisis se ejecutará sin privilegios de administrador: las métricas que los requieren pueden quedar incompletas.'
    : 'Los scripts se ejecutarán sin privilegios de administrador y las tareas que los necesiten pueden fallar. Si la cuenta puede elevarse, configure sudo, su o runas en la cuenta.'
}

/**
 * Un solo paso para el acceso y los permisos (RF71, CP021, DEC-57, DEC-59): prueba la cuenta de la evaluación y la del
 * mantenimiento (una vez si son la misma), o una candidata. Llega al servidor, inicia sesión, aplica la elevación
 * configurada e informa los permisos efectivos.
 */
export function PruebaAcceso({ idServidor, idGrupo, descripcion }: PruebaAccesoProps) {
  const cuentas = useConsulta(() => cuentasServicioApi.listar(), [])
  const [idCuenta, setIdCuenta] = useState('')
  const [resultados, setResultados] = useState<ConexionRespuesta[]>()
  const [probando, setProbando] = useState(false)
  const [error, setError] = useState<unknown>()

  async function probar() {
    setProbando(true)
    setError(undefined)
    setResultados(undefined)
    try {
      const cuenta = idCuenta ? Number(idCuenta) : undefined
      setResultados(idGrupo ? await analisisApi.probarAccesoGrupo(idGrupo, cuenta) : await analisisApi.probarAccesoServidor(idServidor!, cuenta))
    } catch (e) {
      setError(e)
    } finally {
      setProbando(false)
    }
  }

  const servidores = new Set(resultados?.map((r) => r.idServidor))
  const conAcceso = new Set(resultados?.filter((r) => r.correcto).map((r) => r.idServidor))
  resultados?.filter((r) => !r.correcto).forEach((r) => conAcceso.delete(r.idServidor))

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        {descripcion && <p className="min-w-0 flex-1 basis-72 text-sm text-ink-soft">{descripcion}</p>}
        <Selector
          aria-label="Cuenta de servicio a probar"
          className="min-w-0 flex-1 basis-56 sm:max-w-sm"
          value={idCuenta}
          disabled={probando}
          onChange={(e) => {
            setIdCuenta(e.target.value)
            setResultados(undefined)
          }}
        >
          <option value="">Cuentas asignadas al {idGrupo ? 'grupo' : 'servidor'}</option>
          {cuentas.datos
            ?.filter((c) => c.estado === 'VIGENTE')
            .map((c) => (
              <option key={c.id} value={c.id}>
                Probar con {rotuloCuentaServicio(c)}
              </option>
            ))}
        </Selector>
        <Boton icono={PlugZap} cargando={probando} onClick={probar}>
          {idGrupo ? 'Probar todos los integrantes' : 'Probar acceso'}
        </Boton>
      </div>
      <p className="text-xs text-ink-faint">
        Llega al servidor, inicia sesión, aplica la elevación configurada en la cuenta e informa con qué permisos se
        ejecutarán los scripts. Prueba la cuenta de evaluación y la de mantenimiento, una sola vez si son la misma.
      </p>

      <MensajeError error={error} />

      {resultados && !idGrupo && (
        <div className="flex flex-col gap-2">
          {resultados.map((r) => (
            <ResultadoFallo
              key={`${r.idCuenta}-${r.finalidades?.join('-')}`}
              correcto={!!r.correcto}
              fallo={r.fallo}
              mensaje={r.mensaje}
              etiqueta={uso(r)}
              textoCorrecto={`Acceso correcto con ${r.cuenta ?? 'la cuenta de servicio'}`}
              textoFallido={r.cuenta}
              advertencia={advertenciaPermisos(r)}
            />
          ))}
        </div>
      )}

      {resultados && idGrupo && (
        <div className="flex flex-col gap-2" role="status">
          <p className="text-sm text-ink-soft">
            {servidores.size === 0
              ? 'El grupo no tiene integrantes activos.'
              : `${conAcceso.size} de ${servidores.size} servidores con acceso correcto.`}
          </p>
          {resultados.length > 0 && (
            <ul className="divide-y divide-line overflow-hidden rounded-lg border border-line">
              {resultados.map((r) => (
                <li key={`${r.idServidor}-${r.idCuenta}`} className="flex flex-wrap items-start gap-x-3 gap-y-1 px-3 py-2 text-sm">
                  <span className="font-mono text-ink">{r.servidor}</span>
                  <StatusPill
                    tone={!r.correcto ? 'danger' : r.administrador === false ? 'warning' : 'success'}
                    label={
                      !r.correcto
                        ? r.fallo
                          ? FALLO[r.fallo].etiqueta
                          : 'Falló'
                        : r.administrador === false
                          ? 'Correcto, sin privilegios'
                          : 'Correcto'
                    }
                  />
                  <span className="text-xs text-ink-faint">
                    {r.cuenta}
                    {uso(r) && ` · ${uso(r)}`}
                  </span>
                  {r.correcto && r.mensaje && <span className="w-full text-xs text-ink-soft">{r.mensaje}</span>}
                  {!r.correcto && (
                    <span className="w-full text-xs text-ink-soft">
                      {detalleFallo(r.fallo, r.mensaje)}
                      {r.fallo && <span className="block text-ink-faint">{FALLO[r.fallo].sugerencia}</span>}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
