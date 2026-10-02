package com.komainos.inventario.controller;

import com.komainos.inventario.dto.AnalisisImportacionRespuesta;
import com.komainos.inventario.dto.ColumnaInventarioRespuesta;
import com.komainos.inventario.dto.ResultadoImportacionRespuesta;
import com.komainos.inventario.mapper.IntercambioMapeador;
import com.komainos.inventario.model.EstadoServidor;
import com.komainos.inventario.model.FiltroServidores;
import com.komainos.inventario.service.intercambio.ColumnaInventario;
import com.komainos.inventario.service.intercambio.ServicioExportacionInventario;
import com.komainos.inventario.service.intercambio.ServicioImportacionInventario;
import com.komainos.seguridad.dto.ReautenticacionPeticion;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.util.archivo.ArchivoGenerado;
import com.komainos.shared.util.archivo.FormatoArchivo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/api/servidores")
@RequiredArgsConstructor
@Tag(name = "Importación y exportación del inventario")
public class IntercambioInventarioController {

    private final ServicioExportacionInventario exportacion;
    private final ServicioImportacionInventario importacion;

    @GetMapping("/exportacion/columnas")
    @Operation(summary = "Columnas que el usuario puede exportar (HU09 CA2)")
    public List<ColumnaInventarioRespuesta> columnas(@AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return exportacion.columnasAutorizadas(AlcanceUsuario.de(solicitante)).stream()
                .map(IntercambioMapeador::columna)
                .toList();
    }

    @GetMapping("/exportacion")
    @Operation(summary = "Exporta el inventario visible con el filtro del listado (RF13)")
    public ResponseEntity<byte[]> exportar(
            @RequestParam FormatoArchivo formato,
            @RequestParam(required = false) List<String> columnas,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoServidor estado,
            @RequestParam(required = false) Integer idEntorno,
            @RequestParam(required = false) Integer idNivelCriticidad,
            @RequestParam(required = false) Integer idSistemaOperativo,
            @RequestParam(required = false) Integer idResponsable,
            @RequestParam(required = false) String vdc,
            @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        var filtro = new FiltroServidores(texto, estado, idEntorno, idNivelCriticidad, idSistemaOperativo,
                idResponsable, vdc);
        List<ColumnaInventario> elegidas = columnas == null ? List.of() : columnas.stream()
                .map(IntercambioInventarioController::columnaDe)
                .toList();
        return descarga(exportacion.exportar(filtro, elegidas, formato, AlcanceUsuario.de(solicitante)));
    }

    /** POST y no GET: la contraseña viaja en el cuerpo y el archivo no debe quedar en ninguna caché */
    @PostMapping("/exportacion/con-credencial")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Exporta el inventario con la credencial principal de cada servidor, previa reautenticación (RF13, HU05)")
    public ResponseEntity<byte[]> exportarConCredencial(
            @RequestParam FormatoArchivo formato,
            @RequestParam(required = false) List<String> columnas,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoServidor estado,
            @RequestParam(required = false) Integer idEntorno,
            @RequestParam(required = false) Integer idNivelCriticidad,
            @RequestParam(required = false) Integer idSistemaOperativo,
            @RequestParam(required = false) Integer idResponsable,
            @RequestParam(required = false) String vdc,
            @Valid @RequestBody ReautenticacionPeticion peticion,
            @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        var filtro = new FiltroServidores(texto, estado, idEntorno, idNivelCriticidad, idSistemaOperativo,
                idResponsable, vdc);
        List<ColumnaInventario> elegidas = columnas == null ? List.of() : columnas.stream()
                .map(IntercambioInventarioController::columnaDe)
                .toList();
        return sinCache(descarga(exportacion.exportarConCredencial(filtro, elegidas, formato,
                AlcanceUsuario.de(solicitante), peticion.contrasena())));
    }

    @GetMapping("/importacion/plantilla")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Plantilla con las columnas importables (RF12)")
    public ResponseEntity<byte[]> plantilla(@RequestParam FormatoArchivo formato) {
        return descarga(importacion.plantilla(formato));
    }

    @PostMapping(path = "/importacion/analisis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Clasifica los registros del archivo sin importarlos (HU08 CA2)")
    public AnalisisImportacionRespuesta analizar(@RequestPart("archivo") MultipartFile archivo) {
        return IntercambioMapeador.analisis(importacion.analizar(nombreDe(archivo), contenidoDe(archivo)));
    }

    @PostMapping(path = "/importacion", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Importa los registros válidos y los duplicados confirmados (HU08 CA3, CA4)")
    public ResultadoImportacionRespuesta importar(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(required = false) List<Integer> sobrescribir,
            @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        var resultado = importacion.importar(nombreDe(archivo), contenidoDe(archivo),
                sobrescribir == null ? null : new HashSet<>(sobrescribir),
                AlcanceUsuario.de(solicitante).actor());
        return IntercambioMapeador.resultado(resultado);
    }

    private static ColumnaInventario columnaDe(String clave) {
        ColumnaInventario columna = ColumnaInventario.deClave(clave);
        if (columna == null) {
            throw new ReglaNegocioException("La columna «%s» no existe en el inventario".formatted(clave));
        }
        return columna;
    }

    /** Solo el nombre: algunos navegadores envían la ruta completa del archivo */
    private static String nombreDe(MultipartFile archivo) {
        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename();
        nombre = nombre.substring(Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\')) + 1);
        return nombre.length() > 200 ? nombre.substring(nombre.length() - 200) : nombre;
    }

    private static byte[] contenidoDe(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer el archivo recibido", ex);
        }
    }

    private static ResponseEntity<byte[]> sinCache(ResponseEntity<byte[]> respuesta) {
        return ResponseEntity.status(respuesta.getStatusCode())
                .headers(respuesta.getHeaders())
                .cacheControl(CacheControl.noStore())
                .body(respuesta.getBody());
    }

    private static ResponseEntity<byte[]> descarga(ArchivoGenerado archivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.formato().tipoContenido()))
                // Los nombres generados son ASCII: basta el filename simple
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(archivo.nombre())
                        .build()
                        .toString())
                .body(archivo.contenido());
    }
}
