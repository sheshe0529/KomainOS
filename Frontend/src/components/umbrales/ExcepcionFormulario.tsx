import { useState } from 'react'
import type { FormEvent } from 'react'
import { Save } from 'lucide-react'
import { listasApi, umbralesApi } from '@/api/umbrales'
import type { ExcepcionUmbralRespuesta, UmbralRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Selector } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { useConsulta } from '@/hooks/useConsulta'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'
import { aNumero, ETIQUETA_ALCANCE, formatearLimite, problemaDeValores, simbolo } from '@/utils/umbrales'
import type { Destino } from './acciones'
import { ListaChips } from './ListaChips'
import { ValoresNumericos } from './ValoresNumericos'

interface ExcepcionFormularioProps {
  umbral: UmbralRespuesta
  destino: Destino
  /** Vacía al crear */
  excepcion?: ExcepcionUmbralRespuesta
  onCerrar: () => void
  onGuardado: () => void
}

const aTexto = (valor?: number) => (valor === undefined || valor === null ? '' : String(valor))

/** La excepción reemplaza los valores del umbral para su alcance: los dos valores, o la lista. Un valor vacío no se evalúa */
export function ExcepcionFormulario({ umbral, destino, excepcion, onCerrar, onGuardado }: ExcepcionFormularioProps) {
  const deLista = !!umbral.metrica?.esLista
  const [advertencia, setAdvertencia] = useState(aTexto(excepcion?.valorAdvertencia ?? umbral.valorAdvertencia))
  const [critico, setCritico] = useState(aTexto(excepcion?.valorCritico ?? umbral.valorCritico))
  const [idLista, setIdLista] = useState<number | undefined>(excepcion?.lista?.id)
  const [motivo, setMotivo] = useState(excepcion?.motivo ?? '')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  const listas = useConsulta(() => (deLista ? listasApi.listar() : Promise.resolve([])), [deLista])
  const listasDelTipo = (listas.datos ?? []).filter(
    (l) => l.tipoDato === umbral.metrica?.tipoDato && (l.activo || l.id === excepcion?.lista?.id),
  )
  const listaElegida = listasDelTipo.find((l) => l.id === idLista)
  const unidad = umbral.metrica?.unidad
  // Mismas reglas que el umbral, con su operador (R2.4)
  const problema = deLista
    ? !idLista
      ? 'Elija la lista de referencia'
      : null
    : problemaDeValores(umbral.metrica?.tipoDato, umbral.operador, aNumero(advertencia), aNumero(critico))

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    if (problema) {
      setError(new Error(problema))
      return
    }
    setGuardando(true)
    setError(undefined)
    const valores = deLista
      ? { idListaReferencia: idLista }
      : { valorAdvertencia: aNumero(advertencia), valorCritico: aNumero(critico) }
    const datos = { ...valores, motivo: motivo.trim() || undefined }
    try {
      if (excepcion?.id)
        await umbralesApi.actualizarExcepcion(umbral.id!, excepcion.id, { ...datos, versionEsperada: excepcion.fechaActualizacion })
      else await umbralesApi.crearExcepcion(umbral.id!, { ...datos, alcance: destino.alcance, idObjetivo: destino.id })
      onGuardado()
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      onCerrar={onCerrar}
      titulo={`${ETIQUETA_ALCANCE[destino.alcance]} ${destino.nombre}`}
      descripcion={
        deLista
          ? `Global: ${umbral.lista?.nombre ?? ''}`
          : `Global: ${simbolo(umbral.operador)} ${formatearLimite(umbral.valorAdvertencia)} / ${formatearLimite(umbral.valorCritico, unidad)}`
      }
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-excepcion" variante="primario" icono={Save} cargando={guardando}>
            Guardar
          </Boton>
        </>
      }
    >
      <form id="form-excepcion" onSubmit={guardar} className="flex flex-col gap-4">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
        {deLista ? (
          <>
            <Campo etiqueta="Lista de referencia" obligatorio>
              <Selector value={idLista ?? ''} onChange={(e) => setIdLista(Number(e.target.value) || undefined)} aria-label="Lista de referencia">
                <option value="">Elegir…</option>
                {listasDelTipo.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.nombre}
                  </option>
                ))}
              </Selector>
            </Campo>
            {listaElegida && <ListaChips elementos={listaElegida.elementos} maximo={16} />}
          </>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            <ValoresNumericos
              tipo={umbral.metrica?.tipoDato}
              operador={umbral.operador}
              advertencia={advertencia}
              critico={critico}
              onAdvertencia={setAdvertencia}
              onCritico={setCritico}
              errorAdvertencia={errorDeCampo(error, 'valorAdvertencia')}
              errorCritico={errorDeCampo(error, 'valorCritico')}
              autoFocus
            />
          </div>
        )}
        {problema && !deLista && !error && <p className="text-sm text-danger">{problema}</p>}
        <Campo etiqueta="Motivo" error={errorDeCampo(error, 'motivo')}>
          <AreaTexto value={motivo} onChange={(e) => setMotivo(e.target.value)} rows={2} maxLength={500} />
        </Campo>
      </form>
    </Modal>
  )
}
