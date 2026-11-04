import { useState } from 'react'
import { cicloApi } from '@/api/automatizacion'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo } from '@/components/ui/Campo'
import { ErrorApi } from '@/api/cliente'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { SelectorFecha } from '@/components/ui/SelectorFecha'
import { errorDeCampo } from '@/utils/errores'
import { claveDia, formatearFechaHora } from '@/utils/formato'

export type AccionOrden =
  | 'aprobar'
  | 'rechazar'
  | 'proponer'
  | 'aceptar-fecha'
  | 'rechazar-propuesta'
  | 'conforme'
  | 'no-conforme'
  | 'cerrar-sin-implementacion'

interface Presentacion {
  titulo: string
  explicacion: string
  campo?: string
  boton: string
  /** Aviso al terminar */
  hecho: string
  peligro?: boolean
}

const PRESENTACION: Record<AccionOrden, Presentacion> = {
  aprobar: {
    titulo: 'Aprobar el mantenimiento',
    explicacion: 'Si es la última aprobación de la ronda, la orden queda autorizada y entra a la cola en su fecha.',
    campo: 'Comentario',
    boton: 'Aprobar',
    hecho: 'Aprobación registrada.',
  },
  rechazar: {
    titulo: 'Rechazar el mantenimiento',
    explicacion: 'La orden se rechaza y no se ejecuta. El motivo queda en la bitácora.',
    campo: 'Motivo del rechazo',
    boton: 'Rechazar',
    hecho: 'Mantenimiento rechazado.',
    peligro: true,
  },
  proponer: {
    titulo: 'Proponer otra fecha',
    explicacion:
      'El administrador decide si la acepta. La fecha no puede chocar con otros mantenimientos; si queda fuera de la ventana permisiva del servidor, deberá confirmarlo.',
    campo: 'Motivo',
    boton: 'Enviar propuesta',
    hecho: 'Propuesta de fecha enviada al administrador.',
  },
  'aceptar-fecha': {
    titulo: 'Aceptar la fecha propuesta',
    explicacion: 'La orden se reprograma a la fecha propuesta y se abre una nueva ronda de aprobaciones.',
    boton: 'Aceptar fecha',
    hecho: 'Fecha aceptada: se abrió una nueva ronda de aprobaciones.',
  },
  'rechazar-propuesta': {
    titulo: 'No aceptar la fecha propuesta',
    explicacion: 'Sin acuerdo sobre la fecha, el mantenimiento queda rechazado.',
    campo: 'Motivo',
    boton: 'Rechazar mantenimiento',
    hecho: 'Mantenimiento rechazado.',
    peligro: true,
  },
  conforme: {
    titulo: 'Confirmar que el servicio funciona',
    explicacion: 'Con su conformidad el mantenimiento se cierra.',
    campo: 'Observaciones',
    boton: 'Validar conforme',
    hecho: 'Conformidad registrada: el mantenimiento se cierra.',
  },
  'no-conforme': {
    titulo: 'Reportar que el servicio no funciona',
    explicacion: 'Se registra una incidencia para que el administrador la atienda antes de cerrar.',
    campo: 'Qué no funciona',
    boton: 'Reportar no conformidad',
    hecho: 'No conformidad registrada: se abrió una incidencia.',
    peligro: true,
  },
  'cerrar-sin-implementacion': {
    titulo: 'Cerrar sin intervenir',
    explicacion: 'Alguna tarea no tiene implementación para el sistema operativo del servidor. La orden se cierra sin ejecutar nada.',
    campo: 'Motivo',
    boton: 'Cerrar orden',
    hecho: 'Orden cerrada sin intervención.',
    peligro: true,
  },
}

interface AccionOrdenModalProps {
  idOrden: number
  accion: AccionOrden
  fechaPropuesta?: string
  onCerrar: () => void
  onHecho: (mensaje: string) => void
}

