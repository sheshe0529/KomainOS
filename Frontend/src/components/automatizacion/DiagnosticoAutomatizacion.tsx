import { useState } from 'react'
import { ChevronDown, FlaskConical, Server, TriangleAlert } from 'lucide-react'
import type { Diagnostico } from '@/api/types'
import { Dato } from '@/components/common/Tarjeta'
import { formatearFechaHora } from '@/utils/formato'

interface DiagnosticoAutomatizacionProps {
  diagnostico: Diagnostico
}

const si = (valor?: boolean) => (valor ? 'Sí' : 'No')

/**
 * Estado del motor de ejecución: si las ejecuciones son simuladas o van a los servidores con Ansible, si el proceso
 * automático corre y qué le falta al controlador para operar en modo real
 */
export function DiagnosticoAutomatizacion({ diagnostico: d }: DiagnosticoAutomatizacionProps) {
  const [abierto, setAbierto] = useState(false)
  const avisos = d.avisos ?? []
  const t = d.transporte

  return (
    <section
      className={`mb-6 rounded-xl border ${
        d.simulado ? 'border-warning/40 bg-warning-soft/60' : avisos.length ? 'border-danger/40 bg-danger-soft/60' : 'border-line bg-panel'
      }`}
    >
      <button
        type="button"
        aria-expanded={abierto}
        onClick={() => setAbierto(!abierto)}
        className="flex w-full flex-wrap items-center gap-x-6 gap-y-2 px-5 py-3 text-left text-sm"
      >
        <span className="flex items-center gap-2 font-medium text-ink">
          {d.simulado ? (
            <FlaskConical className="h-4 w-4 text-warning" aria-hidden="true" />
          ) : (
            <Server className="h-4 w-4 text-success" aria-hidden="true" />
          )}
          {d.simulado ? 'Modo simulado: los scripts no llegan a los servidores' : 'Modo real: ejecución con Ansible'}
        </span>
        <span className="text-ink-soft">
          Proceso automático:{' '}
          <span className="text-ink">{d.procesoHabilitado ? `cada ${d.intervaloSegundos} s` : 'detenido'}</span>
        </span>
        <span className="text-ink-soft">
          Conexiones simultáneas: <span className="text-ink">{d.maxConexionesSimultaneas}</span>
        </span>
        {avisos.length > 0 && (
          <span className="flex items-center gap-1.5 text-danger">
            <TriangleAlert className="h-4 w-4" aria-hidden="true" />
            {avisos.length} {avisos.length === 1 ? 'aviso' : 'avisos'}
          </span>
        )}
        <ChevronDown
          className={`ml-auto h-4 w-4 text-ink-faint transition-transform ${abierto ? 'rotate-180' : ''}`}
          aria-hidden="true"
        />
      </button>

      {abierto && (
        <div className="flex flex-col gap-4 border-t border-line px-5 py-4">
          {avisos.length > 0 && (
            <ul className="flex list-disc flex-col gap-1 pl-5 text-sm text-danger">
              {avisos.map((a) => (
                <li key={a}>{a}</li>
              ))}
            </ul>
          )}
          {d.simulado && (
            <p className="text-sm text-ink-soft">
              Las respuestas se calculan con los umbrales vigentes de cada servidor, para probar el ciclo completo sin
              conectarse. Para ejecutar en servidores reales configure{' '}
              <code className="font-mono text-ink">KOMAINOS_AUTOMATIZACION_MODO=REAL</code> en un controlador Linux con
              Ansible.
            </p>
          )}
          <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Dato etiqueta="Controlador" valor={`${d.controlador ?? '—'}${d.controladorCompatible ? '' : ' (no compatible)'}`} />
            <Dato
              etiqueta="Ansible"
              valor={d.ansibleDisponible ? d.versionAnsible : 'No disponible'}
              mono={!!d.ansibleDisponible}
            />
            <Dato etiqueta="Ejecutable" valor={d.ejecutable} mono />
            <Dato etiqueta="Tiempo de conexión" valor={t?.timeoutConexionSegundos ? `${t.timeoutConexionSegundos} s` : undefined} />
            <Dato etiqueta="SSH" valor={t ? `Puerto ${t.puertoSsh} · verifica la clave del host: ${si(t.verificarClaveHost)}` : undefined} />
            <Dato
              etiqueta="WinRM"
              valor={
                t
                  ? `${t.esquemaWinrm?.toUpperCase()} ${t.puertoWinrm} · ${t.transporteWinrm} · valida certificado: ${si(t.validarCertificadoWinrm)}`
                  : undefined
              }
            />
            <Dato etiqueta="Verificado" valor={formatearFechaHora(d.verificado)} />
          </dl>
        </div>
      )}
    </section>
  )
}
