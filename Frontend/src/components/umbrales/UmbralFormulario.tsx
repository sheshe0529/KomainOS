import { useState } from 'react'
import type { FormEvent } from 'react'
import { Save } from 'lucide-react'
import type { NivelAlerta, OperadorComparacion } from '@/api/dominio'
import { listasApi, umbralesApi } from '@/api/umbrales'
import type { MetricaRespuesta, UmbralPeticion, UmbralRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Campo, Entrada, Selector } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { useConsulta } from '@/hooks/useConsulta'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'
import { aNumero, describirMetrica, operadoresPara, problemaDeValores } from '@/utils/umbrales'
import { ListaChips } from './ListaChips'
import { ValoresNumericos } from './ValoresNumericos'

interface UmbralFormularioProps {
  /** Vacío al crear */
  umbral?: UmbralRespuesta
  metricas: MetricaRespuesta[]
  idMetricaInicial?: number
  onCerrar: () => void
  onGuardado: (umbral: UmbralRespuesta) => void
}

/** Sin nivel, la diferencia no se evalúa; al menos uno debe estar definido */
const NIVELES: { valor: '' | NivelAlerta; etiqueta: string }[] = [
  { valor: '', etiqueta: 'No evaluar' },
  { valor: 'ADVERTENCIA', etiqueta: 'Advertencia' },
  { valor: 'CRITICO', etiqueta: 'Crítico' },
]

const aTexto = (valor?: number) => (valor === undefined || valor === null ? '' : String(valor))

