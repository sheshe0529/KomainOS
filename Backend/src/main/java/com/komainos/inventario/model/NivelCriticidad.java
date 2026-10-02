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

/** prioridad es única: menor valor = mayor criticidad (DEC-13) */
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

    /** Anticipación de la evaluación previa y plazo de autorización (RF38, RF75) */
    @Column(name = "plazo_autorizacion_horas", nullable = false)
    private Integer plazoAutorizacionHoras;

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
