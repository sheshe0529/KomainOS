import { describe, expect, it } from 'vitest'
import type { UmbralServidorRespuesta } from '@/api/types'
import { umbralEnGrupo } from './umbrales'

describe('Umbrales de un integrante dentro del grupo', () => {
  const umbral: UmbralServidorRespuesta = {
    origen: 'ENTORNO',
    aplicado: { valorAdvertencia: 70, valorCritico: 80 },
    global: { valorAdvertencia: 80, valorCritico: 90 },
    enGrupos: [
      { grupo: { id: 1 }, excepcion: { motivo: 'Grupo actual' }, valores: { valorAdvertencia: 65, valorCritico: 75 } },
      { grupo: { id: 2 }, valores: { valorAdvertencia: 50, valorCritico: 60 } },
    ],
  }

  it('muestra los valores y el motivo del grupo consultado, sin mezclar otros grupos', () => {
    expect(umbralEnGrupo(umbral, 1)).toMatchObject({
      origen: 'GRUPO', aplicado: { valorCritico: 75 }, global: { valorCritico: 90 },
      excepcion: { motivo: 'Grupo actual' }, enGrupos: [],
    })
    expect(umbral.enGrupos).toHaveLength(2)
  })

  it('conserva la excepción individual resuelta por el backend cuando tiene prioridad', () => {
    const individual: UmbralServidorRespuesta = {
      ...umbral, origen: 'SERVIDOR', aplicado: { valorCritico: 60 }, enGrupos: [],
      excepcion: { motivo: 'Servidor sensible' },
    }
    expect(umbralEnGrupo(individual, 1)).toEqual(individual)
  })

  it('conserva el valor heredado cuando no existe excepción aplicable de ese grupo', () => {
    expect(umbralEnGrupo(umbral, 3)).toMatchObject({ origen: 'ENTORNO', aplicado: { valorCritico: 80 }, enGrupos: [] })
    expect(umbralEnGrupo({ origen: 'GLOBAL', aplicado: umbral.global }, 3).origen).toBe('GLOBAL')
  })

  it('presenta la lista de referencia que corresponde al grupo', () => {
    const deLista: UmbralServidorRespuesta = {
      enGrupos: [{ grupo: { id: 1 }, valores: { lista: { nombre: 'Servicios BD', elementos: ['postgres'] } } }],
    }
    expect(umbralEnGrupo(deLista, 1).aplicado?.lista?.elementos).toEqual(['postgres'])
  })
})
