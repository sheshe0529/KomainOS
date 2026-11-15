/** Copia desde una acción del usuario, también cuando el navegador no expone la API de portapapeles. */
export async function copiarTexto(texto: string): Promise<void> {
  if (typeof navigator.clipboard?.writeText === 'function') {
    try {
      await navigator.clipboard.writeText(texto)
      return
    } catch {
      // Algunos navegadores deniegan esta API, pero permiten copiar la selección del documento.
    }
  }

  const focoAnterior = document.activeElement instanceof HTMLElement ? document.activeElement : null
  const campo = document.createElement('textarea')
  campo.value = texto
  campo.readOnly = true
  campo.tabIndex = -1
  campo.style.cssText = 'position:fixed;top:0;left:0;width:1px;height:1px;padding:0;border:0;opacity:0;font-size:16px;'
  // Fuera de un dialog modal el documento es inerte: la selección debe permanecer dentro del modal abierto.
  const contenedor = focoAnterior?.closest('dialog[open]') ?? document.querySelector('dialog[open]') ?? document.body

  try {
    contenedor.appendChild(campo)
    campo.focus({ preventScroll: true })
    campo.select()
    if (!document.execCommand('copy')) throw new Error('Copia rechazada')
  } catch {
    throw new Error('El navegador no permitió la copia. Seleccione el texto y cópielo manualmente.')
  } finally {
    // La contraseña o llave privada no debe permanecer en un elemento auxiliar del documento.
    campo.value = ''
    campo.remove()
    if (focoAnterior?.isConnected) focoAnterior.focus({ preventScroll: true })
  }
}
