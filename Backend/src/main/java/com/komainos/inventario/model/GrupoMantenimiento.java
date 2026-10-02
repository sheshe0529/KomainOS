package com.komainos.inventario.model;

import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.util.Tiempo;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** No persiste criticidad ni ventana: se derivan de sus integrantes (RF21, RF76) */
@Entity
@Table(name = "grupo_mantenimiento")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrupoMantenimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_grupo_mantenimiento")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado", nullable = false, columnDefinition = "enum_estado_grupo")
    private EstadoGrupo estado;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    /** Set y no List: la consulta con ventanas repite cada integrante una vez por intervalo */
    @OneToMany(mappedBy = "grupo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fechaIncorporacion ASC, id ASC")
    private Set<GrupoServidor> integrantes = new LinkedHashSet<>();

    public static GrupoMantenimiento nuevo(String nombre, String descripcion) {
        GrupoMantenimiento grupo = new GrupoMantenimiento();
        grupo.nombre = nombre;
        grupo.descripcion = descripcion;
        grupo.estado = EstadoGrupo.PENDIENTE_DE_CONFIGURACION;
        return grupo;
    }

    public List<Servidor> servidores() {
        return integrantes.stream().map(GrupoServidor::getServidor).toList();
    }

    public boolean contiene(Integer idServidor) {
        return integrantes.stream().anyMatch(i -> i.getServidor().getId().equals(idServidor));
    }

    public void agregar(Servidor servidor, Instant ahora) {
        integrantes.add(GrupoServidor.nuevo(this, servidor, ahora));
    }

    public void quitar(Integer idServidor) {
        integrantes.removeIf(i -> i.getServidor().getId().equals(idServidor));
    }

    /** La más alta entre sus integrantes: menor prioridad = más crítica (DEC-13) */
    public Optional<NivelCriticidad> criticidadEfectiva() {
        return servidores().stream()
                .map(Servidor::getNivelCriticidad)
                .min(Comparator.comparing(NivelCriticidad::getPrioridad));
    }

    public boolean estaInactivo() {
        return estado == EstadoGrupo.INACTIVO;
    }

    /** Guardar la configuración habilita el grupo salvo que esté desactivado (DEC-14) */
    public void alConfigurar() {
        if (estado == EstadoGrupo.PENDIENTE_DE_CONFIGURACION) {
            estado = EstadoGrupo.ACTIVO;
        }
    }

    public void desactivar() {
        if (estaInactivo()) {
            throw new ReglaNegocioException("El grupo %s ya se encuentra inactivo".formatted(nombre));
        }
        estado = EstadoGrupo.INACTIVO;
    }

    /** Vuelve a ACTIVO solo si conserva su configuración */
    public void activar(boolean tieneConfiguracion) {
        if (!estaInactivo()) {
            throw new ReglaNegocioException("El grupo %s no se encuentra inactivo".formatted(nombre));
        }
        estado = tieneConfiguracion ? EstadoGrupo.ACTIVO : EstadoGrupo.PENDIENTE_DE_CONFIGURACION;
    }

    @PrePersist
    void alInsertar() {
        Instant ahora = Tiempo.ahora();
        fechaCreacion = ahora;
        fechaActualizacion = ahora;
    }

    @PreUpdate
    void alActualizar() {
        fechaActualizacion = Tiempo.ahora();
    }
}
