import { consulta, descargar, http } from './cliente'
import type { FiltroServidores } from './inventario'
import type { AnalisisImportacionRespuesta, ColumnaInventarioRespuesta, ResultadoImportacionRespuesta } from './types'

export type FormatoArchivo = 'XLSX' | 'CSV' | 'YAML' | 'JSON'

export const FORMATOS: { valor: FormatoArchivo; etiqueta: string; descripcion: string }[] = [
  { valor: 'XLSX', etiqueta: 'Excel (XLSX)', descripcion: 'Hoja de cálculo con encabezados legibles' },
  { valor: 'CSV', etiqueta: 'CSV', descripcion: 'Texto separado por comas, UTF-8' },
  { valor: 'JSON', etiqueta: 'JSON', descripcion: 'Lista de objetos para integraciones' },
  { valor: 'YAML', etiqueta: 'YAML', descripcion: 'Lista legible para configuración' },
]

/** Tamaño máximo aceptado por el backend (spring.servlet.multipart.max-file-size). */
export const TAMANO_MAXIMO_BYTES = 5 * 1024 * 1024

/** Importación (RF12, HU08) y exportación (RF13, HU09) del inventario. */
export const intercambioApi = {
  columnas: () => http.get<ColumnaInventarioRespuesta[]>('/servidores/exportacion/columnas'),

  /** Exporta con los mismos filtros del listado; devuelve el nombre del archivo descargado. */
  exportar: (formato: FormatoArchivo, columnas: string[], f: FiltroServidores & { texto?: string }) =>
    descargar(
      `/servidores/exportacion${consulta({
        formato,
        columnas,
        texto: f.texto,
        estado: f.estado,
        idEntorno: f.idEntorno,
        idNivelCriticidad: f.idNivelCriticidad,
        idSistemaOperativo: f.idSistemaOperativo,
        idResponsable: f.idResponsable,
      })}`,
      `inventario_servidores.${formato.toLowerCase()}`,
    ),

  plantilla: (formato: FormatoArchivo) =>
    descargar(`/servidores/importacion/plantilla${consulta({ formato })}`, `plantilla.${formato.toLowerCase()}`),

  analizar: (archivo: File) => {
    const formulario = new FormData()
    formulario.append('archivo', archivo)
    return http.enviarArchivo<AnalisisImportacionRespuesta>('/servidores/importacion/analisis', formulario)
  },

  /** Envía de nuevo el archivo con las filas duplicadas cuya sobrescritura se confirmó (HU08 CA4). */
  importar: (archivo: File, sobrescribir: number[]) => {
    const formulario = new FormData()
    formulario.append('archivo', archivo)
    sobrescribir.forEach((fila) => formulario.append('sobrescribir', String(fila)))
    return http.enviarArchivo<ResultadoImportacionRespuesta>('/servidores/importacion', formulario)
  },
}
