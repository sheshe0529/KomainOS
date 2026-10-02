import { useMemo, useState } from 'react'
import type { DragEvent } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, Download, FileSpreadsheet, ScanSearch, Upload } from 'lucide-react'
import { FORMATOS, TAMANO_MAXIMO_BYTES, intercambioApi, type FormatoArchivo } from '@/api/intercambio'
import type { AnalisisImportacionRespuesta, FilaAnalisisRespuesta, ResultadoImportacionRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Selector } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { StatusPill, type StatusTone } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { ESTADO_FILA_IMPORTACION, RESULTADO_IMPORTACION } from '@/utils/etiquetas'

interface ImportarModalProps {
  abierto: boolean
  onCerrar: () => void
  /** Se llama al cerrar después de una importación, para recargar el listado */
  onImportado: () => void
}

type Paso = 'archivo' | 'vista' | 'resultado'
type FiltroFila = 'TODAS' | 'NUEVA' | 'DUPLICADA' | 'ERRONEA'

const EXTENSIONES = '.xlsx,.csv,.yaml,.yml,.json'

function tamanoLegible(bytes: number): string {
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/** Un duplicado solo se sobrescribe si el usuario lo marca (HU08 CA4) */
export function ImportarModal({ abierto, onCerrar, onImportado }: ImportarModalProps) {
  const columnas = useConsulta(() => intercambioApi.columnas(), [])
  const [paso, setPaso] = useState<Paso>('archivo')
  const [archivo, setArchivo] = useState<File | null>(null)
  const [arrastrando, setArrastrando] = useState(false)
  const [formatoPlantilla, setFormatoPlantilla] = useState<FormatoArchivo>('XLSX')
  const [analisis, setAnalisis] = useState<AnalisisImportacionRespuesta | null>(null)
  const [resultado, setResultado] = useState<ResultadoImportacionRespuesta | null>(null)
  const [sobrescribir, setSobrescribir] = useState<number[]>([])
  const [filtro, setFiltro] = useState<FiltroFila>('TODAS')
  const [trabajando, setTrabajando] = useState(false)
  const [error, setError] = useState<unknown>()

  const importables = columnas.datos?.filter((c) => c.importable) ?? []
  const obligatorias = importables.filter((c) => c.obligatoria).map((c) => c.etiqueta)
  const opcionales = importables.filter((c) => !c.obligatoria).map((c) => c.etiqueta)

  function elegir(nuevo: File | undefined) {
    setError(undefined)
    if (!nuevo) return
    if (nuevo.size > TAMANO_MAXIMO_BYTES) {
      setArchivo(null)
      setError(new Error('El archivo supera el tamaño máximo permitido de 5 MB'))
      return
    }
    setArchivo(nuevo)
  }

  function alSoltar(evento: DragEvent<HTMLLabelElement>) {
    evento.preventDefault()
    setArrastrando(false)
    elegir(evento.dataTransfer.files[0])
  }

  async function ejecutar(accion: () => Promise<void>) {
    setTrabajando(true)
    setError(undefined)
    try {
      await accion()
    } catch (e) {
      setError(e)
    } finally {
      setTrabajando(false)
    }
  }

  const analizar = () =>
    ejecutar(async () => {
      const respuesta = await intercambioApi.analizar(archivo!)
      setAnalisis(respuesta)
      setSobrescribir([])
      setFiltro('TODAS')
      setPaso('vista')
    })

  const importar = () =>
    ejecutar(async () => {
      setResultado(await intercambioApi.importar(archivo!, sobrescribir))
      setPaso('resultado')
    })

  const descargarPlantilla = () => ejecutar(async () => void (await intercambioApi.plantilla(formatoPlantilla)))

  function cerrar() {
    if (resultado) onImportado()
    onCerrar()
  }

  const sobrescribibles = useMemo(
    () => analisis?.filas?.filter((f) => f.sobrescribible).map((f) => f.fila!) ?? [],
    [analisis],
  )
  const visibles = analisis?.filas?.filter((f) => filtro === 'TODAS' || f.estado === filtro) ?? []
  const nuevos = analisis?.nuevos ?? 0

  const titulo = { archivo: 'Importar servidores', vista: 'Vista previa de la importación', resultado: 'Resultado de la importación' }[paso]

  const pie = {
    archivo: (
      <>
        <Boton onClick={cerrar}>Cancelar</Boton>
        <Boton variante="primario" icono={ScanSearch} cargando={trabajando} disabled={!archivo} onClick={analizar}>
          Analizar archivo
        </Boton>
      </>
    ),
    vista: (
      <>
        <Boton variante="fantasma" icono={ArrowLeft} onClick={() => setPaso('archivo')} className="mr-auto">
          Elegir otro archivo
        </Boton>
        <Boton onClick={cerrar}>Cancelar</Boton>
        <Boton variante="primario" icono={Upload} cargando={trabajando} disabled={nuevos === 0 && sobrescribir.length === 0} onClick={importar}>
          {textoConfirmar(nuevos, sobrescribir.length)}
        </Boton>
      </>
    ),
    resultado: (
      <Boton variante="primario" onClick={cerrar}>
        Cerrar
      </Boton>
    ),
  }[paso]

  return (
    <Modal abierto={abierto} onCerrar={cerrar} titulo={titulo} ancho="xl" pie={pie}>
      <div className="flex flex-col gap-5">
        <MensajeError error={error} />

        {paso === 'archivo' && (
          <>
            <label
              onDragOver={(e) => {
                e.preventDefault()
                setArrastrando(true)
              }}
              onDragLeave={() => setArrastrando(false)}
              onDrop={alSoltar}
              className={`flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed px-6 py-8 text-center transition ${
                arrastrando ? 'border-accent bg-accent-soft' : 'border-line hover:bg-panel-muted'
              }`}
            >
              <FileSpreadsheet className="h-8 w-8 text-accent" aria-hidden="true" />
              {archivo ? (
                <>
                  <span className="font-mono text-sm font-medium text-ink">{archivo.name}</span>
                  <span className="text-xs text-ink-soft">{tamanoLegible(archivo.size)} · haga clic para elegir otro</span>
                </>
              ) : (
                <>
                  <span className="text-sm font-medium text-ink">Arrastre un archivo o haga clic para elegirlo</span>
                  <span className="text-xs text-ink-soft">XLSX, CSV, YAML o JSON · hasta 5 MB y 5000 registros</span>
                </>
              )}
              <input
                type="file"
                accept={EXTENSIONES}
                className="sr-only"
                onChange={(e) => {
                  elegir(e.target.files?.[0])
                  e.target.value = ''
                }}
              />
            </label>

            <div className="grid gap-4 rounded-lg bg-panel-muted p-4 text-sm sm:grid-cols-[1fr_auto]">
              <div className="flex flex-col gap-1.5">
                <p className="text-ink">
                  <span className="font-medium">Columnas obligatorias:</span> {obligatorias.join(', ')}.
                </p>
                <p className="text-ink-soft">
                  <span className="font-medium">Opcionales:</span> {opcionales.join(', ')}.
                </p>
                <p className="text-xs text-ink-soft">
                  El sistema operativo, el entorno y la criticidad se indican por su nombre, y el responsable por su código
                  de usuario. Los registros nuevos quedan pendientes de configuración. Al sobrescribir un servidor, las
                  columnas que no estén en el archivo conservan su valor.
                </p>
              </div>
              <div className="flex items-end gap-2 sm:flex-col sm:items-stretch">
                <Selector
                  aria-label="Formato de la plantilla"
                  value={formatoPlantilla}
                  onChange={(e) => setFormatoPlantilla(e.target.value as FormatoArchivo)}
                >
                  {FORMATOS.map((f) => (
                    <option key={f.valor} value={f.valor}>
                      {f.etiqueta}
                    </option>
                  ))}
                </Selector>
                <Boton icono={Download} onClick={descargarPlantilla} disabled={trabajando}>
                  Plantilla
                </Boton>
              </div>
            </div>
          </>
        )}

        {paso === 'vista' && analisis && (
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
                <button
                  type="button"
                  className="text-xs font-medium text-accent hover:underline"
                  onClick={() => setSobrescribir(sobrescribir.length === sobrescribibles.length ? [] : sobrescribibles)}
                >
                  {sobrescribir.length === sobrescribibles.length ? 'Desmarcar todos' : 'Marcar todos'}
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
                    <FilaVistaPrevia
                      key={f.fila}
                      fila={f}
                      marcada={sobrescribir.includes(f.fila!)}
                      onAlternar={() =>
                        setSobrescribir((s) => (s.includes(f.fila!) ? s.filter((n) => n !== f.fila) : [...s, f.fila!]))
                      }
                    />
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
        )}

        {paso === 'resultado' && resultado && (
          <>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              <Contador etiqueta="Creados" valor={resultado.creados ?? 0} tono="success" />
              <Contador etiqueta="Sobrescritos" valor={resultado.actualizados ?? 0} tono="success" />
              <Contador etiqueta="Omitidos" valor={resultado.omitidos ?? 0} />
              <Contador etiqueta="Rechazados" valor={resultado.rechazados ?? 0} tono="danger" />
            </div>
            <div className="max-h-[45dvh] overflow-auto rounded-lg border border-line">
              <table className="w-full min-w-[640px] text-left text-sm">
                <thead className="sticky top-0 bg-panel">
                  <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                    <th className="px-3 py-2 font-medium">Fila</th>
                    <th className="px-3 py-2 font-medium">Servidor</th>
                    <th className="px-3 py-2 font-medium">Resultado</th>
                    <th className="px-3 py-2 font-medium">Detalle</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {resultado.filas?.map((f) => {
                    const r = f.resultado ? RESULTADO_IMPORTACION[f.resultado] : undefined
                    const aplicado = f.resultado === 'CREADO' || f.resultado === 'ACTUALIZADO'
                    return (
                      <tr key={f.fila}>
                        <td className="px-3 py-2 font-mono tabular-nums text-ink-soft">{f.fila}</td>
                        <td className="px-3 py-2 font-mono">
                          {aplicado && f.idServidor ? (
                            <Link to={`/servidores/${f.idServidor}`} className="text-ink hover:text-accent">
                              {f.hostname}
                            </Link>
                          ) : (
                            <span className="text-ink">{f.hostname ?? '—'}</span>
                          )}
                        </td>
                        <td className="px-3 py-2">{r && <StatusPill tone={r.tono} label={r.etiqueta} />}</td>
                        <td className="px-3 py-2 text-xs text-ink-soft">{f.detalle}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>
    </Modal>
  )
}

function textoConfirmar(nuevos: number, sobrescritos: number): string {
  const partes = []
  if (nuevos > 0) partes.push(`${nuevos} ${nuevos === 1 ? 'nuevo' : 'nuevos'}`)
  if (sobrescritos > 0) partes.push(`sobrescribir ${sobrescritos}`)
  return partes.length ? `Importar ${partes.join(' y ')}` : 'Nada que importar'
}

const BORDE_TONO: Record<StatusTone, string> = {
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
  neutral: 'bg-ink-faint',
}

/** Si recibe onClick, filtra la tabla */
function Contador({
  etiqueta,
  valor,
  tono = 'neutral',
  activo,
  onClick,
}: {
  etiqueta: string
  valor: number
  tono?: StatusTone
  activo?: boolean
  onClick?: () => void
}) {
  const contenido = (
    <>
      <span className="flex items-center gap-1.5 text-xs text-ink-soft">
        <span className={`h-2 w-2 rounded-full ${BORDE_TONO[tono]}`} aria-hidden="true" />
        {etiqueta}
      </span>
      <span className="font-mono text-xl font-semibold tabular-nums text-ink">{valor}</span>
    </>
  )
  const clases = `flex flex-col items-start gap-1 rounded-lg border px-3 py-2 text-left transition ${
    activo ? 'border-accent bg-accent-soft' : 'border-line'
  }`
  return onClick ? (
    <button type="button" onClick={onClick} aria-pressed={activo} className={`${clases} hover:bg-panel-muted`}>
      {contenido}
    </button>
  ) : (
    <div className={clases}>{contenido}</div>
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
