import { Fragment, useMemo, useState } from 'react'
import { diferenciasPorLinea, resumenDiferencias } from '@/utils/diferencias'
import type { LineaDiferencia } from '@/utils/diferencias'

interface DiferenciasCodigoProps {
  anterior: string
  nuevo: string
  etiquetaAnterior: string
  etiquetaNueva: string
}

/** Líneas sin cambios que se muestran alrededor de cada cambio */
const CONTEXTO = 3

type Bloque = { tipo: 'lineas'; lineas: LineaDiferencia[] } | { tipo: 'oculto'; desde: number; lineas: LineaDiferencia[] }

function agrupar(lineas: LineaDiferencia[]): Bloque[] {
  const cerca = lineas.map((_, i) =>
    lineas.slice(Math.max(0, i - CONTEXTO), i + CONTEXTO + 1).some((l) => l.tipo !== 'igual'),
  )
  const bloques: Bloque[] = []
  lineas.forEach((linea, i) => {
    const visible = cerca[i]
    const ultimo = bloques.at(-1)
    if (visible) {
      if (ultimo?.tipo === 'lineas') ultimo.lineas.push(linea)
      else bloques.push({ tipo: 'lineas', lineas: [linea] })
    } else if (ultimo?.tipo === 'oculto') {
      ultimo.lineas.push(linea)
    } else {
      bloques.push({ tipo: 'oculto', desde: i, lineas: [linea] })
    }
  })
  return bloques
}

const ESTILO: Record<LineaDiferencia['tipo'], string> = {
  igual: 'text-ink-soft',
  agregada: 'bg-success-soft text-ink',
  quitada: 'bg-danger-soft text-ink',
}

const SIGNO: Record<LineaDiferencia['tipo'], string> = { igual: ' ', agregada: '+', quitada: '−' }

/** Cambios de código entre dos versiones, con las zonas sin cambios plegadas */
export function DiferenciasCodigo({ anterior, nuevo, etiquetaAnterior, etiquetaNueva }: DiferenciasCodigoProps) {
  const lineas = useMemo(() => diferenciasPorLinea(anterior, nuevo), [anterior, nuevo])
  const [desplegados, setDesplegados] = useState<Set<number>>(new Set())

  if (!lineas) {
    return <p className="text-sm text-ink-soft">Los scripts son demasiado extensos para compararlos aquí.</p>
  }

  const { agregadas, quitadas } = resumenDiferencias(lineas)
  if (agregadas === 0 && quitadas === 0) {
    return <p className="text-sm text-ink-soft">El código es idéntico a {etiquetaAnterior}.</p>
  }

  return (
    <div className="flex flex-col gap-2">
      <p className="text-sm text-ink-soft">
        De {etiquetaAnterior} a {etiquetaNueva}:{' '}
        <span className="font-medium text-success">+{agregadas}</span>{' '}
        <span className="font-medium text-danger">−{quitadas}</span> líneas
      </p>
      <div className="max-h-[32rem] overflow-auto rounded-lg border border-line bg-panel font-mono text-xs leading-relaxed">
        <table className="w-full border-collapse">
          <tbody>
            {agrupar(lineas).map((bloque, b) =>
              bloque.tipo === 'oculto' && !desplegados.has(bloque.desde) ? (
                <tr key={`o${bloque.desde}`}>
                  <td colSpan={4} className="border-y border-line bg-panel-muted px-3 py-1 text-center">
                    <button
                      type="button"
                      onClick={() => setDesplegados(new Set(desplegados).add(bloque.desde))}
                      className="text-ink-soft hover:text-ink hover:underline"
                    >
                      Mostrar {bloque.lineas.length} {bloque.lineas.length === 1 ? 'línea sin cambios' : 'líneas sin cambios'}
                    </button>
                  </td>
                </tr>
              ) : (
                <Fragment key={`l${b}`}>
                  {bloque.lineas.map((l, i) => (
                    <tr key={`${b}-${i}`} className={ESTILO[l.tipo]}>
                      <td className="w-10 select-none px-2 text-right text-ink-faint">{l.antes ?? ''}</td>
                      <td className="w-10 select-none px-2 text-right text-ink-faint">{l.despues ?? ''}</td>
                      <td className="w-4 select-none text-center">{SIGNO[l.tipo]}</td>
                      <td className="whitespace-pre px-2">{l.texto || ' '}</td>
                    </tr>
                  ))}
                </Fragment>
              ),
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
