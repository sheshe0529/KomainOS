import { describe, expect, it } from 'vitest'
import { detalleFallo, diagnosticarFallo, diagnosticarResultado, filaCsv, formatearSegundos } from './automatizacion'

describe('fallos de conexión con causa precisa (RF71)', () => {
  const certificado =
    "Task failed: ntlm: HTTPSConnectionPool(host='172.31.39.120', port=5986): Max retries exceeded with url: /wsman "
    + "(Caused by SSLError(SSLCertVerificationError(1, '[SSL: CERTIFICATE_VERIFY_FAILED] certificate verify failed: "
    + "self-signed certificate (_ssl.c:1000)'))) — El certificado WinRM no es de confianza para el controlador"

  it('un certificado WinRM autofirmado no sugiere revisar la IP ni el firewall', () => {
    const d = diagnosticarResultado('COMUNICACION', certificado)
    expect(d).toMatchObject({ tipo: 'COMUNICACION', titulo: 'Certificado WinRM no confiable' })
    expect(d.detalle).toBe(
      'El servidor respondió en el puerto WinRM, pero su certificado HTTPS es autofirmado y el controlador no confía en él.',
    )
    expect(d.sugerencia).toContain('despliegue/confiar-winrm.sh')
    expect(d.sugerencia).not.toMatch(/firewall|dirección IP/)
    expect(d.mensajeOriginal).toContain('self-signed certificate')
  })

  it('el mismo diagnóstico aparece en la actividad, con el tipo como prefijo', () => {
    expect(diagnosticarFallo(`Error de comunicación: ${certificado}`).titulo).toBe('Certificado WinRM no confiable')
  })

  it('un certificado vencido pide renovarlo', () => {
    const d = diagnosticarResultado('COMUNICACION', 'certificate verify failed: certificate has expired')
    expect(d.detalle).toContain('venció')
    expect(d.sugerencia).toMatch(/^Renueve/)
  })

  it('una clave de host SSH no registrada pide registrarla', () => {
    const d = diagnosticarResultado('COMUNICACION', 'Failed to connect to the host via ssh: Host key verification failed.')
    expect(d.titulo).toBe('Clave SSH del servidor no verificada')
    expect(d.sugerencia).toContain('known_hosts')
  })

  it('sin causa precisa conserva la sugerencia general del tipo de fallo', () => {
    const d = diagnosticarResultado('COMUNICACION', 'ssh: connect to host 10.0.0.5 port 22: Connection timed out')
    expect(d).toMatchObject({ titulo: 'Error de comunicación', detalle: 'Ssh: connect to host 10.0.0.5 port 22: Connection timed out' })
    expect(d.sugerencia).toContain('firewall')
    expect(diagnosticarResultado('AUTENTICACION', undefined)).toMatchObject({ titulo: 'Error de autenticación' })
  })
})

describe('presentación de la ejecución', () => {
  it('no repite el tipo de fallo que ya se muestra como título (RF71)', () => {
    expect(detalleFallo('AUTENTICACION', 'Error de autenticación: el servidor rechazó la cuenta')).toBe(
      'El servidor rechazó la cuenta',
    )
    expect(detalleFallo('PERMISOS', 'Otro mensaje sin prefijo')).toBe('Otro mensaje sin prefijo')
    expect(detalleFallo(undefined, 'Sin tipo')).toBe('Sin tipo')
  })

  it('formatea duraciones de tareas en segundos', () => {
    expect(formatearSegundos(0)).toBe('0 s')
    expect(formatearSegundos(65)).toBe('1 min 05 s')
    expect(formatearSegundos(120)).toBe('2 min')
    expect(formatearSegundos(3725)).toBe('1 h 2 min')
    expect(formatearSegundos(undefined)).toBe('—')
  })

  it('arma filas CSV con punto y coma y comillas solo donde hacen falta', () => {
    expect(filaCsv(['a', 1, undefined, 'x;y', 'dijo "hola"'])).toBe('a;1;;"x;y";"dijo ""hola"""')
  })
})
