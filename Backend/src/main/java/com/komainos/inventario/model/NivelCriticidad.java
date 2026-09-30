package com.komainos.inventario.model;

import com.komainos.shared.exception.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Nivel de criticidad con frecuencias recomendadas y plazos (RF15), tabla
 * {@code nivel_criticidad}.
 *
 * <p>Las frecuencias se copian a la configuracion de mantenimiento al crearla;
 * editarlas aqui no reescribe configuraciones existentes (HU11 CA2).
 *
 * <p>{@code prioridad} es unica y ordena los niveles: <b>menor valor = mayor
 * criticidad</b> (DEC-13). Se usa para la criticidad de un grupo (RF76) y para
 * decidir que orden toma primero un intervalo en la planificacion (RF28).
 */
@Entity
@Table(name = "nivel_criticidad")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NivelCriticidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_nivel_criticidad")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "prioridad", nullable = false)
    private Integer prioridad;

    @Column(name = "frecuencia_revision_dias", nullable = false)
    private Integer frecuenciaRevisionDias;

    @Column(name = "frecuencia_mantenimiento_dias", nullable = false)
    private Integer frecuenciaMantenimientoDias;

    /** Anticipacion de la evaluacion previa y plazo de autorizacion (RF38, RF75). */
    @Column(name = "plazo_autorizacion_horas", nullable = false)
    private Integer plazoAutorizacionHoras;

    /** Plazo de la validacion funcional del responsable (RF56). */
    @Column(name = "plazo_validacion_horas", nullable = false)
    private Integer plazoValidacionHoras;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    public static NivelCriticidad nuevo(DatosNivelCriticidad datos) {
        NivelCriticidad nivel = new NivelCriticidad();
        nivel.aplicar(datos);
        nivel.activo = true;
        return nivel;
    }

    public void aplicar(DatosNivelCriticidad datos) {
        nombre = datos.nombre();
        prioridad = datos.prioridad();
        frecuenciaRevisionDias = datos.frecuenciaRevisionDias();
        frecuenciaMantenimientoDias = datos.frecuenciaMantenimientoDias();
        plazoAutorizacionHoras = datos.plazoAutorizacionHoras();
        plazoValidacionHoras = datos.plazoValidacionHoras();
    }

    /** Verdadero si este nivel es mas critico que el otro (DEC-13). */
    public boolean esMasCriticoQue(NivelCriticidad otro) {
        return otro == null || prioridad < otro.prioridad;
    }

    public void activar() {
        if (activo) {
            throw new ReglaNegocioException("El nivel de criticidad %s ya se encuentra activo".formatted(nombre));
        }
        activo = true;
    }

    public void desactivar() {
        if (!activo) {
            throw new ReglaNegocioException("El nivel de criticidad %s ya se encuentra inactivo".formatted(nombre));
        }
        activo = false;
    }

    public record DatosNivelCriticidad(String nombre, int prioridad, int frecuenciaRevisionDias,
                                       int frecuenciaMantenimientoDias, int plazoAutorizacionHoras,
                                       int plazoValidacionHoras) {
    }
}
