package com.komainos.inventario.service.intercambio;

import com.komainos.inventario.model.Servidor;
import com.komainos.shared.util.archivo.ClaveColumna;
import com.komainos.shared.util.archivo.ColumnaArchivo;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Columnas del inventario para exportar (RF13, HU09) e importar (RF12, HU08).
 *
 * <p>La clave es la etiqueta normalizada ({@link ClaveColumna}): así el mismo
 * archivo se entiende con encabezados legibles en XLSX y CSV y con claves en
 * JSON y YAML. Las referencias a catálogos se intercambian por nombre y el
 * responsable por su código de usuario, porque los identificadores internos no
 * significan nada fuera del sistema.
 *
 * <p>Ninguna columna contiene credenciales: incluirlas depende del revelado con
 * reautenticación de RF08, que no forma parte de esta iteración (DEC-31).
 */
public enum ColumnaInventario {

    HOSTNAME("Hostname", Uso.OBLIGATORIA, Servidor::getHostname),
    /** IP principal (DEC-37). */
    DIRECCION_IP("Dirección IP", Uso.OBLIGATORIA, Servidor::getDireccionIp),
    /** Las demás IP del servidor, separadas por «;». */
    IPS_ADICIONALES("IPs adicionales", Uso.OPCIONAL,
            s -> s.direccionesAdicionales().isEmpty() ? null : String.join("; ", s.direccionesAdicionales())),
    ESTADO("Estado", Uso.SOLO_EXPORTACION, s -> s.getEstado().name()),
    VDC("VDC", Uso.OPCIONAL, Servidor::getVdc),
    SERVIDOR_FISICO("Servidor físico", Uso.OPCIONAL, Servidor::getServidorFisico),
    VLAN("VLAN", Uso.OPCIONAL, Servidor::getVlan),
    CLUSTER("Clúster", Uso.OPCIONAL, Servidor::getCluster),
    DNS("DNS", Uso.OPCIONAL, Servidor::getDns),
    SISTEMA_OPERATIVO("Sistema operativo", Uso.OBLIGATORIA,
            s -> s.getVersionSistemaOperativo().getSistemaOperativo().getNombre()),
    VERSION("Versión", Uso.OBLIGATORIA, s -> s.getVersionSistemaOperativo().getVersion()),
    PLATAFORMA("Plataforma", Uso.OPCIONAL, Servidor::getPlataforma),
    CPU("CPU", Uso.OPCIONAL, Servidor::getCantidadCpu),
    RAM_GB("RAM (GB)", Uso.OPCIONAL, s -> numero(s.getRamGb())),
    DISCO_VIRTUAL_GB("Disco virtual (GB)", Uso.OPCIONAL, s -> numero(s.getHdVirtualGb())),
    ENTORNO("Entorno", Uso.OBLIGATORIA, s -> s.getEntorno().getNombre()),
    CRITICIDAD("Criticidad", Uso.OBLIGATORIA, s -> s.getNivelCriticidad().getNombre()),
    RESPONSABLE("Responsable", Uso.OBLIGATORIA, s -> s.getResponsable().getCodigo()),
    NOMBRE_RESPONSABLE("Nombre del responsable", Uso.SOLO_EXPORTACION,
            s -> s.getResponsable().getNombreCompleto()),
    DESCRIPCION("Descripción", Uso.OPCIONAL, Servidor::getDescripcion),
    FECHA_ALTA("Fecha de alta", Uso.SOLO_EXPORTACION, Servidor::getFechaAlta),
    FECHA_ACTUALIZACION("Fecha de actualización", Uso.SOLO_EXPORTACION, Servidor::getFechaActualizacion);

    /** Participación de la columna en la importación. */
    public enum Uso {
        /** Debe estar en el encabezado y tener valor en cada registro. */
        OBLIGATORIA,
        /** Se importa si viene; vacía deja el dato sin valor. */
        OPCIONAL,
        /** Dato que administra el sistema: se exporta, pero al importar se ignora. */
        SOLO_EXPORTACION
    }

    private final String etiqueta;
    private final String clave;
    private final Uso uso;
    private final Function<Servidor, Object> extractor;

    ColumnaInventario(String etiqueta, Uso uso, Function<Servidor, Object> extractor) {
        this.etiqueta = etiqueta;
        this.clave = ClaveColumna.normalizar(etiqueta);
        this.uso = uso;
        this.extractor = extractor;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public String clave() {
        return clave;
    }

    public Uso uso() {
        return uso;
    }

    public boolean importable() {
        return uso != Uso.SOLO_EXPORTACION;
    }

    public boolean obligatoria() {
        return uso == Uso.OBLIGATORIA;
    }

    public Object valorDe(Servidor servidor) {
        return extractor.apply(servidor);
    }

    /** 16 y no 16.00: el valor se exporta como texto y se vuelve a importar igual. */
    private static String numero(BigDecimal valor) {
        return valor == null ? null : valor.stripTrailingZeros().toPlainString();
    }

    public ColumnaArchivo comoColumnaArchivo() {
        return new ColumnaArchivo(clave, etiqueta);
    }

    public static List<ColumnaInventario> importables() {
        return Arrays.stream(values()).filter(ColumnaInventario::importable).toList();
    }

    /** Busca por clave normalizada, de modo que acepta también la etiqueta. */
    public static ColumnaInventario deClave(String texto) {
        String clave = ClaveColumna.normalizar(texto);
        return Arrays.stream(values()).filter(c -> c.clave.equals(clave)).findFirst().orElse(null);
    }
}
