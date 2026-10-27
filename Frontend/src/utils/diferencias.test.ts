import { describe, expect, it } from 'vitest'
import { diferenciasPorLinea, resumenDiferencias } from './diferencias'

describe('diferenciasPorLinea: cambios entre versiones de un script (RF26)', () => {
  it('marca solo lo agregado y lo quitado, con la numeración de cada versión', () => {
    const lineas = diferenciasPorLinea('a\nb\nc\nd', 'a\nB\nc\nd\ne')!
    expect(lineas.map((l) => `${l.tipo}:${l.texto}`)).toEqual([
      'igual:a',
      'quitada:b',
      'agregada:B',
      'igual:c',
      'igual:d',
      'agregada:e',
    ])
    expect(lineas.find((l) => l.texto === 'c')).toMatchObject({ antes: 3, despues: 3 })
    const agregada = lineas.find((l) => l.texto === 'e')
    expect(agregada?.despues).toBe(5)
    expect(agregada?.antes).toBeUndefined()
    expect(resumenDiferencias(lineas)).toEqual({ agregadas: 2, quitadas: 1 })
  })

  it('ignora el fin de línea de Windows y detecta el código idéntico', () => {
    const lineas = diferenciasPorLinea('echo uno\r\necho dos', 'echo uno\necho dos')!
    expect(resumenDiferencias(lineas)).toEqual({ agregadas: 0, quitadas: 0 })
  })

  it('compara desde una versión vacía', () => {
    expect(resumenDiferencias(diferenciasPorLinea('', 'x\ny')!)).toEqual({ agregadas: 2, quitadas: 1 })
  })
})
