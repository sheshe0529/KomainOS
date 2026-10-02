package com.komainos.inventario.service;

/** Credencial principal tal como viaja en el archivo del inventario: textos de las celdas, los secretos sin recortar */
public record CredencialArchivo(String mecanismo, String tipoUsuario, String usuario, String secreto, String secretoSu) {
}
