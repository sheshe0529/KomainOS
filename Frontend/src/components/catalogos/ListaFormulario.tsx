import { useState } from 'react'
import type { FormEvent } from 'react'
import { Save } from 'lucide-react'
import type { TipoDato } from '@/api/dominio'
import { listasApi } from '@/api/umbrales'
import type { ListaReferenciaRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Campo, Entrada, Selector } from '@/components/ui/Campo'
import { EditorElementos } from '@/components/ui/EditorElementos'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'
import { EJEMPLO_ELEMENTOS, ETIQUETA_TIPO_DATO, normalizarElemento, TIPOS_LISTA } from '@/utils/umbrales'

interface ListaFormularioProps {
  /** Vacío al crear */
  lista?: ListaReferenciaRespuesta
  onCerrar: () => void
  onGuardado: (lista: ListaReferenciaRespuesta) => void
}

/** El tipo se elige al crear y no cambia: los umbrales que usan la lista dependen de él (R2.4) */
export function ListaFormulario({ lista, onCerrar, onGuardado }: ListaFormularioProps) {
  const [nombre, setNombre] = useState(lista?.nombre ?? '')
  const [tipo, setTipo] = useState<TipoDato>(lista?.tipoDato ?? 'INTEGER')
  const [ignoraMayusculas, setIgnoraMayusculas] = useState(lista?.ignoraMayusculas ?? false)
  const [elementos, setElementos] = useState<string[]>(lista?.elementos ?? [])
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  function elegirTipo(valor: TipoDato) {
    if (valor === tipo) return
    setTipo(valor)
    // Un puerto no es un servicio: los elementos de otro tipo no valen
    setElementos([])
  }

  function alternarMayusculas(ignora: boolean) {
    setIgnoraMayusculas(ignora)
    if (ignora) {
      // Mismo criterio que el backend: sin distinguir mayúsculas, SSHD y sshd son el mismo elemento
      setElementos([...new Set(elementos.map((e) => normalizarElemento(e, tipo, true) ?? e))].sort())
    }
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    const datos = { nombre: nombre.trim(), tipoDato: tipo, ignoraMayusculas: tipo === 'STRING' && ignoraMayusculas, elementos }
    try {
      onGuardado(lista?.id ? await listasApi.actualizar(lista.id, datos) : await listasApi.crear(datos))
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
      titulo={lista ? `Editar ${lista.nombre}` : 'Nueva lista'}
      ancho="lg"
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-lista" variante="primario" icono={Save} cargando={guardando} disabled={elementos.length === 0}>
            Guardar
          </Boton>
        </>
      }
    >
      <form id="form-lista" onSubmit={guardar} className="flex flex-col gap-4">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo etiqueta="Nombre" obligatorio error={errorDeCampo(error, 'nombre')}>
            <Entrada value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Puertos web" required autoFocus />
          </Campo>
          <Campo etiqueta="Tipo" obligatorio ayuda={lista ? 'No cambia una vez creada' : undefined}>
            <Selector value={tipo} onChange={(e) => elegirTipo(e.target.value as TipoDato)} disabled={!!lista} aria-label="Tipo de dato">
              {TIPOS_LISTA.map((t) => (
                <option key={t} value={t}>
                  {ETIQUETA_TIPO_DATO[t]}
                </option>
              ))}
            </Selector>
          </Campo>
        </div>
        <Campo etiqueta="Elementos" obligatorio error={errorDeCampo(error, 'elementos')} ayuda={`${elementos.length} de 500`}>
          <EditorElementos
            valor={elementos}
            onChange={setElementos}
            tipo={tipo}
            ignoraMayusculas={tipo === 'STRING' && ignoraMayusculas}
            placeholder={`${EJEMPLO_ELEMENTOS[tipo]}…`}
            aria-label="Elementos de la lista"
          />
        </Campo>
        {tipo === 'STRING' && (
          <label className="flex items-center gap-2 text-sm text-ink">
            <input type="checkbox" checked={ignoraMayusculas} onChange={(e) => alternarMayusculas(e.target.checked)} />
            No distinguir mayúsculas
          </label>
        )}
        {lista && <p className="text-xs text-ink-faint">Los elementos que quite se conservan con su fecha de baja.</p>}
      </form>
    </Modal>
  )
}
