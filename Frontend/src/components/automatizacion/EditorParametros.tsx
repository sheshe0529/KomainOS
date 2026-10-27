import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react'
import type { ParametroPeticion } from '@/api/types'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Entrada, Selector } from '@/components/ui/Campo'
import { nuevaClave, problemaLocal, TIPOS_PARAMETRO, usoEnScript } from '@/utils/parametrosScript'
import type { Lenguaje, ParametroEditable } from '@/utils/parametrosScript'

interface EditorParametrosProps {
  parametros: ParametroEditable[]
  onChange: (parametros: ParametroEditable[]) => void
  lenguaje: Lenguaje
  soloLectura?: boolean
}

/**
 * Parámetros de una versión (RF25). KomainOS los define como variables antes del código, con el mismo valor en la
 * vista previa y en la ejecución, así el script los usa directamente sin leer argumentos
 */
export function EditorParametros({ parametros, onChange, lenguaje, soloLectura = false }: EditorParametrosProps) {
  const actualizar = (clave: string, cambios: Partial<ParametroPeticion>) =>
    onChange(parametros.map((p) => (p.clave === clave ? { ...p, ...cambios } : p)))

  const mover = (indice: number, paso: -1 | 1) => {
    const copia = [...parametros]
    const [fila] = copia.splice(indice, 1)
    copia.splice(indice + paso, 0, fila)
    onChange(copia)
  }

  const agregar = () =>
    onChange([...parametros, { clave: nuevaClave(), nombre: '', tipo: 'STRING', obligatorio: true, valorPorDefecto: '' }])

  return (
    <div className="flex flex-col gap-3">
      {parametros.length === 0 ? (
        <p className="rounded-lg border border-dashed border-line px-4 py-5 text-center text-sm text-ink-soft">
          Sin parámetros: el script se ejecuta tal como está escrito.
        </p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-line">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead>
              <tr className="border-b border-line bg-panel-muted text-xs uppercase tracking-wide text-ink-faint">
                <th className="px-3 py-2 font-medium">Nombre</th>
                <th className="px-3 py-2 font-medium">Tipo</th>
                <th className="px-3 py-2 font-medium">Valor</th>
                <th className="px-3 py-2 text-center font-medium">Obligatorio</th>
                <th className="px-3 py-2 font-medium">En el script</th>
                {!soloLectura && (
                  <th className="w-24 px-3 py-2">
                    <span className="sr-only">Acciones</span>
                  </th>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {parametros.map((p, i) => {
                const problema = soloLectura ? undefined : problemaLocal(p, parametros)
                const tipo = TIPOS_PARAMETRO.find((t) => t.valor === p.tipo)
                return (
                  <tr key={p.clave} className="align-top">
                    <td className="px-3 py-2">
                      {soloLectura ? (
                        <span className="font-mono">{p.nombre}</span>
                      ) : (
                        <Entrada
                          value={p.nombre}
                          maxLength={64}
                          spellCheck={false}
                          aria-label={`Nombre del parámetro ${i + 1}`}
                          aria-invalid={!!problema}
                          placeholder="DIAS_RETENCION"
                          onChange={(e) => actualizar(p.clave, { nombre: e.target.value.replace(/\s/g, '_') })}
                          className="font-mono"
                        />
                      )}
                      {problema && <p className="mt-1 text-xs text-danger">{problema}</p>}
                    </td>
                    <td className="w-36 px-3 py-2">
                      {soloLectura ? (
                        tipo?.etiqueta
                      ) : (
                        <Selector
                          value={p.tipo}
                          aria-label={`Tipo del parámetro ${i + 1}`}
                          onChange={(e) =>
                            actualizar(p.clave, {
                              tipo: e.target.value as ParametroPeticion['tipo'],
                              valorPorDefecto: e.target.value === 'BOOLEAN' ? 'false' : p.valorPorDefecto,
                            })
                          }
                        >
                          {TIPOS_PARAMETRO.map((t) => (
                            <option key={t.valor} value={t.valor}>
                              {t.etiqueta}
                            </option>
                          ))}
                        </Selector>
                      )}
                    </td>
                    <td className="px-3 py-2">
                      {soloLectura ? (
                        <span className="font-mono">{p.valorPorDefecto || '—'}</span>
                      ) : p.tipo === 'BOOLEAN' ? (
                        <Selector
                          value={(p.valorPorDefecto ?? '').toLowerCase()}
                          aria-label={`Valor del parámetro ${i + 1}`}
                          onChange={(e) => actualizar(p.clave, { valorPorDefecto: e.target.value })}
                        >
                          <option value="true">Sí (true)</option>
                          <option value="false">No (false)</option>
                        </Selector>
                      ) : (
                        <Entrada
                          value={p.valorPorDefecto ?? ''}
                          maxLength={1000}
                          spellCheck={false}
                          inputMode={p.tipo === 'STRING' ? 'text' : 'decimal'}
                          placeholder={tipo?.ejemplo}
                          aria-label={`Valor del parámetro ${i + 1}`}
                          onChange={(e) => actualizar(p.clave, { valorPorDefecto: e.target.value })}
                          className="font-mono"
                        />
                      )}
                    </td>
                    <td className="px-3 py-2 text-center">
                      <input
                        type="checkbox"
                        checked={!!p.obligatorio}
                        disabled={soloLectura}
                        aria-label={`Parámetro ${i + 1} obligatorio`}
                        onChange={(e) => actualizar(p.clave, { obligatorio: e.target.checked })}
                        className="mt-2.5 h-4 w-4 accent-[var(--color-accent)]"
                      />
                    </td>
                    <td className="px-3 py-2">
                      <code className="mt-2 inline-block rounded bg-panel-muted px-1.5 py-0.5 font-mono text-xs text-ink-soft">
                        {usoEnScript(p.nombre, lenguaje)}
                      </code>
                    </td>
                    {!soloLectura && (
                      <td className="px-3 py-2">
                        <div className="flex">
                          <BotonIcono icono={ArrowUp} etiqueta="Subir" disabled={i === 0} onClick={() => mover(i, -1)} />
                          <BotonIcono
                            icono={ArrowDown}
                            etiqueta="Bajar"
                            disabled={i === parametros.length - 1}
                            onClick={() => mover(i, 1)}
                          />
                          <BotonIcono
                            icono={Trash2}
                            etiqueta="Quitar"
                            onClick={() => onChange(parametros.filter((o) => o.clave !== p.clave))}
                          />
                        </div>
                      </td>
                    )}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {!soloLectura && (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-xs text-ink-faint">
            Las MOP se ejecutan sin intervención: cada parámetro toma este valor en todos los servidores.
          </p>
          <Boton icono={Plus} onClick={agregar}>
            Agregar parámetro
          </Boton>
        </div>
      )}
    </div>
  )
}
