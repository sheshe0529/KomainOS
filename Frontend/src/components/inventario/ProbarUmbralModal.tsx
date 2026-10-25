import { useState } from 'react'
import type { FormEvent } from 'react'
import { FlaskConical } from 'lucide-react'
import { umbralesApi } from '@/api/umbrales'
import type { PruebaUmbralRespuesta, ReferenciaSimple, UmbralServidorRespuesta } from '@/api/types'
import { ListaChips } from '@/components/umbrales/ListaChips'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada, Selector } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { StatusPill } from '@/components/ui/StatusPill'
import { describirValores, EJEMPLO_ELEMENTOS, ETIQUETA_ORIGEN, NIVEL } from '@/utils/umbrales'

interface ProbarUmbralModalProps {
  idServidor: number
  umbral: UmbralServidorRespuesta
  /** Grupos del servidor: se puede probar como integrante de una orden de ese grupo */
  grupos: ReferenciaSimple[]
  onCerrar: () => void
}

/** Evalúa un valor con lo que rige para el servidor, sin registrar nada (RF16) */
export function ProbarUmbralModal({ idServidor, umbral, grupos, onCerrar }: ProbarUmbralModalProps) {
  const deLista = !!umbral.metrica?.esLista
  const [valor, setValor] = useState('')
  const [elementos, setElementos] = useState('')
  const [idGrupo, setIdGrupo] = useState('')
  const [resultado, setResultado] = useState<PruebaUmbralRespuesta>()
  const [probando, setProbando] = useState(false)
  const [error, setError] = useState<unknown>()

  async function probar(evento: FormEvent) {
    evento.preventDefault()
    setProbando(true)
    setError(undefined)
    try {
      setResultado(
        await umbralesApi.probar(idServidor, umbral.umbral!.id!, {
          valor: deLista ? undefined : valor,
          elementos: deLista ? elementos : undefined,
          idGrupo: idGrupo ? Number(idGrupo) : undefined,
        }),
      )
    } catch (e) {
      setResultado(undefined)
      setError(e)
    } finally {
      setProbando(false)
    }
  }

  const nivel = resultado?.nivel ? NIVEL[resultado.nivel] : undefined

  return (
    <Modal
      abierto
      onCerrar={onCerrar}
      titulo={`Probar ${umbral.umbral?.nombre ?? ''}`}
      descripcion="No se registra ninguna medición."
      pie={
        <>
          <Boton onClick={onCerrar}>Cerrar</Boton>
          <Boton type="submit" form="form-prueba" variante="primario" icono={FlaskConical} cargando={probando}>
            Probar
          </Boton>
        </>
      }
    >
      <form id="form-prueba" onSubmit={probar} className="flex flex-col gap-4">
        <MensajeError error={error} />
        {deLista ? (
          <Campo etiqueta="Elementos encontrados" ayuda="Separados por coma, punto y coma o salto de línea">
            <AreaTexto value={elementos} onChange={(e) => setElementos(e.target.value)} rows={3} placeholder={EJEMPLO_ELEMENTOS[umbral.metrica?.tipoDato ?? 'STRING']} autoFocus />
          </Campo>
        ) : umbral.metrica?.tipoDato === 'BOOLEAN' ? (
          <Campo etiqueta="Valor informado" obligatorio>
            <Selector value={valor} onChange={(e) => setValor(e.target.value)} aria-label="Valor informado">
              <option value="">Elegir…</option>
              <option value="1">Verdadero (1)</option>
              <option value="0">Falso (0)</option>
            </Selector>
          </Campo>
        ) : (
          <Campo etiqueta={`Valor${umbral.metrica?.unidad ? ` (${umbral.metrica.unidad})` : ''}`} obligatorio>
            <Entrada
              type="number"
              step={umbral.metrica?.tipoDato === 'INTEGER' ? '1' : 'any'}
              value={valor}
              onChange={(e) => setValor(e.target.value)}
              required
              autoFocus
            />
          </Campo>
        )}
        {grupos.length > 0 && (
          <Campo etiqueta="Contexto">
            <Selector value={idGrupo} onChange={(e) => setIdGrupo(e.target.value)} aria-label="Contexto de la prueba">
              <option value="">Mantenimiento individual</option>
              {grupos.map((g) => (
                <option key={g.id} value={g.id}>
                  Orden del grupo {g.nombre}
                </option>
              ))}
            </Selector>
          </Campo>
        )}
      </form>

      {resultado && nivel && (
        <div className="mt-4 flex flex-col gap-3 rounded-lg border border-line bg-panel-muted px-4 py-3" role="status">
          <div className="flex flex-wrap items-center gap-2">
            <StatusPill tone={nivel.tono} label={nivel.etiqueta} />
            <span className="text-sm text-ink-soft">
              {resultado.origen ? ETIQUETA_ORIGEN[resultado.origen] : ''}
              {resultado.excepcion?.objetivo?.nombre && ` ${resultado.excepcion.objetivo.nombre}`} ·{' '}
              <span className="font-mono">{describirValores(resultado.aplicado, umbral.metrica?.unidad)}</span>
            </span>
          </div>
          {deLista && (
            <dl className="grid gap-2 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-xs font-medium text-ink-soft">Falta</dt>
                <dd>{resultado.faltantes?.length ? <ListaChips elementos={resultado.faltantes} /> : <span className="text-ink-faint">—</span>}</dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-ink-soft">Sobra</dt>
                <dd>{resultado.sobrantes?.length ? <ListaChips elementos={resultado.sobrantes} /> : <span className="text-ink-faint">—</span>}</dd>
              </div>
            </dl>
          )}
        </div>
      )}
    </Modal>
  )
}
