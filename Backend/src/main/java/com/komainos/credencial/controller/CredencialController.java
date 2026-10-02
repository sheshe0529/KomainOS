package com.komainos.credencial.controller;

import com.komainos.credencial.config.PropiedadesCredenciales;
import com.komainos.credencial.dto.CredencialPeticion;
import com.komainos.credencial.dto.CredencialRespuesta;
import com.komainos.credencial.dto.DatosCredencialPeticion;
import com.komainos.credencial.dto.RevocacionPeticion;
import com.komainos.credencial.dto.SecretoPeticion;
import com.komainos.credencial.dto.SecretoReveladoRespuesta;
import com.komainos.credencial.mapper.CredencialMapeador;
import com.komainos.credencial.service.ServicioCredenciales;
import com.komainos.credencial.service.ServicioCredenciales.CredencialExportada;
import com.komainos.seguridad.dto.ReautenticacionPeticion;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.shared.util.archivo.EscritorTabular;
import com.komainos.shared.util.archivo.FormatoArchivo;
import com.komainos.seguridad.model.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Las credenciales son exclusivas del administrador (RF04, RF08) */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(name = "Credenciales")
public class CredencialController {

    private final ServicioCredenciales servicio;
    private final PropiedadesCredenciales propiedades;
    private final EscritorTabular escritor;
    private final Clock reloj;

    @GetMapping("/servidores/{idServidor}/credenciales")
    @Operation(summary = "Lista las credenciales documentales del servidor, sin sus secretos (RF04)")
    public List<CredencialRespuesta> documentales(@PathVariable Integer idServidor,
                                                  @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return servicio.documentalesDe(idServidor, AlcanceUsuario.de(solicitante)).stream()
                .map(CredencialMapeador::credencial).toList();
    }

    @PostMapping("/servidores/{idServidor}/credenciales")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una credencial documental del servidor (RF04, HU03)")
    public CredencialRespuesta registrarDocumental(@PathVariable Integer idServidor,
                                                   @Valid @RequestBody CredencialPeticion peticion,
                                                   @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return CredencialMapeador.credencial(
                servicio.registrarDocumental(idServidor, peticion.aDatos(), AlcanceUsuario.de(solicitante)));
    }

    @PostMapping("/credenciales/{id}/principal")
    @Operation(summary = "Marca la credencial documental como la principal del servidor, la que viaja en el inventario (DEC-39)")
    public CredencialRespuesta marcarPrincipal(@PathVariable Integer id,
                                               @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return CredencialMapeador.credencial(servicio.marcarPrincipal(id, AlcanceUsuario.de(solicitante).actor()));
    }

    @PutMapping("/credenciales/{id}")
    @Operation(summary = "Modifica nombre, usuario de acceso y descripción de una credencial (RF04)")
    public CredencialRespuesta actualizarDatos(@PathVariable Integer id,
                                               @Valid @RequestBody DatosCredencialPeticion peticion,
                                               @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return CredencialMapeador.credencial(servicio.actualizarDatos(id, peticion.nombre(), peticion.usuarioAcceso(),
                peticion.descripcion(), AlcanceUsuario.de(solicitante).actor()));
    }

    @PostMapping("/credenciales/{id}/versiones")
    @Operation(summary = "Registra un secreto nuevo como otra versión y conserva las anteriores (RF04)")
    public CredencialRespuesta actualizarSecreto(@PathVariable Integer id, @Valid @RequestBody SecretoPeticion peticion,
                                                 @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return CredencialMapeador.credencial(
                servicio.actualizarSecreto(id, peticion.aSecreto(), AlcanceUsuario.de(solicitante).actor()));
    }

    @PostMapping("/credenciales/{id}/revocacion")
    @Operation(summary = "Revoca la credencial conservando su historial (RF04)")
    public CredencialRespuesta revocar(@PathVariable Integer id, @Valid @RequestBody RevocacionPeticion peticion,
                                       @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return CredencialMapeador.credencial(
                servicio.revocar(id, peticion.motivo(), AlcanceUsuario.de(solicitante).actor()));
    }

    /** POST y no GET: la contraseña viaja en el cuerpo y la respuesta no debe quedar en ninguna caché */
    @PostMapping("/credenciales/{id}/revelado")
    @Operation(summary = "Revela temporalmente el secreto vigente previa reautenticación (RF08, HU05)")
    public ResponseEntity<SecretoReveladoRespuesta> revelar(@PathVariable Integer id,
                                                            @Valid @RequestBody ReautenticacionPeticion peticion,
                                                            @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        var revelado = servicio.revelar(id, peticion.contrasena(), AlcanceUsuario.de(solicitante));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CredencialMapeador.revelado(revelado, propiedades.segundosRevelado()));
    }

    /** Exportación adicional a la del inventario: una fila por credencial vigente de cada servidor (DEC-39) */
    @PostMapping("/credenciales/exportacion")
    @Operation(summary = "Exporta todas las credenciales documentales vigentes, previa reautenticación (RF13, HU05)")
    public ResponseEntity<byte[]> exportar(@RequestParam FormatoArchivo formato,
                                           @Valid @RequestBody ReautenticacionPeticion peticion,
                                           @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        List<CredencialExportada> credenciales = servicio.todasLasDocumentales(AlcanceUsuario.de(solicitante),
                peticion.contrasena());
        byte[] contenido = escritor.escribir(formato, CredencialMapeador.COLUMNAS_EXPORTACION,
                credenciales.stream().map(CredencialMapeador::filaExportacion).toList(), "Credenciales");
        String nombre = "credenciales_servidores_%s.%s".formatted(LocalDate.now(reloj), formato.extension());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(formato.tipoContenido()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(contenido);
    }
}
