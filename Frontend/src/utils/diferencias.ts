export interface LineaDiferencia {
  tipo: 'igual' | 'agregada' | 'quitada'
  texto: string
  /** Número de línea en la versión anterior (igual o quitada) */
  antes?: number
  /** Número de línea en la versión nueva (igual o agregada) */
  despues?: number
}

/** Por encima de este producto de líneas la tabla de la subsecuencia común ocupa demasiada memoria */
const LIMITE_CELDAS = 4_000_000

/**
 * Diferencias línea a línea por subsecuencia común más larga. Basta para scripts de mantenimiento, que rara vez pasan
 * de unos cientos de líneas; devuelve null si son demasiado grandes para compararlos en el navegador
 */
export function diferenciasPorLinea(anterior: string, nuevo: string): LineaDiferencia[] | null {
  const a = anterior.replace(/\r\n/g, '\n').split('\n')
  const b = nuevo.replace(/\r\n/g, '\n').split('\n')

  // Las líneas iguales al inicio y al final no entran en la tabla
  let inicio = 0
  while (inicio < a.length && inicio < b.length && a[inicio] === b[inicio]) inicio++
  let finA = a.length
  let finB = b.length
  while (finA > inicio && finB > inicio && a[finA - 1] === b[finB - 1]) {
    finA--
    finB--
  }

  const n = finA - inicio
  const m = finB - inicio
  if (n * m > LIMITE_CELDAS) return null

  // largo[i][j]: subsecuencia común más larga entre a[inicio+i..] y b[inicio+j..]
  const largo = Array.from({ length: n + 1 }, () => new Uint32Array(m + 1))
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      largo[i][j] =
        a[inicio + i] === b[inicio + j] ? largo[i + 1][j + 1] + 1 : Math.max(largo[i + 1][j], largo[i][j + 1])
    }
  }

  const resultado: LineaDiferencia[] = []
  for (let k = 0; k < inicio; k++) resultado.push({ tipo: 'igual', texto: a[k], antes: k + 1, despues: k + 1 })

  let i = 0
  let j = 0
  while (i < n || j < m) {
    if (i < n && j < m && a[inicio + i] === b[inicio + j]) {
      resultado.push({ tipo: 'igual', texto: a[inicio + i], antes: inicio + i + 1, despues: inicio + j + 1 })
      i++
      j++
    } else if (i < n && (j === m || largo[i + 1][j] >= largo[i][j + 1])) {
      // Como en git, lo quitado se muestra antes que lo agregado en su lugar
      resultado.push({ tipo: 'quitada', texto: a[inicio + i], antes: inicio + i + 1 })
      i++
    } else {
      resultado.push({ tipo: 'agregada', texto: b[inicio + j], despues: inicio + j + 1 })
      j++
    }
  }

  for (let k = 0; k < a.length - finA; k++) {
    resultado.push({ tipo: 'igual', texto: a[finA + k], antes: finA + k + 1, despues: finB + k + 1 })
  }
  return resultado
}

export function resumenDiferencias(lineas: LineaDiferencia[]): { agregadas: number; quitadas: number } {
  return {
    agregadas: lineas.filter((l) => l.tipo === 'agregada').length,
    quitadas: lineas.filter((l) => l.tipo === 'quitada').length,
  }
}