/** Decisiones de la orden (RF44, RF47, RF48, RF57, RF42): todas exigen motivo para la trazabilidad */
export function AccionOrdenModal({ idOrden, accion, fechaPropuesta, onCerrar, onHecho }: AccionOrdenModalProps) {
  const p = PRESENTACION[accion]
  const [texto, setTexto] = useState('')
  const [fecha, setFecha] = useState('')
  const [error, setError] = useState<unknown>()
  const [enviando, setEnviando] = useState(false)
  // La fecha queda fuera de la ventana permisiva: el responsable debe confirmarlo expresamente (HU19 CA3)
  const [fueraDeVentana, setFueraDeVentana] = useState(false)
  const [confirmaFuera, setConfirmaFuera] = useState(false)

  const completo =
    (!p.campo || texto.trim()) && (accion !== 'proponer' || fecha) && (!fueraDeVentana || confirmaFuera)

  async function confirmar() {
    setEnviando(true)
    setError(undefined)
    const motivo = texto.trim()
    try {
      switch (accion) {
        case 'aprobar':
          await cicloApi.decidir(idOrden, { decision: 'APROBADA', motivo })
          break
        case 'rechazar':
        case 'rechazar-propuesta':
          await cicloApi.decidir(idOrden, { decision: 'RECHAZADA', motivo })
          break
        case 'proponer':
          await cicloApi.decidir(idOrden, {
            decision: 'FECHA_PROPUESTA',
            motivo,
            fechaPropuesta: new Date(fecha).toISOString(),
            confirmarFueraVentana: fueraDeVentana && confirmaFuera,
          })
          break
        case 'aceptar-fecha':
          await cicloApi.aceptarFecha(idOrden)
          break
        case 'conforme':
        case 'no-conforme':
          await cicloApi.validar(idOrden, accion === 'conforme', motivo)
          break
        case 'cerrar-sin-implementacion':
          await cicloApi.cerrarSinImplementacion(idOrden, motivo)
          break
      }
      onHecho(p.hecho)
    } catch (e) {
      if (accion === 'proponer' && e instanceof ErrorApi && e.message.includes('fuera de la ventana permisiva')) {
        // No es un error: se pide la confirmación expresa antes de volver a enviar
        setFueraDeVentana(true)
        setConfirmaFuera(false)
        return
      }
      setError(e)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Modal
      abierto
      titulo={p.titulo}
      onCerrar={onCerrar}
      pie={
        <>
          <Boton onClick={onCerrar} disabled={enviando}>
            Cancelar
          </Boton>
          <Boton variante={p.peligro ? 'peligro' : 'primario'} cargando={enviando} disabled={!completo} onClick={confirmar}>
            {p.boton}
          </Boton>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <MensajeError error={error} />
        <p className="text-sm text-ink-soft">{p.explicacion}</p>
        {accion === 'aceptar-fecha' && fechaPropuesta && (
          <p className="rounded-lg bg-panel-muted px-3 py-2 text-sm text-ink">
            Fecha propuesta: <strong>{formatearFechaHora(fechaPropuesta)}</strong>
          </p>
        )}
        {accion === 'proponer' && (
          <Campo etiqueta="Nueva fecha de inicio" obligatorio error={errorDeCampo(error, 'fechaPropuesta')}>
            <SelectorFecha
              conHora
              required
              value={fecha}
              min={claveDia(new Date())}
              onChange={(valor) => {
                setFecha(valor)
                setFueraDeVentana(false)
                setConfirmaFuera(false)
              }}
              aria-label="Nueva fecha de inicio"
            />
          </Campo>
        )}
        {accion === 'proponer' && fueraDeVentana && (
          <div className="flex flex-col gap-2 rounded-lg border border-warning/40 bg-warning-soft px-3 py-3 text-sm">
            <p className="font-medium text-ink">La fecha queda fuera de la ventana permisiva del servidor</p>
            <p className="text-ink-soft">
              Puede proponerla igual: el mantenimiento se ejecutaría fuera del horario acordado con el negocio. Su confirmación
              queda registrada.
            </p>
            <label className="flex items-start gap-2 text-ink">
              <input
                type="checkbox"
                checked={confirmaFuera}
                onChange={(e) => setConfirmaFuera(e.target.checked)}
                className="mt-0.5 h-4 w-4 accent-[var(--color-accent)]"
              />
              Confirmo que estoy seguro de proponer el {fecha ? formatearFechaHora(new Date(fecha).toISOString()) : ''} fuera de
              la ventana permisiva.
            </label>
          </div>
        )}
        {p.campo && (
          <Campo etiqueta={p.campo} obligatorio error={errorDeCampo(error, 'motivo') ?? errorDeCampo(error, 'observaciones')}>
            <AreaTexto value={texto} maxLength={1000} autoFocus onChange={(e) => setTexto(e.target.value)} />
          </Campo>
        )}
      </div>
    </Modal>
  )
}
