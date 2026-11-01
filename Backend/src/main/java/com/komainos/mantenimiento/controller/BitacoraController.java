package com.komainos.mantenimiento.controller;

import com.komainos.mantenimiento.service.ServicioBitacora;
import com.komainos.mantenimiento.service.ServicioBitacora.Bitacora;
import com.komainos.mantenimiento.service.ServicioBitacora.EventoBitacora;
import com.komainos.mantenimiento.service.ServicioBitacora.EventosBitacora;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.shared.util.archivo.ColumnaArchivo;
import com.komainos.shared.util.archivo.EscritorTabular;
import com.komainos.shared.util.archivo.FormatoArchivo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bitácora de mantenimiento (RF53) de una orden, de un servidor o de un grupo, para verla y exportarla. El alcance del
 * responsable se aplica igual que en la ficha de cada orden: solo ve sus servidores.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Bitácora de mantenimiento")
public class BitacoraController {

    static final List<ColumnaArchivo> COLUMNAS = List.of(
            new ColumnaArchivo("fecha", "Fecha"),
            new ColumnaArchivo("orden", "Orden"),
            new ColumnaArchivo("evento", "Evento"),
            new ColumnaArchivo("servidor", "Servidor"),
            new ColumnaArchivo("descripcion", "Descripción"),
            new ColumnaArchivo("resultado", "Resultado"),
            new ColumnaArchivo("usuario", "Usuario o cuenta"),
            new ColumnaArchivo("detalle", "Detalle"));

    /** Fecha y hora local: así se lee en una hoja de cálculo y se ordena como texto */
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("America/Lima"));
    /** Límite de una celda de Excel, con margen */
    private static final int LARGO_MAXIMO = 32_000;

    private final ServicioBitacora servicio;
    private final EscritorTabular escritor;
    private final Clock reloj;

    @GetMapping("/ordenes/{id}/bitacora/exportacion")
    @Operation(summary = "Exporta la bitácora de la orden: estados, análisis, decisiones, tareas, validaciones e incidencias")
    public ResponseEntity<byte[]> exportarOrden(@PathVariable Integer id, @RequestParam FormatoArchivo formato,
                                                @AuthenticationPrincipal UsuarioAutenticado u) {
        return archivo(formato, servicio.eventosDeOrden(id, AlcanceUsuario.de(u)), "bitacora_");
    }

    @GetMapping("/servidores/{id}/bitacora")
    @Operation(summary = "Mantenimientos del servidor, individuales y de grupo, con el resultado de cada uno")
    public Bitacora servidor(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado u) {
        return servicio.delServidor(id, AlcanceUsuario.de(u));
    }

    @GetMapping("/servidores/{id}/bitacora/exportacion")
    @Operation(summary = "Exporta la bitácora de todos los mantenimientos del servidor")
    public ResponseEntity<byte[]> exportarServidor(@PathVariable Integer id, @RequestParam FormatoArchivo formato,
                                                   @AuthenticationPrincipal UsuarioAutenticado u) {
        return archivo(formato, servicio.eventosDelServidor(id, AlcanceUsuario.de(u)), "bitacora_");
    }

    @GetMapping("/grupos/{id}/bitacora")
    @Operation(summary = "Mantenimientos del grupo con el resultado de cada integrante")
    public Bitacora grupo(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado u) {
        return servicio.delGrupo(id, AlcanceUsuario.de(u));
    }

    @GetMapping("/grupos/{id}/bitacora/exportacion")
    @Operation(summary = "Exporta la bitácora de los mantenimientos del grupo, completa o de un integrante")
    public ResponseEntity<byte[]> exportarGrupo(@PathVariable Integer id, @RequestParam FormatoArchivo formato,
                                                @RequestParam(required = false) Integer idServidor,
                                                @AuthenticationPrincipal UsuarioAutenticado u) {
        return archivo(formato, servicio.eventosDelGrupo(id, idServidor, AlcanceUsuario.de(u)), "bitacora_grupo_");
    }

    private ResponseEntity<byte[]> archivo(FormatoArchivo formato, EventosBitacora bitacora, String prefijo) {
        byte[] contenido = escritor.escribir(formato, COLUMNAS,
                bitacora.eventos().stream().map(BitacoraController::fila).toList(), "Bitácora");
        String nombre = "%s%s_%s.%s".formatted(prefijo, bitacora.objetivo().replaceAll("[^\\p{L}\\p{N}._-]+", "_"),
                LocalDate.now(reloj), formato.extension());
        // Con filename* en UTF-8: el nombre de un grupo puede llevar tildes
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(formato.tipoContenido()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nombre, StandardCharsets.UTF_8).build().toString())
                .body(contenido);
    }

    static Map<String, ?> fila(EventoBitacora e) {
        Map<String, String> fila = new LinkedHashMap<>();
        fila.put("fecha", e.fecha() == null ? null : FECHA.format(e.fecha()));
        fila.put("orden", e.orden());
        fila.put("evento", e.evento());
        fila.put("servidor", e.servidor());
        fila.put("descripcion", e.descripcion());
        fila.put("resultado", e.resultado());
        fila.put("usuario", e.usuario());
        fila.put("detalle", e.detalle() == null || e.detalle().length() <= LARGO_MAXIMO ? e.detalle()
                : e.detalle().substring(0, LARGO_MAXIMO) + "…");
        return fila;
    }
}
