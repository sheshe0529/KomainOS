import { describe, expect, it } from 'vitest'
import { matchPath } from 'react-router-dom'
import { isNavGroup, navigation, navegacionPara } from './navigation'

const hojas = navigation.flatMap((item) => (isNavGroup(item) ? item.children : [item]))
function activas(ruta: string) {
  return hojas.filter((h) => matchPath({ path: h.path, end: h.exact ?? false }, ruta)).map((h) => h.id)
}
describe('Selección y ubicación del menú', () => {
  it.each([
    ['/automatizacion', 'sala'],
    ['/automatizacion/scripts', 'scripts'],
    ['/automatizacion/tareas', 'tareas'],
    ['/automatizacion/incidencias', 'incidencias'],
    ['/configuracion/politicas-cambio', 'politicas'],
    ['/servidores/12', 'servidores'],
  ])('selecciona una sola sección para %s', (ruta, id) => expect(activas(ruta)).toEqual([id]))
  it('ubica las políticas dentro de Configuración', () => {
    const grupo = navigation.find((g) => isNavGroup(g) && g.children.some((h) => h.id === 'politicas'))
    expect(grupo?.id).toBe('configuracion')
  })
  it('el responsable no recibe las políticas administrativas', () => {
    const visibles = navegacionPara('RESPONSABLE').flatMap((g) => (isNavGroup(g) ? g.children : [g]))
    expect(visibles.some((h) => h.id === 'politicas')).toBe(false)
  })
})
