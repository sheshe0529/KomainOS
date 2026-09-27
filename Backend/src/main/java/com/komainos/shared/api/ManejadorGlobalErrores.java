package com.komainos.shared.api;

import com.komainos.shared.error.ConflictoException;
import com.komainos.shared.error.RecursoNoEncontradoException;
import com.komainos.shared.error.ReglaNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Traduce las excepciones del dominio a respuestas HTTP con una forma unica.
 *
 * <p>Los servicios lanzan la excepcion que describe el problema de negocio sin
 * conocer codigos HTTP; el panel recibe siempre la misma estructura y puede
 * ramificar sobre {@code codigo}. Todos los mensajes van en espanol (RNF05).
 */
@RestControllerAdvice
@Slf4j
public class ManejadorGlobalErrores {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorRespuesta> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        return construir(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", ex.getMessage(), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorRespuesta> rutaInexistente(NoResourceFoundException ex, HttpServletRequest req) {
        return construir(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "El recurso solicitado no existe", req);
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ErrorRespuesta> conflicto(ConflictoException ex, HttpServletRequest req) {
        return construir(HttpStatus.CONFLICT, "CONFLICTO", ex.getMessage(), req);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorRespuesta> reglaNegocio(ReglaNegocioException ex, HttpServletRequest req) {
        return construir(HttpStatus.UNPROCESSABLE_ENTITY, "REGLA_NEGOCIO", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRespuesta> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErrorRespuesta.ErrorCampo> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorRespuesta.ErrorCampo(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorRespuesta.deValidacion(req.getRequestURI(), campos));
    }

    /**
     * JSON mal formado o un valor que no pertenece a un enumerado (por ejemplo
     * un dia de la semana inexistente). Sin este manejador terminaria como 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRespuesta> cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "VALIDACION",
                "El cuerpo de la petición no es válido o contiene valores no permitidos", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorRespuesta> parametroInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "VALIDACION",
                "El parámetro '%s' tiene un valor no válido".formatted(ex.getName()), req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorRespuesta> parametroFaltante(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "VALIDACION",
                "Falta el parámetro obligatorio '%s'".formatted(ex.getParameterName()), req);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorRespuesta> parteFaltante(MissingServletRequestPartException ex, HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "VALIDACION",
                "Falta el archivo en el campo '%s'".formatted(ex.getRequestPartName()), req);
    }

    /** RF12: el tamaño máximo lo fija {@code spring.servlet.multipart.max-file-size}. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorRespuesta> archivoExcedido(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return construir(HttpStatus.PAYLOAD_TOO_LARGE, "ARCHIVO_EXCEDIDO",
                "El archivo supera el tamaño máximo permitido de 5 MB", req);
    }

    /**
     * Red de seguridad para las restricciones que solo la base puede verificar
     * de forma atomica (unicidad bajo concurrencia, integridad referencial).
     * El servicio igual comprueba antes para dar un mensaje util.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorRespuesta> integridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violacion de integridad en {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return construir(HttpStatus.CONFLICT, "CONFLICTO",
                "La operación entra en conflicto con un registro existente", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorRespuesta> accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        return construir(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                "No tiene permisos para realizar esta operación", req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorRespuesta> noAutenticado(AuthenticationException ex, HttpServletRequest req) {
        return construir(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                "Credenciales inválidas o sesión expirada", req);
    }

    /**
     * Nada de lo inesperado llega al cliente: el detalle va al log y la
     * respuesta lleva un mensaje generico (RNF11).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorRespuesta> inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrió un error inesperado. Consulte al administrador del sistema", req);
    }

    private ResponseEntity<ErrorRespuesta> construir(HttpStatus estado, String codigo,
                                                     String mensaje, HttpServletRequest req) {
        return ResponseEntity.status(estado)
                .body(ErrorRespuesta.de(estado.value(), codigo, mensaje, req.getRequestURI()));
    }
}
