import { useState } from 'react'
import { PlugZap } from 'lucide-react'
import { analisisApi } from '@/api/automatizacion'
import { cuentasServicioApi } from '@/api/credenciales'
import type { FinalidadCuentaServicio } from '@/api/credenciales'
import type { ConexionRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Selector } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { detalleFallo, FALLO } from '@/utils/automatizacion'
import { FINALIDADES_CUENTA, rotuloCuentaServicio } from '@/utils/cuentasServicio'
import { ResultadoFallo } from './ResultadoFallo'

interface PruebaAccesoProps {
  idServidor?: number
  idGrupo?: number
  /** Texto a la izquierda de los controles, en la misma fila */
  descripcion?: string
}

/**
 * Sin privilegios de administrador la prueba es correcta: KomainOS ejecuta lo que la cuenta tiene configurado (DEC-57).
 * Para el mantenimiento conviene revisarlo; para la evaluación es el mínimo privilegio esperado (DEC-53)
 */
function advertenciaPermisos(r: ConexionRespuesta, finalidad: FinalidadCuentaServicio): string | undefined {
  if (!r.correcto || r.administrador !== false) return undefined
  return finalidad === 'MANTENIMIENTO'
    ? 'Las tareas de mantenimiento se ejecutarán sin privilegios de administrador y pueden fallar si los necesitan. Si la cuenta puede elevarse, configure sudo, su o runas en la cuenta.'
    : 'El análisis se ejecutará sin privilegios de administrador: las métricas que los requieren pueden quedar incompletas.'
}

/**
 * Prueba la cuenta asignada o una candidata: conexión, inicio de sesión, elevación configurada y permisos efectivos
 * (RF71, CP021, DEC-57).
 */
export function PruebaAcceso({ idServidor, idGrupo, descripcion }: PruebaAccesoProps) {
  const cuentas = useConsulta(() => cuentasServicioApi.listar(), [])
  const [idCuenta, setIdCuenta] = useState('')
  const [finalidad, setFinalidad] = useState<FinalidadCuentaServicio>('MANTENIMIENTO')
  const [resultados, setResultados] = useState<ConexionRespuesta[]>()
  const [probando, setProbando] = useState(false)
  const [error, setError] = useState<unknown>()

  async function probar() {
    setProbando(true)
    setError(undefined)
    setResultados(undefined)
    try {
      const cuenta = idCuenta ? Number(idCuenta) : undefined
      setResultados(
        idGrupo ? await analisisApi.probarGrupo(idGrupo, cuenta, finalidad) : [await analisisApi.probarServidor(idServidor!, cuenta, finalidad)],
      )
    } catch (e) {
      setError(e)
    } finally {
      setProbando(false)
    }
  }

  const correctos = resultados?.filter((r) => r.correcto).length ?? 0

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        {descripcion && <p className="min-w-0 flex-1 basis-72 text-sm text-ink-soft">{descripcion}</p>}
        <Selector
          aria-label="Finalidad del acceso a probar"
          className="!w-auto"
          value={finalidad}
          disabled={probando}
          onChange={(evento) => {
            setFinalidad(evento.target.value as FinalidadCuentaServicio)
            setResultados(undefined)
          }}
        >
          {FINALIDADES_CUENTA.map((item) => <option key={item.finalidad} value={item.finalidad}>{item.etiqueta}</option>)}
        </Selector>
        <Selector
          aria-label="Cuenta de servicio a probar"
          className="!w-auto min-w-56"
          value={idCuenta}
          disabled={probando}
          onChange={(e) => {
            setIdCuenta(e.target.value)
            setResultados(undefined)
          }}
        >
          <option value="">Cuenta de {finalidad === 'EVALUACION' ? 'evaluación' : 'mantenimiento'} del {idGrupo ? 'grupo' : 'servidor'}</option>
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
        ejecutarán los scripts{finalidad === 'EVALUACION' ? ' de análisis' : ' de mantenimiento'}.
      </p>

      <MensajeError error={error} />

      {resultados && resultados.length === 1 && !idGrupo && (
        <ResultadoFallo
          correcto={!!resultados[0].correcto}
          fallo={resultados[0].fallo}
          mensaje={resultados[0].mensaje}
          textoCorrecto={`Acceso correcto con ${resultados[0].cuenta ?? 'la cuenta de servicio'}`}
          advertencia={advertenciaPermisos(resultados[0], finalidad)}
        />
      )}

      {resultados && idGrupo && (
        <div className="flex flex-col gap-2" role="status">
          <p className="text-sm text-ink-soft">
            {resultados.length === 0
              ? 'El grupo no tiene integrantes activos.'
              : `${correctos} de ${resultados.length} servidores con acceso correcto.`}
          </p>
          {resultados.length > 0 && (
            <ul className="divide-y divide-line overflow-hidden rounded-lg border border-line">
              {resultados.map((r) => (
                <li key={r.idServidor} className="flex flex-wrap items-start gap-x-3 gap-y-1 px-3 py-2 text-sm">
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
                  <span className="text-xs text-ink-faint">{r.cuenta}</span>
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
