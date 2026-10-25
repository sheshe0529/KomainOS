package com.komainos.ejecucion.service;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
/** Los secretos solo se resuelven en el adaptador REAL; no se serializan ni se registran (RNF11). */
public interface PuertoConexionAutomatizacion {
 Conexion resolver(Integer idCuenta,FamiliaSistemaOperativo familia);
 final class Conexion {
  private final String usuario,secreto,su; private final boolean llave;
  public Conexion(String usuario,String secreto,String su,boolean llave){this.usuario=usuario;this.secreto=secreto;this.su=su;this.llave=llave;}
  public String usuario(){return usuario;} public String secreto(){return secreto;} public String su(){return su;} public boolean llave(){return llave;}
  @Override public String toString(){return "Conexion[protegida]";}
 }
}
