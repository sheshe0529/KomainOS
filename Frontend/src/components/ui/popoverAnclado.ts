import { useCallback, useEffect, useRef, useState } from 'react'
import type { RefObject } from 'react'

interface OpcionesPopover {
  /** Alto máximo del panel; si no cabe, se desplaza por dentro */
  altoMaximo?: number
  /** Al abrir, por ejemplo para enfocar la opción elegida */
  alAbrir?: () => void
  /** A lo ancho de su contenido (una lista de opciones); si no, el ancho lo fija su clase */
  anchoContenido?: boolean
}

/**
 * Ubica un popover nativo junto a su disparador: debajo si cabe, encima si hay más espacio, y lo sigue si la página o
 * el diálogo se desplazan. Al cerrarse devuelve el foco al disparador. Lo comparten los desplegables del panel
 */
export function usePopoverAnclado(
  disparador: RefObject<HTMLElement | null>,
  panel: RefObject<HTMLElement | null>,
  { altoMaximo = 288, alAbrir, anchoContenido = false }: OpcionesPopover = {},
) {
  const [abierto, setAbierto] = useState(false)
  // El manejador del evento toggle se registra una sola vez y llama a la versión vigente
  const alAbrirRef = useRef(alAbrir)
  useEffect(() => {
    alAbrirRef.current = alAbrir
  })

  /**
   * Se posiciona de forma imperativa antes de mostrarse, para que no parpadee, y otra vez ya abierto: cerrado no tiene
   * tamaño, así que antes de abrir se estima. Siempre queda dentro de la ventana: si no cabe debajo se abre encima, y
   * si es más ancho que el disparador se corre hacia la izquierda
   */
  const ubicar = useCallback(() => {
    const el = panel.current
    const r = disparador.current?.getBoundingClientRect()
    if (!el || !r) return
    const medido = el.offsetWidth > 0
    // Cerrado se estima por sus elementos (las opciones de un selector miden unos 32 px)
    const altoNecesario = medido ? el.scrollHeight : Math.max(120, el.childElementCount * 32 + 8)
    const espacioAbajo = window.innerHeight - r.bottom - 12
    const espacioArriba = r.top - 12
    const haciaArriba = espacioAbajo < Math.min(altoNecesario, altoMaximo) && espacioArriba > espacioAbajo
    const alto = Math.max(0, Math.min(altoMaximo, haciaArriba ? espacioArriba : espacioAbajo))
    const anchoMaximo = window.innerWidth - 16
    const ancho = Math.min(anchoMaximo, Math.max(r.width, medido ? el.offsetWidth : r.width))
    Object.assign(el.style, {
      left: `${Math.max(8, Math.min(r.left, window.innerWidth - ancho - 8))}px`,
      // A lo ancho de su contenido, sin pasar de la ventana: antes de partir una opción en dos líneas se corre a la izquierda
      width: anchoContenido ? 'max-content' : '',
      minWidth: `${Math.min(r.width, anchoMaximo)}px`,
      maxWidth: `${anchoMaximo}px`,
      maxHeight: `${alto}px`,
      top: haciaArriba ? 'auto' : `${r.bottom + 4}px`,
      bottom: haciaArriba ? `${window.innerHeight - r.top + 4}px` : 'auto',
      right: 'auto',
    })
  }, [disparador, panel, altoMaximo, anchoContenido])

  useEffect(() => {
    const el = panel.current
    if (!el) return
    // Al cerrar, el foco vuelve al disparador salvo que el usuario haya hecho clic en otro control. Dentro de un
    // <dialog> no basta con mirar el foco después de cerrar: el navegador ya lo movió a otro control del diálogo
    let teniaFoco = false
    let porClicAfuera = false
    const alPresionar = (e: PointerEvent) => {
      const destino = e.target as Node
      porClicAfuera = !el.contains(destino) && !disparador.current?.contains(destino)
    }
    const antes = (e: Event) => {
      if ((e as Event & { newState?: string }).newState === 'open') {
        porClicAfuera = false
        ubicar()
      } else {
        teniaFoco = el.contains(document.activeElement)
      }
    }
    const despues = (e: Event) => {
      const abiertoAhora = (e as Event & { newState?: string }).newState === 'open'
      setAbierto(abiertoAhora)
      if (abiertoAhora) {
        // Ya abierto tiene su tamaño real: se corrige la estimación
        ubicar()
        alAbrirRef.current?.()
        return
      }
      const foco = document.activeElement
      if ((teniaFoco && !porClicAfuera) || el.contains(foco) || foco === document.body) {
        disparador.current?.focus()
      }
    }
    document.addEventListener('pointerdown', alPresionar, true)
    el.addEventListener('beforetoggle', antes)
    el.addEventListener('toggle', despues)
    return () => {
      document.removeEventListener('pointerdown', alPresionar, true)
      el.removeEventListener('beforetoggle', antes)
      el.removeEventListener('toggle', despues)
    }
  }, [ubicar, panel, disparador])

  // Mientras está abierto, sigue al control si la página o el diálogo se desplazan
  useEffect(() => {
    if (!abierto) return
    window.addEventListener('resize', ubicar)
    window.addEventListener('scroll', ubicar, true)
    return () => {
      window.removeEventListener('resize', ubicar)
      window.removeEventListener('scroll', ubicar, true)
    }
  }, [abierto, ubicar])

  return abierto
}