/** La métrica decide la modalidad: operador y valores si es numérica, lista y niveles si es de lista (RF16, R2.4) */
export function UmbralFormulario({ umbral, metricas, idMetricaInicial, onCerrar, onGuardado }: UmbralFormularioProps) {
  const activas = metricas.filter((m) => m.activo || m.id === umbral?.metrica?.id)
  const [idMetrica, setIdMetrica] = useState<number | undefined>(umbral?.metrica?.id ?? idMetricaInicial ?? activas[0]?.id)
  const [nombre, setNombre] = useState(umbral?.nombre ?? '')
  const [operador, setOperador] = useState<OperadorComparacion>(
    () => umbral?.operador ?? operadoresPara(activas.find((m) => m.id === idMetrica)?.tipoDato)[0].valor,
  )
  const [advertencia, setAdvertencia] = useState(aTexto(umbral?.valorAdvertencia))
  const [critico, setCritico] = useState(aTexto(umbral?.valorCritico))
  const [idLista, setIdLista] = useState<number | undefined>(umbral?.lista?.id)
  const [faltante, setFaltante] = useState<'' | NivelAlerta>(umbral?.nivelFaltante ?? 'CRITICO')
  const [sobrante, setSobrante] = useState<'' | NivelAlerta>(umbral?.nivelSobrante ?? '')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  const metrica = activas.find((m) => m.id === idMetrica)
  const deLista = !!metrica?.esLista
  const listas = useConsulta(() => (deLista ? listasApi.listar() : Promise.resolve([])), [deLista])
  // Solo las del tipo de la métrica; una inactiva solo si ya es la del umbral
  const listasDelTipo = (listas.datos ?? []).filter(
    (l) => l.tipoDato === metrica?.tipoDato && (l.activo || l.id === umbral?.lista?.id),
  )
  const listaElegida = listasDelTipo.find((l) => l.id === idLista)
  const operadores = operadoresPara(metrica?.tipoDato)
  const escrito = advertencia.trim() !== '' || critico.trim() !== ''
  // Lo mismo que rechazaría el backend, avisado mientras se escribe
  const problema = deLista
    ? !idLista
      ? 'Elija la lista de referencia'
      : !faltante && !sobrante
        ? 'Indique el nivel para los elementos faltantes, para los sobrantes o ambos'
        : null
    : problemaDeValores(metrica?.tipoDato, operador, aNumero(advertencia), aNumero(critico))

  function elegirMetrica(id: number) {
    setIdMetrica(id)
    setIdLista(undefined)
    const nueva = activas.find((m) => m.id === id)
    // Al pasar a una booleana, un operador de orden ya no aplica
    if (!operadoresPara(nueva?.tipoDato).some((o) => o.valor === operador)) setOperador('IGUAL')
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    if (!idMetrica) return
    if (problema) {
      setError(new Error(problema))
      return
    }
    setGuardando(true)
    setError(undefined)
    const datos: UmbralPeticion = deLista
      ? { idMetrica, nombre: nombre.trim(), idListaReferencia: idLista, nivelFaltante: faltante || undefined, nivelSobrante: sobrante || undefined }
      : { idMetrica, nombre: nombre.trim(), operador, valorAdvertencia: aNumero(advertencia), valorCritico: aNumero(critico) }
    try {
      onGuardado(
        umbral?.id
          ? await umbralesApi.actualizarUmbral(umbral.id, { ...datos, versionEsperada: umbral.fechaActualizacion })
          : await umbralesApi.crearUmbral(datos),
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
      titulo={umbral ? `Editar ${umbral.nombre}` : 'Nuevo umbral'}
      ancho="lg"
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-umbral" variante="primario" icono={Save} cargando={guardando} disabled={!metrica}>
            Guardar
          </Boton>
        </>
      }
    >
      <form id="form-umbral" onSubmit={guardar} className="flex flex-col gap-4">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo etiqueta="Métrica" obligatorio ayuda={metrica ? describirMetrica(metrica) : undefined}>
            <Selector
              value={idMetrica ?? ''}
              onChange={(e) => elegirMetrica(Number(e.target.value))}
              disabled={!!umbral}
              aria-label="Métrica"
            >
              {activas.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.nombre}
                </option>
              ))}
            </Selector>
          </Campo>
          <Campo etiqueta="Nombre" obligatorio error={errorDeCampo(error, 'nombre')}>
            <Entrada value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder={deLista ? 'Puertos esperados' : 'CPU alta'} required />
          </Campo>
        </div>

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
            {!listas.cargando && listasDelTipo.length === 0 && (
              <p className="text-sm text-ink-soft">Sin listas de este tipo. Regístrelas en Catálogos › Listas.</p>
            )}
            <div className="grid gap-4 sm:grid-cols-2">
              <Campo etiqueta="Si falta un elemento">
                <Selector value={faltante} onChange={(e) => setFaltante(e.target.value as '' | NivelAlerta)} aria-label="Nivel si falta">
                  {NIVELES.map((n) => (
                    <option key={n.valor} value={n.valor}>
                      {n.etiqueta}
                    </option>
                  ))}
                </Selector>
              </Campo>
              <Campo etiqueta="Si sobra un elemento">
                <Selector value={sobrante} onChange={(e) => setSobrante(e.target.value as '' | NivelAlerta)} aria-label="Nivel si sobra">
                  {NIVELES.map((n) => (
                    <option key={n.valor} value={n.valor}>
                      {n.etiqueta}
                    </option>
                  ))}
                </Selector>
              </Campo>
            </div>
          </>
        ) : (
          <div className="grid gap-4 sm:grid-cols-3">
            <Campo etiqueta="Condición" obligatorio ayuda={umbral?.cantidadExcepciones ? 'Fija mientras tenga excepciones' : undefined}>
              <Selector
                value={operador}
                onChange={(e) => setOperador(e.target.value as OperadorComparacion)}
                disabled={!!umbral?.cantidadExcepciones}
                aria-label="Operador"
              >
                {operadores.map((o) => (
                  <option key={o.valor} value={o.valor}>
                    {o.simbolo} {o.etiqueta}
                  </option>
                ))}
              </Selector>
            </Campo>
            <ValoresNumericos
              tipo={metrica?.tipoDato}
              advertencia={advertencia}
              critico={critico}
              onAdvertencia={setAdvertencia}
              onCritico={setCritico}
              errorAdvertencia={errorDeCampo(error, 'valorAdvertencia')}
              errorCritico={errorDeCampo(error, 'valorCritico')}
            />
          </div>
        )}
        {problema && (deLista || escrito) && !error && <p className="text-sm text-danger">{problema}</p>}
      </form>
    </Modal>
  )
}
