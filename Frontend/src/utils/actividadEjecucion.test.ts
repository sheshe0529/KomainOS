import { describe, expect, it } from 'vitest'
import { deRespuesta, deRespuestas } from './actividadEjecucion'
import { diagnosticarFallo } from './automatizacion'

const fecha = '2026-11-10T23:31:04.125Z'

describe('actividad registrada por el backend (DEC-59)', () => {
  it('conserva la hora exacta, el servidor y la tarea de cada evento', () => {
    expect(deRespuesta({
      clave: 'actividad-7', fecha, fase: 'EJECUCION', nivel: 'INFO', estado: 'COMPLETADO',
      accion: 'Tarea «Liberar temporales» completada', detalle: 'Código de salida 0 · 2 s',
      servidor: 'SERVLNXV01', script: 'Liberar temporales v3', idTarea: 12,
    })).toMatchObject({
      clave: 'actividad-7', fecha, fase: 'EJECUCION', nivel: 'INFO', estado: 'COMPLETADO', servidor: 'SERVLNXV01',
      script: 'Liberar temporales v3', idTarea: 12, detalle: 'Código de salida 0 · 2 s', diagnostico: undefined,
    })
  })

  it('diagnostica los errores y no las advertencias', () => {
    const [fallo, advertencia] = deRespuestas([
      { clave: 'a-1', nivel: 'ERROR', fase: 'EJECUCION', accion: 'Comprobación de acceso fallida',
        error: 'Error de autenticación: el servidor rechazó la cuenta' },
      { clave: 'a-2', nivel: 'WARNING', fase: 'EJECUCION', accion: 'Acceso verificado sin privilegios de administrador',
        detalle: 'Los scripts se ejecutarán como ops (uid 1001)' },
    ])
    expect(fallo.diagnostico).toMatchObject({ tipo: 'AUTENTICACION', titulo: 'Error de autenticación' })
    expect(fallo.diagnostico?.causas?.length).toBeGreaterThan(0)
    expect(advertencia.diagnostico).toBeUndefined()
  })

  it('completa lo que falta con valores seguros', () => {
    expect(deRespuestas(undefined)).toEqual([])
    expect(deRespuesta({}, 3)).toMatchObject({ clave: 'evento-3', nivel: 'INFO', fase: 'ORDEN' })
  })
})

describe('diagnóstico de un fallo registrado', () => {
  it('reconoce el error de sintaxis que el backend extrajo de la salida', () => {
    const error = "Error de ejecución: Script, línea 4: unexpected EOF while looking for matching `''"
    expect(diagnosticarFallo(error)).toMatchObject({
      tipo: 'EJECUCION',
      titulo: 'Error de sintaxis del script',
      detalle: "Script, línea 4: unexpected EOF while looking for matching `''",
      mensajeOriginal: error,
    })
  })

  it('con solo el aviso de cierre SSH busca el error de sintaxis en la salida', () => {
    const error = 'Error de ejecución: Connection to 172.31.37.104 closed.'
    expect(diagnosticarFallo(error)).toMatchObject({ titulo: 'Fallo de ejecución sin causa detallada', mensajeOriginal: error })
    const salida = 'tarea.sh: line 4: unexpected EOF while looking for a matching quote'
    expect(diagnosticarFallo(error, salida)).toMatchObject({ titulo: 'Error de sintaxis del script', detalle: salida })
  })

  it('distingue un comando que no existe en el servidor', () => {
    expect(diagnosticarFallo('Error de ejecución: Script, línea 9: jq: command not found').titulo)
      .toBe('Comando no disponible en el servidor')
  })

  it('explica la métrica faltante con el código que sí informó el script', () => {
    const error = 'Salida no válida: el análisis no informó uptime_dias, que exige un umbral activo. El script informó '
      + '«uptime», que no está en el catálogo: ¿debía ser «uptime_dias»? Los códigos se comparan de forma exacta'
    const d = diagnosticarFallo(error)
    expect(d).toMatchObject({ tipo: 'CONTRATO', titulo: 'Faltan métricas requeridas' })
    expect(d.detalle).toContain('¿debía ser «uptime_dias»?')
    expect(d.sugerencia).toContain('letra por letra')
  })
})
