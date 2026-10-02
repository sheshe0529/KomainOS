import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { AnalisisImportacionRespuesta, FilaAnalisisRespuesta } from '@/api/types'
import { StatusPill } from '@/components/ui/StatusPill'
import { ESTADO_FILA_IMPORTACION } from '@/utils/etiquetas'
import { Contador } from './Contador'

type FiltroFila = 'TODAS' | 'NUEVA' | 'DUPLICADA' | 'ERRONEA'

interface VistaPreviaImportacionProps {
  analisis: AnalisisImportacionRespuesta
  sobrescribir: number[]
  onSobrescribir: (filas: number[]) => void
}

/** HU08 CA2: clasifica cada registro y deja marcar los duplicados que se sobrescriben (CA4) */
export function VistaPreviaImportacion({ analisis, sobrescribir, onSobrescribir }: VistaPreviaImportacionProps) {
  const [filtro, setFiltro] = useState<FiltroFila>('TODAS')
  const sobrescribibles = analisis.filas?.filter((f) => f.sobrescribible).map((f) => f.fila!) ?? []
  const visibles = analisis.filas?.filter((f) => filtro === 'TODAS' || f.estado === filtro) ?? []
  const todasMarcadas = sobrescribir.length === sobrescribibles.length

  function alternar(fila: number) {
    onSobrescribir(sobrescribir.includes(fila) ? sobrescribir.filter((n) => n !== fila) : [...sobrescribir, fila])
  }

  return (
    <>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Contador etiqueta="Registros" valor={analisis.registros ?? 0} activo={filtro === 'TODAS'} onClick={() => setFiltro('TODAS')} />
        <Contador etiqueta="Nuevos" valor={analisis.nuevos ?? 0} tono="success" activo={filtro === 'NUEVA'} onClick={() => setFiltro('NUEVA')} />
        <Contador etiqueta="Duplicados" valor={analisis.duplicados ?? 0} tono="warning" activo={filtro === 'DUPLICADA'} onClick={() => setFiltro('DUPLICADA')} />
        <Contador etiqueta="Erróneos" valor={analisis.erroneos ?? 0} tono="danger" activo={filtro === 'ERRONEA'} onClick={() => setFiltro('ERRONEA')} />
      </div>

      {(analisis.columnasIgnoradas?.length ?? 0) > 0 && (
        <p className="rounded-lg bg-panel-muted px-3 py-2 text-xs text-ink-soft">
          No se importan estas columnas del archivo: {analisis.columnasIgnoradas!.join(', ')}.
        </p>
      )}

      {sobrescribibles.length > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-warning/40 bg-warning-soft px-3 py-2 text-sm text-ink">
          <span>
            {sobrescribibles.length === 1
              ? 'Un registro coincide con un servidor existente y puede sobrescribirlo.'
              : `${sobrescribibles.length} registros coinciden con servidores existentes y pueden sobrescribirlos.`}{' '}
            Solo se sobrescriben los que marque.
          </span>
          <button type="button" className="text-xs font-medium text-accent hover:underline" onClick={() => onSobrescribir(todasMarcadas ? [] : sobrescribibles)}>
            {todasMarcadas ? 'Desmarcar todos' : 'Marcar todos'}
          </button>
        </div>
      )}

      <div className="max-h-[45dvh] overflow-auto rounded-lg border border-line">
        <table className="w-full min-w-[720px] text-left text-sm">
          <thead className="sticky top-0 bg-panel">
            <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
              <th className="px-3 py-2 font-medium">Fila</th>
              <th className="px-3 py-2 font-medium">Servidor</th>
              <th className="px-3 py-2 font-medium">Clasificación</th>
              <th className="px-3 py-2 font-medium">Detalle</th>
              <th className="px-3 py-2 text-center font-medium">Sobrescribir</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {visibles.map((f) => (
              <FilaVistaPrevia key={f.fila} fila={f} marcada={sobrescribir.includes(f.fila!)} onAlternar={() => alternar(f.fila!)} />
            ))}
            {visibles.length === 0 && (
              <tr>
                <td colSpan={5} className="px-3 py-6 text-center text-ink-soft">
                  No hay registros en esta clasificación.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  )
}

function FilaVistaPrevia({ fila, marcada, onAlternar }: { fila: FilaAnalisisRespuesta; marcada: boolean; onAlternar: () => void }) {
  const estado = fila.estado ? ESTADO_FILA_IMPORTACION[fila.estado] : undefined
  const existente = fila.servidorExistente
  return (
    <tr className={marcada ? 'bg-warning-soft' : undefined}>
      <td className="px-3 py-2 align-top font-mono tabular-nums text-ink-soft">{fila.fila}</td>
      <td className="px-3 py-2 align-top">
        <span className="block whitespace-nowrap font-mono text-ink">{fila.hostname ?? '—'}</span>
        <span className="block font-mono text-xs text-ink-faint">{fila.direccionIp}</span>
      </td>
      <td className="px-3 py-2 align-top">{estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}</td>
      <td className="px-3 py-2 align-top text-xs text-ink-soft">
        {fila.motivos?.length ? (
          <ul className="flex flex-col gap-0.5">
            {fila.motivos.map((m) => (
              <li key={m}>{m}</li>
            ))}
          </ul>
        ) : (
          'Se registrará como servidor nuevo, pendiente de configuración.'
        )}
        {fila.sobrescribible && (fila.camposModificados?.length ?? 0) > 0 && (
          <p className="mt-1 text-ink">Cambiaría: {fila.camposModificados!.join(', ')}.</p>
        )}
        {existente && (
          <Link to={`/servidores/${existente.id}`} target="_blank" className="mt-1 inline-block text-accent hover:underline">
            Ver ficha de {existente.nombre}
          </Link>
        )}
      </td>
      <td className="px-3 py-2 text-center align-top">
        {fila.sobrescribible && (
          <input
            type="checkbox"
            checked={marcada}
            onChange={onAlternar}
            aria-label={`Sobrescribir ${existente?.nombre ?? 'el servidor existente'} con la fila ${fila.fila}`}
            className="accent-[var(--color-accent)]"
          />
        )}
      </td>
    </tr>
  )
}
