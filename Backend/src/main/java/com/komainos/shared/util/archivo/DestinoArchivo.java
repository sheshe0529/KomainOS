package com.komainos.shared.util.archivo;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Dónde se escribe un archivo generado por partes. Se abre cuando ya pasaron las validaciones: así un error responde
 * como error y no como una descarga a medias. Quien lo implementa es dueño de la salida y la cierra.
 */
@FunctionalInterface
public interface DestinoArchivo {

    OutputStream abrir(String nombre, FormatoArchivo formato) throws IOException;
}
