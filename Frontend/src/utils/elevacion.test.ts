import { describe, expect, it } from 'vitest'
import { llevaContrasenaElevacion, metodosAdmitidos } from './elevacion'

describe('metodosAdmitidos: la elevación que sirve en cada familia (DEC-57)', () => {
  it('Linux: ninguno, sudo y su', () => {
    expect(metodosAdmitidos('LINUX', 'PASSWORD')).toEqual(['NINGUNO', 'SUDO', 'SU'])
    expect(metodosAdmitidos('LINUX', 'LLAVE_SSH')).toEqual(['NINGUNO', 'SUDO', 'SU'])
  })

  it('Windows: ninguno o runas, que exige contraseña', () => {
    expect(metodosAdmitidos('WINDOWS', 'PASSWORD')).toEqual(['NINGUNO', 'RUNAS'])
  })

  it('cuenta de servicio sin familia: todos, salvo runas con llave SSH', () => {
    expect(metodosAdmitidos(undefined, 'PASSWORD')).toEqual(['NINGUNO', 'SUDO', 'SU', 'RUNAS'])
    expect(metodosAdmitidos(undefined, 'LLAVE_SSH')).toEqual(['NINGUNO', 'SUDO', 'SU'])
  })
})

describe('llevaContrasenaElevacion', () => {
  it('su siempre, sudo solo si la pide, ninguno y runas nunca', () => {
    expect(llevaContrasenaElevacion('SU', false)).toBe(true)
    expect(llevaContrasenaElevacion('SUDO', true)).toBe(true)
    expect(llevaContrasenaElevacion('SUDO', false)).toBe(false)
    expect(llevaContrasenaElevacion('NINGUNO', true)).toBe(false)
    expect(llevaContrasenaElevacion('RUNAS', true)).toBe(false)
  })
})
