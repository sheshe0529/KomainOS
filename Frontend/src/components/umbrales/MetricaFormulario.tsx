import { useState } from 'react'
import type { FormEvent } from 'react'
import { Save } from 'lucide-react'
import type { TipoDato } from '@/api/dominio'
import { umbralesApi } from '@/api/umbrales'
import type { MetricaRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Campo, Entrada, Selector } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'
import { ETIQUETA_TIPO_DATO, TIPOS_ESCALAR, TIPOS_LISTA } from '@/utils/umbrales'

interface MetricaFormularioProps {
  metrica?: MetricaRespuesta
  onCerrar: () => void
  onGuardado: (metrica: MetricaRespuesta) => void
}

/** El código no cambia: es lo que busca el sistema en la salida del script de análisis */
export function MetricaFormulario({ metrica, onCerrar, onGuardado }: MetricaFormularioProps) {
  const [codigo, setCodigo] = useState(metrica?.codigo ?? '')
  const [nombre, setNombre] = useState(metrica?.nombre ?? '')
  const [unidad, setUnidad] = useState(metrica?.unidad ?? '')
  const [esLista, setEsLista] = useState(metrica?.esLista ?? false)
  const [tipo, setTipo] = useState<TipoDato>(metrica?.tipoDato ?? 'DECIMAL')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  const tipos = esLista ? TIPOS_LISTA : TIPOS_ESCALAR
  // Un conjunto de elementos o un verdadero/falso no son magnitudes: no llevan unidad
  const llevaUnidad = !esLista && tipo !== 'BOOLEAN'
  const ayudaTipo = esLista
    ? 'Cada elemento que informe el script se interpreta con este tipo, y sus umbrales solo pueden usar listas de referencia del mismo tipo'
    : tipo === 'BOOLEAN'
      ? 'El script informa 1 (verdadero) o 0 (falso)'
      : undefined

  function elegirClase(lista: boolean) {
    setEsLista(lista)
    if (!(lista ? TIPOS_LISTA : TIPOS_ESCALAR).includes(tipo)) setTipo(lista ? 'STRING' : 'DECIMAL')
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    const comunes = { nombre: nombre.trim(), unidad: llevaUnidad ? unidad.trim() || undefined : undefined, tipoDato: tipo, esLista }
    try {
      onGuardado(
        metrica?.id
          ? await umbralesApi.actualizarMetrica(metrica.id, comunes)
          : await umbralesApi.crearMetrica({ ...comunes, codigo: codigo.trim() }),
      )
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
      titulo={metrica ? `Editar ${metrica.nombre}` : 'Nueva métrica'}
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-metrica" variante="primario" icono={Save} cargando={guardando}>
            Guardar
          </Boton>
        </>
      }
    >
      <form id="form-metrica" onSubmit={guardar} className="flex flex-col gap-4">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
        <Campo etiqueta="Código" obligatorio error={errorDeCampo(error, 'codigo')}>
          <Entrada
            value={codigo}
            onChange={(e) => setCodigo(e.target.value)}
            placeholder="cpu_uso_pct"
            disabled={!!metrica}
            className="font-mono"
            required
            autoFocus={!metrica}
          />
        </Campo>
        <Campo etiqueta="Nombre" obligatorio error={errorDeCampo(error, 'nombre')}>
          <Entrada value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Uso de CPU" required />
        </Campo>
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo etiqueta="Mide">
            <Selector value={esLista ? 'LISTA' : 'VALOR'} onChange={(e) => elegirClase(e.target.value === 'LISTA')} aria-label="Qué mide">
              <option value="VALOR">Un valor</option>
              <option value="LISTA">Una lista de elementos</option>
            </Selector>
          </Campo>
          <Campo etiqueta={esLista ? 'Tipo de los elementos' : 'Tipo del valor'} obligatorio error={errorDeCampo(error, 'tipoDato')}>
            <Selector value={tipo} onChange={(e) => setTipo(e.target.value as TipoDato)} aria-label="Tipo de dato">
              {tipos.map((t) => (
                <option key={t} value={t}>
                  {ETIQUETA_TIPO_DATO[t]}
                </option>
              ))}
            </Selector>
          </Campo>
        </div>
        {ayudaTipo && <p className="-mt-2 text-xs text-ink-faint">{ayudaTipo}</p>}
        {llevaUnidad && (
          <Campo etiqueta="Unidad" error={errorDeCampo(error, 'unidad')}>
            <Entrada value={unidad} onChange={(e) => setUnidad(e.target.value)} placeholder="%" />
          </Campo>
        )}
      </form>
    </Modal>
  )
}
