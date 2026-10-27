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
import { ResultadoFallo } from './ResultadoFallo'

interface PruebaAccesoProps {
  idServidor?: number
  idGrupo?: number
}

/**
 * Verifica conectividad, autenticación y permisos de administrador (RF71, CP021) con la cuenta asignada o con una
 * candidata antes de asignarla. No ejecuta ningún mantenimiento
 */
export function PruebaAcceso({ idServidor, idGrupo }: PruebaAccesoProps) {
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
      setResultados(
        idGrupo ? await analisisApi.probarGrupo(idGrupo, cuenta) : [await analisisApi.probarServidor(idServidor!, cuenta)],
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
        <Selector
          aria-label="Cuenta de servicio a probar"
          className="!w-auto min-w-56"
          value={idCuenta}
          onChange={(e) => setIdCuenta(e.target.value)}
        >
          <option value="">{idGrupo ? 'Cuenta asignada a cada servidor' : 'Cuenta asignada al servidor'}</option>
          {cuentas.datos
            ?.filter((c) => c.estado === 'VIGENTE')
            .map((c) => (
              <option key={c.id} value={c.id}>
                Probar con {c.nombre}
              </option>
            ))}
        </Selector>
        <Boton icono={PlugZap} cargando={probando} onClick={probar}>
          {idGrupo ? 'Probar todos los integrantes' : 'Probar acceso'}
        </Boton>
      </div>

      <MensajeError error={error} />

      {resultados && resultados.length === 1 && !idGrupo && (
        <ResultadoFallo
          correcto={!!resultados[0].correcto}
          fallo={resultados[0].fallo}
          mensaje={resultados[0].mensaje}
          simulado={resultados[0].simulado}
          textoCorrecto={`Acceso correcto con ${resultados[0].cuenta ?? 'la cuenta de servicio'}`}
        />
      )}

      {resultados && idGrupo && (
        <div className="flex flex-col gap-2" role="status">
          <p className="text-sm text-ink-soft">
            {resultados.length === 0
              ? 'El grupo no tiene integrantes activos.'
              : `${correctos} de ${resultados.length} servidores con acceso correcto${resultados[0]?.simulado ? ' (simulado)' : ''}.`}
          </p>
          {resultados.length > 0 && (
            <ul className="divide-y divide-line overflow-hidden rounded-lg border border-line">
              {resultados.map((r) => (
                <li key={r.idServidor} className="flex flex-wrap items-start gap-x-3 gap-y-1 px-3 py-2 text-sm">
                  <span className="font-mono text-ink">{r.servidor}</span>
                  <StatusPill
                    tone={r.correcto ? 'success' : 'danger'}
                    label={r.correcto ? 'Correcto' : r.fallo ? FALLO[r.fallo].etiqueta : 'Falló'}
                  />
                  <span className="text-xs text-ink-faint">{r.cuenta}</span>
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
