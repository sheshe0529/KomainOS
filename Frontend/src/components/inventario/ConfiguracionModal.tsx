import { useState } from 'react'
import type { FormEvent } from 'react'
import { Save } from 'lucide-react'
import { cuentasServicioApi } from '@/api/credenciales'
import type { FamiliaSistemaOperativo, ModalidadPlanificacion, ModoEjecucion } from '@/api/dominio'
import type { ConfiguracionRespuesta } from '@/api/types'
import { SelectorCuentaServicio } from '@/components/credenciales/SelectorCuentaServicio'
import { Boton } from '@/components/ui/Boton'
import { Campo, Entrada } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { useConsulta } from '@/hooks/useConsulta'
import { FINALIDADES_CUENTA } from '@/utils/cuentasServicio'
import { ETIQUETA_MODALIDAD, ETIQUETA_MODO } from '@/utils/etiquetas'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'

export interface DatosConfiguracion {
  frecuenciaRevisionDias?: number
  frecuenciaMantenimientoDias?: number
  modalidadPlanificacion: ModalidadPlanificacion
  modoEjecucion?: ModoEjecucion
  /** Cada asignación omitida usa la predeterminada de su finalidad (RF06). */
  idCuentaServicioEvaluacion?: number
  idCuentaServicioMantenimiento?: number
}

interface ConfiguracionModalProps {
  abierto: boolean
  titulo: string
  actual?: ConfiguracionRespuesta
  /** Frecuencias de la criticidad, que se copian si se dejan vacías (HU11 CA2) */
  recomendadas?: { revision?: number; mantenimiento?: number }
  /** Solo para grupos: pide el modo de ejecución (RF17) */
  conModoEjecucion?: boolean
  /** WinRM no admite cuentas con llave SSH (HU04 CA3) */
  familia?: FamiliaSistemaOperativo
  onCerrar: () => void
  onGuardar: (datos: DatosConfiguracion) => Promise<void>
}

export function ConfiguracionModal({
  abierto,
  titulo,
  actual,
  recomendadas,
  conModoEjecucion,
  familia,
  onCerrar,
  onGuardar,
}: ConfiguracionModalProps) {
  const cuentas = useConsulta(() => cuentasServicioApi.listar(), [])
  const [revision, setRevision] = useState(actual?.frecuenciaRevisionDias?.toString() ?? '')
  const [mantenimiento, setMantenimiento] = useState(actual?.frecuenciaMantenimientoDias?.toString() ?? '')
  const [modalidad, setModalidad] = useState<ModalidadPlanificacion>(actual?.modalidadPlanificacion ?? 'AUTOMATICA')
  const [modo, setModo] = useState<ModoEjecucion>(actual?.modoEjecucion ?? 'SECUENCIAL')
  const [asignaciones, setAsignaciones] = useState({
    idCuentaServicioEvaluacion: actual?.idCuentaServicioEvaluacion,
    idCuentaServicioMantenimiento: actual?.idCuentaServicioMantenimiento,
  })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    try {
      await onGuardar({
        frecuenciaRevisionDias: revision ? Number(revision) : undefined,
        frecuenciaMantenimientoDias: mantenimiento ? Number(mantenimiento) : undefined,
        modalidadPlanificacion: modalidad,
        modoEjecucion: conModoEjecucion ? modo : undefined,
        ...asignaciones,
      })
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo={titulo}
      descripcion="Los cambios aplican solo a las órdenes nuevas; las existentes conservan su configuración."
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-configuracion" variante="primario" icono={Save} cargando={guardando}>
            Guardar configuración
          </Boton>
        </>
      }
    >
      <form id="form-configuracion" onSubmit={guardar} className="flex flex-col gap-4">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo
            etiqueta="Frecuencia de revisión (días)"
            error={errorDeCampo(error, 'frecuenciaRevisionDias')}
            ayuda={recomendadas?.revision ? `Si la deja vacía se usa la de la criticidad: ${recomendadas.revision} días` : undefined}
          >
            <Entrada type="number" min={1} value={revision} placeholder={recomendadas?.revision?.toString()} onChange={(e) => setRevision(e.target.value)} />
          </Campo>
          <Campo
            etiqueta="Frecuencia de mantenimiento (días)"
            error={errorDeCampo(error, 'frecuenciaMantenimientoDias')}
            ayuda={
              recomendadas?.mantenimiento
                ? `Si la deja vacía se usa la de la criticidad: ${recomendadas.mantenimiento} días`
                : undefined
            }
          >
            <Entrada
              type="number"
              min={1}
              value={mantenimiento}
              placeholder={recomendadas?.mantenimiento?.toString()}
              onChange={(e) => setMantenimiento(e.target.value)}
            />
          </Campo>
        </div>

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-sm font-medium text-ink">Modalidad de planificación</legend>
          {(['AUTOMATICA', 'BAJO_DEMANDA'] as ModalidadPlanificacion[]).map((m) => (
            <label key={m} className="flex cursor-pointer items-start gap-3 rounded-lg border border-line p-3 text-sm has-[:checked]:border-accent has-[:checked]:bg-accent-soft">
              <input type="radio" name="modalidad" checked={modalidad === m} onChange={() => setModalidad(m)} className="mt-0.5 accent-[var(--color-accent)]" />
              <span>
                <span className="font-medium text-ink">{ETIQUETA_MODALIDAD[m]}</span>
                <span className="block text-xs text-ink-soft">
                  {m === 'AUTOMATICA'
                    ? 'El sistema genera cada ciclo en el primer intervalo disponible de la ventana permisiva.'
                    : 'Las órdenes se originan a partir de una solicitud del administrador.'}
                </span>
              </span>
            </label>
          ))}
        </fieldset>

        {conModoEjecucion && (
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium text-ink">Modo de ejecución de los integrantes</legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {(['SECUENCIAL', 'PARALELO'] as ModoEjecucion[]).map((m) => (
                <label key={m} className="flex cursor-pointer items-center gap-2 rounded-lg border border-line p-3 text-sm has-[:checked]:border-accent has-[:checked]:bg-accent-soft">
                  <input type="radio" name="modo" checked={modo === m} onChange={() => setModo(m)} className="accent-[var(--color-accent)]" />
                  {ETIQUETA_MODO[m]}
                </label>
              ))}
            </div>
          </fieldset>
        )}

        {FINALIDADES_CUENTA.map(({ finalidad, campoId }) => (
          <SelectorCuentaServicio
            key={finalidad}
            finalidad={finalidad}
            cuentas={cuentas.datos ?? []}
            valor={asignaciones[campoId]}
            onCambiar={(id) => setAsignaciones((actuales) => ({ ...actuales, [campoId]: id }))}
            familia={familia}
            deshabilitado={cuentas.cargando}
            error={errorDeCampo(error, campoId)}
          />
        ))}
        {cuentas.error ? <MensajeError error={cuentas.error} /> : null}
      </form>
    </Modal>
  )
}
