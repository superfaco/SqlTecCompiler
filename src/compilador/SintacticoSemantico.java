/*:-----------------------------------------------------------------------------
 *:                       INSTITUTO TECNOLOGICO DE LA LAGUNA
 *:                     INGENIERIA EN SISTEMAS COMPUTACIONALES
 *:                         LENGUAJES Y AUTOMATAS II           
 *: 
 *:                  SEMESTRE: ___________    HORA: ___________ HRS
 *:                                   
 *:               
 *:         Clase con la funcionalidad del Analizador Sintactico
 *                 
 *:                           
 *: Archivo       : SintacticoSemantico.java
 *: Autor         : Fernando Gil  ( Estructura general de la clase  )
 *:                 Grupo de Lenguajes y Automatas II ( Procedures  )
 *: Fecha         : 03/SEP/2014
 *: Compilador    : Java JDK 7
 *: Descripción   : Esta clase implementa un parser descendente del tipo 
 *:                 Predictivo Recursivo. Se forma por un metodo por cada simbolo
 *:                 No-Terminal de la gramatica mas el metodo emparejar ().
 *:                 El analisis empieza invocando al metodo del simbolo inicial.
 *: Ult.Modif.    :
 *:  Fecha      Modificó            Modificacion
 *:=============================================================================
 *: 22/Feb/2015 FGil                -Se mejoro errorEmparejar () para mostrar el
 *:                                 numero de linea en el codigo fuente donde 
 *:                                 ocurrio el error.
 *: 08/Sep/2015 FGil                -Se dejo lista para iniciar un nuevo analizador
 *:                                 sintactico.
 *: 06/Nov/2018  FCaldera            -Se agregaron las constantes "VACIO" y "ERROR_TIPO", 
 *:                                  así como el método "tiposCompatibles", "buscaTipo"
 *:                                  y checarArchivo.
 *: 17/Nov/2018  FCaldera           - Se corrigieron e implementaron las acciones semánticas
 *:                                   para las sentencias CREATE, DROP, DECLARE y ASSIGN
 *: 17/Nov/2018  FCaldera           - Se corrigieron e implementaron las acciones semánticas
 *:                                   para las sentencias INSERT, UPDATE, DELETE, SELECT,
 *:                                   WHILE, IF, CASE
 *:-----------------------------------------------------------------------------
 */
package compilador;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Stack;
import javafx.util.Pair;

public class SintacticoSemantico {

    private Compilador cmp;
    private boolean analizarSemantica = false;
    private String preAnalisis;
    private final Stack<Pair<Linea_BE, Linea_BE>> paresIdvarId = new Stack<>();
    private final Stack<Linea_BE> columnas = new Stack<>();
    private final Stack<String> tiposExpresiones = new Stack<>();
    public static final String VACIO = "vacio";
    public static final String ERROR_TIPO = "error_tipo";
    public static final int SUMA = 0;
    public static final int MULT = 1;

    private boolean tiposCompatibles(String tipo1, String tipo2) {
        if (tipo1.equals("integer") && tipo2.equals("integer")) {
            return true;
        } else if (tipo1.equals("real") && tipo2.equals("real")) {
            return true;
        } else if ((tipo1.equals("integer") && tipo2.equals("real"))
                || (tipo1.equals("real") && tipo2.equals("integer"))) {
            return true;
        } else if (tipo1.contains("char")
                && tipo2.contains("char")) {
            return true;
        } else {
            return false;
        }
    }

    private boolean opeTiposCompatibles(String operador, String tipo) {
        if (tipo.equals("integer") || tipo.equals("real")) {
            String[] opComp = {"<", "<=", "=", ">=", ">", "!=", "<>"};
            for (int i = 0; i < opComp.length; i++) {
                if (operador.equals(opComp[i])) {
                    return true;
                }
            }
            return false;
        } else if (tipo.contains("char")) {
            String[] opComp = {"=", "<>", "!="};
            for (int i = 0; i < opComp.length; i++) {
                if (operador.equals(opComp[i])) {
                    return true;
                }
            }
            return false;
        } else {
            return false;
        }
    }

    private boolean paresColsExprsCorrectos(String proceso) {
        boolean correctos = false;
        //Verificamos que el tamaño sea el mismo
        if (columnas.size() != tiposExpresiones.size()) {
            correctos = false;
            errorSemantico(String.format(
                    "[%s]: El número de columnas y el número de"
                    + " expresiones no coinciden. En Linea %s",
                    proceso,
                    columnas.peek().numLinea
            ));
        } else {
            //Iteramos ambas pilas y validamos que sus tipos sean compatibles
            //y el caso especial de que se quiera guardar una expresión de tipo
            //real en un id de tipo integer
            while (!columnas.empty()) {
                Linea_BE columna = columnas.pop();
                String tipoExpr = tiposExpresiones.pop();

                //Obtenemos el tipo de la columna y verificamos que
                // sea compatible con el tipo de la epxresión
                String tipoCol = buscaTipo(columna.entrada);
                tipoCol = tipoCol.substring(tipoCol.indexOf("(") + 1, tipoCol.length() - 1);

                if (tiposCompatibles(tipoCol, tipoExpr)) {
                    //Verificamos que no se guarde un valor real en una columna integer
                    if (tipoCol.equals("integer") && tipoExpr.equals("real")) {
                        correctos = false;
                        errorSemantico(
                                String.format(
                                        "[%s]: El tipo de la columna"
                                        + " \"%s\" no es compatible"
                                        + " con el tipo de la expresión. En"
                                        + " Linea %s",
                                        proceso,
                                        columna.lexema,
                                        columna.numLinea
                                ));
                        break;
                    } else {
                        correctos = true;
                    }
                } else {
                    correctos = false;
                    errorSemantico(
                            String.format(
                                    "[%s]: El tipo de la columna"
                                    + " \"%s\" no es compatible"
                                    + " con el tipo de la expresión. En"
                                    + " Linea %s",
                                    proceso,
                                    columna.lexema,
                                    columna.numLinea
                            ));
                    break;
                }
            }
        }
        return correctos;
    }

    private boolean paresIdvarIdCorrectos(String ambito, String proceso) {
        boolean correctas = false;
        while (!paresIdvarId.empty()) {
            Linea_BE idvar = paresIdvarId.peek().getKey();
            Linea_BE id = paresIdvarId.peek().getValue();
            paresIdvarId.pop();
            //Si idvar no está declarado, regresa falso
            if (buscaTipo(idvar.entrada).equals("")) {
                correctas = false;
                errorSemantico(
                        String.format("[%s]: El idvar \"%s\" no está"
                                + " declarado. En Linea %s",
                                proceso,
                                idvar.lexema,
                                idvar.numLinea
                        )
                );
                break;
            } else {
                //Si id es una columna, continua, si no, regresa falso
                if (buscaTipo(id.entrada).contains("COLUMNA")) {
                    //Si la columna pertenece a la tabla, continua, si no,
                    //regresa falso
                    if (buscaAmbito(id.entrada).equals(ambito)) {
                        String tidvar = buscaTipo(idvar.entrada);
                        String texpr = buscaTipo(id.entrada);
                        texpr = texpr.substring(texpr.indexOf("(") + 1, texpr.length() - 1);
                        //Si los tipos son compatibles, continua, si no, regresa falso
                        if (tiposCompatibles(tidvar, texpr)) {
                            //Validamos el caso en que se quiera asignar un valor
                            //real a un idvar entero
                            if (tidvar.equals("integer") && texpr.equals("real")) {
                                errorSemantico(
                                        String.format("[%s]: El tipo del idvar \"%s\""
                                                + " y la columna \"%s\" no"
                                                + " son compatibles. En Linea %s",
                                                proceso,
                                                idvar.lexema,
                                                id.lexema,
                                                id.numLinea
                                        )
                                );
                            } else {
                                correctas = true;
                            }
                        } else {
                            correctas = false;
                            errorSemantico(
                                    String.format("[%s]: El tipo del idvar \"%s\""
                                            + " y la columna \"%s\" no"
                                            + " son compatibles. En Linea %s",
                                            proceso,
                                            idvar.lexema,
                                            id.lexema,
                                            id.numLinea
                                    )
                            );
                            break;
                        }
                    } else {
                        correctas = false;
                        errorSemantico(
                                String.format("[%s]: La columna \"%s\""
                                        + " no pertenece a la tabla \"%s\"."
                                        + " En Linea %s",
                                        proceso,
                                        id.lexema,
                                        ambito,
                                        id.numLinea
                                )
                        );
                        break;
                    }
                } else {
                    correctas = false;
                    errorSemantico(
                            String.format("[%s]: El id \"%s\" no es"
                                    + " una columna. En Linea %s",
                                    proceso,
                                    id.lexema,
                                    id.numLinea
                            )
                    );
                    break;
                }
            }
        }
        return correctas;
    }

    private String buscaTipo(int entrada) {
        return cmp.ts.buscaTipo(entrada);
    }

    private void anadeTipo(int p, String t) {
        cmp.ts.anadeTipo(p, t);
    }

    private void anadeAmbito(int p, String t) {
        cmp.ts.anadeAmbito(p, t);
    }

    private String buscaAmbito(int p) {
        return cmp.ts.buscaAmbito(p);
    }

    private String buscaValor(int p) {
        return cmp.ts.buscaValor(p);
    }

    private void anadeValor(int p, String v) {
        cmp.ts.anadeValor(p, v);
    }

    private boolean existeSimbolo(String lexema, String ambito) {
        return cmp.ts.existeSimbolo(lexema, ambito);
    }

    private int buscar(String lex) {
        return cmp.ts.buscar(lex);
    }

    /*----------------------------------------------------------------------------------------*/
    // Metodo para comprobar la existencia en disco del archivo "nomarchivo" 
    // y en su caso cargar su contenido en la Tabla de Simbolos.
    // El argumento representa el nombre de un archivo de texto con extension
    //  ".db" que contiene el esquema (dise�o) de una tabla de base de datos. 
    // Los archivos .db tienen el siguiente dise�o:
    //     Dato            ColIni    ColFin
    //     ==================================
    //     nombre-columna  1         25
    //     tipo-de-dato    30        40
    //
    // Ejemplo:  alumnos.db
    //          1         2         3        
    // 1234567890123456789012345678901234567890
    // ==========================================
    // numctrl                      char(8)  
    // nombre                       char(25)
    // edad                         int
    // promedio                     float
    //
    // Cada columna se carga en la Tabla de Simbolos con Complex = "id" y
    // Tipo = "columna(t)"  siendo t  el tipo de dato de la columna.
    // ----------------------------------------------------------------------
    // 20/Oct/2018: Si en la T.S. ya existe la columna con el mismo ambito 
    // que el que se va a registrar solo se sustituye el TIPO si est� en blanco.
    // Si existe la columna pero no tiene ambito entonces se rellenan los datos
    // del tipo y el ambito. 
    private boolean checarArchivo(String nomarchivo) {
        FileReader fr = null;
        BufferedReader br = null;
        String linea = null;
        String columna = null;
        String tipo = null;
        String ambito = null;
        boolean existeArch = false;
        int pos;

        try {
            // Intentar abrir el archivo con el dise�o de la tabla  
            fr = new FileReader(nomarchivo);
            anadeTipo(buscar(nomarchivo.substring(0, nomarchivo.length() - 3)), "tabla");
            br = new BufferedReader(fr);

            // Leer linea x linea, cada linea es la especificacion de una columna
            linea = br.readLine();
            while (linea != null) {
                // Extraer nombre y tipo de dato de la columna
                try {
                    columna = linea.substring(0, 24).trim();
                } catch (Exception err) {
                    columna = "ERROR";
                }
                try {
                    tipo = linea.substring(29).trim();
                } catch (Exception err) {
                    tipo = "ERROR";
                }
                try {
                    ambito = nomarchivo.substring(0, nomarchivo.length() - 3);
                } catch (Exception err) {
                    ambito = "ERROR";
                }
                if (tipo.equals("int")) {
                    tipo = "integer";
                } else if (tipo.equals("float")) {
                    tipo = "real";
                }
                // Agregar a la tabla de simbolos
                Linea_TS lts = new Linea_TS("id",
                        columna,
                        "COLUMNA(" + tipo + ")",
                        ambito, ""
                );
                // Checar si en la Tabla de Simbolos existe la entrada para un 
                // lexema y ambito iguales al de columna y ambito de la tabla .db
                if ((pos = cmp.ts.buscar(columna, ambito)) > 0) {
                    // YA EXISTE: Si no tiene tipo asignarle el tipo columna(t) 
                    if (cmp.ts.buscaTipo(pos).trim().isEmpty()) {
                        cmp.ts.anadeTipo(pos, tipo);
                    }
                } else // NO EXISTE: Buscar si en la T. de S. existe solo el lexema de la columna
                if ((pos = cmp.ts.buscar(columna)) > 0) {
                    // SI EXISTE: checar si el ambito esta en blanco
                    Linea_TS aux = cmp.ts.obt_elemento(pos);
                    if (aux.getAmbito().trim().isEmpty()) {
                        // Ambito en blanco rellenar el tipo y el ambito  
                        cmp.ts.anadeTipo(pos, "COLUMNA(" + tipo + ")");
                        cmp.ts.anadeAmbito(pos, ambito);

                    } else {
                        // Insertar un nuevo elemento a la tabla de simb.
                        cmp.ts.insertar(lts);
                    }
                } else {
                    // NO EXISTE: insertar un nuevo elemento a la tabla de simb.
                    cmp.ts.insertar(lts);
                }

                // Leer siguiente linea
                linea = br.readLine();
            }
            existeArch = true;
        } catch (IOException ex) {
            System.out.println(ex);
        } finally {
            // Cierra los streams de texto si es que se crearon
            try {
                if (br != null) {
                    br.close();
                }
                if (fr != null) {
                    fr.close();
                }
            } catch (IOException ex) {
            }
        }
        return existeArch;
    }

    private List<Linea_TS> buscaColumnas(Linea_BE id) {
        return cmp.ts.buscaColumnas(id);
    }

    private boolean crearArchivo(Linea_BE id) {
        boolean creado = false;
        FileWriter fw = null;
        BufferedWriter bw = null;
        try {
            fw = new FileWriter(id.lexema + ".db");
            bw = new BufferedWriter(fw);
            List<Linea_TS> list = buscaColumnas(id);
            bw.write(list.get(0).getLexema());
            for (int i = 0; i < 49 - list.get(0).getLexema().length(); i++) {
                bw.write(" ");
            }
            String tipo;

            tipo = list.get(0).getTipo().substring(
                    list.get(0).getTipo().indexOf("(") + 1, list.get(0).getTipo().length() - 1);

            if (tipo.equals("integer")) {
                bw.write("int");
            } else if (tipo.equals("real")) {
                bw.write("float");
            } else {
                bw.write(tipo);
            }

            for (int i = 1; i < list.size(); i++) {
                bw.newLine();
                bw.write(list.get(i).getLexema());
                for (int j = 0; j < 49 - list.get(i).getLexema().length(); j++) {
                    bw.write(" ");
                }
                tipo = list.get(i).getTipo().substring(
                        list.get(i).getTipo().indexOf("(") + 1, list.get(i).getTipo().length() - 1);

                if (tipo.equals("integer")) {
                    bw.write("int");
                } else if (tipo.equals("real")) {
                    bw.write("float");
                } else {
                    bw.write(tipo);
                }

            }
            creado = true;
        } catch (IOException e) {
            System.out.println(e);
        } finally {
            try {
                if (bw != null) {
                    bw.close();
                }
                if (fw != null) {
                    fw.close();
                }
            } catch (IOException e) {
            }
        }
        return creado;
    }

    private boolean eliminarArchivo(String nomarchivo) {
        boolean eliminado = false;
        File f = null;
        try {
            f = new File(nomarchivo);
            if (f.exists()) {
                eliminado = f.delete();
            }
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            f = null;
        }
        return eliminado;
    }

    /*----------------------------------------------------------------------------------------*/
    //--------------------------------------------------------------------------
    // Constructor de la clase, recibe la referencia de la clase principal del 
    // compilador.
    //
    public SintacticoSemantico(Compilador c) {
        cmp = c;
    }

    //--------------------------------------------------------------------------
    //--------------------------------------------------------------------------
    // Metodo que inicia la ejecucion del analisis sintactico predictivo.
    // analizarSemantica : true = realiza el analisis semantico a la par del sintactico
    //                     false= realiza solo el analisis sintactico sin comprobacion semantica
    public void analizar(boolean analizarSemantica) {
        this.analizarSemantica = analizarSemantica;
        preAnalisis = cmp.be.preAnalisis.complex;

        // * * *   INVOCAR AQUI EL PROCEDURE DEL SIMBOLO INICIAL   * * *
        Atributos PROGRAMASQL = new Atributos();
        PROGRAMASQL(PROGRAMASQL);
    }

    //--------------------------------------------------------------------------
    private void emparejar(String t) {
        if (cmp.be.preAnalisis.complex.equals(t)) {
            cmp.be.siguiente();
            preAnalisis = cmp.be.preAnalisis.complex;
        } else {
            errorEmparejar(t, cmp.be.preAnalisis.lexema, cmp.be.preAnalisis.numLinea);
        }
    }

    //--------------------------------------------------------------------------
    // Metodo para devolver un error al emparejar
    //--------------------------------------------------------------------------
    private void errorEmparejar(String _token, String _lexema, int numLinea) {
        String msjError = "";

        if (_token.equals("id")) {
            msjError += "Se esperaba un identificador";
        } else if (_token.equals("num")) {
            msjError += "Se esperaba una constante entera";
        } else if (_token.equals("num.num")) {
            msjError += "Se esperaba una constante real";
        } else if (_token.equals("literal")) {
            msjError += "Se esperaba una literal";
        } else if (_token.equals("oparit")) {
            msjError += "Se esperaba un operador aritmetico";
        } else if (_token.equals("oprel")) {
            msjError += "Se esperaba un operador relacional";
        } else if (_token.equals("opasig")) {
            msjError += "Se esperaba operador de asignacion";
        } else {
            msjError += "Se esperaba " + _token;
        }
        msjError += " se encontró " + (_lexema.equals("$") ? "fin de archivo" : _lexema)
                + ". Linea " + numLinea;        // FGil: Se agregó el numero de linea

        cmp.me.error(Compilador.ERR_SINTACTICO, msjError);
    }

    // Fin de ErrorEmparejar
    //--------------------------------------------------------------------------
    // Metodo para mostrar un error sintactico
    private void error(String _descripError) {
        cmp.me.error(cmp.ERR_SINTACTICO, _descripError);
    }

    //Método para agregar errores semánticos
    private void errorSemantico(String _descripError) {
        cmp.me.error(Compilador.ERR_SEMANTICO, _descripError);
    }

    // Fin de error
    //--------------------------------------------------------------------------
    //  *  *   *   *    PEGAR AQUI EL CODIGO DE LOS PROCEDURES  *  *  *  *
    //--------------------------------------------------------------------------
//Autor: Cabrales Coronado Heber - 13130684
    private void PROGRAMASQL(Atributos PROGRAMASQL) {

        Atributos DECLARACION = new Atributos();
        Atributos SENTENCIAS = new Atributos();

        if (preAnalisis.equals("declare") || preAnalisis.equals("if")
                || preAnalisis.equals("while") || preAnalisis.equals("print")
                || preAnalisis.equals("select") || preAnalisis.equals("delete")
                || preAnalisis.equals("insert") || preAnalisis.equals("update")
                || preAnalisis.equals("create") || preAnalisis.equals("drop")
                || preAnalisis.equals("assign") || preAnalisis.equals("case")
                || preAnalisis.equals("end")) {
            DECLARACION(DECLARACION);
            SENTENCIAS(SENTENCIAS);
            emparejar("end");

            if (analizarSemantica) {
                if (DECLARACION.tipo.equals(VACIO) && SENTENCIAS.tipo.equals(VACIO)) {
                    PROGRAMASQL.tipo = VACIO;
                } else {
                    PROGRAMASQL.tipo = ERROR_TIPO;
                }
            }

        } else {
            error("[PROGRAMASQL] inicio no correcto" + " Linea " + cmp.be.preAnalisis.numLinea);
        }
    }

//-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
//Autor: Cabrales Coronado Heber - 13130684
    private void ACTREGS(Atributos ACTREGS) {

        Atributos IGUALACION = new Atributos();
        Atributos EXPRCOND = new Atributos();
        Linea_BE id = new Linea_BE();

        if (preAnalisis.equals("update")) {
            //ACTREGS -> update id  set  IGUALACION   where EXPRCOND
            emparejar("update");
            if (analizarSemantica) {
                id = cmp.be.preAnalisis;
                if (!checarArchivo(id.lexema + ".db")) {
                    ACTREGS.tipo = ERROR_TIPO;
                    errorSemantico("[ACTREGS]: La tabla \"" + id.lexema + "\""
                            + " no existe. En Linea "
                            + (id.numLinea)
                    );
                    return;
                }
            }
            emparejar("id");
            emparejar("set");
            if (analizarSemantica) {
                IGUALACION.ambito = id.lexema;
            }
            IGUALACION(IGUALACION);
            emparejar("where");
            if (analizarSemantica) {
                EXPRCOND.ambito = id.lexema;
            }
            EXPRCOND(EXPRCOND);
            if (analizarSemantica) {

                //Verificamos que la tabla exista
                if (checarArchivo(id.lexema + ".db")) {

                    //Verificamos que la expresión condicional esté bien
                    if (EXPRCOND.tipo.equals("boolean")) {
                        //Verificamos que no haya errores en la asignación
                        //de nuevos valores
                        if (IGUALACION.tipo.equals(VACIO)) {
                            ACTREGS.tipo = VACIO;
                        } else {
                            ACTREGS.tipo = ERROR_TIPO;
                        }
                    } else {
                        ACTREGS.tipo = ERROR_TIPO;
                    }
                } else {
                    ACTREGS.tipo = ERROR_TIPO;
                    errorSemantico("[ACTREGS]: La tabla \"" + id.lexema + "\""
                            + " no existe. En Linea "
                            + (id.numLinea)
                    );
                }
            }

        } else {
            error("[ACTREGS] El programa debe continuar con la sentencia update"
                    + " Linea " + cmp.be.preAnalisis.numLinea);
        }
    }
//---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
//Autor: Cabrales Coronado Heber - 13130684

    //primeros = {id}
    private void COLUMNAS(Atributos COLUMNAS) {

        Atributos COLUMNAS_P = new Atributos();
        Linea_BE id = new Linea_BE();

        if (preAnalisis.equals("id")) {
            //COLUMNAS -> id COLUMNAS_P
            if (analizarSemantica) {
                id = cmp.be.preAnalisis;
            }
            emparejar("id");
            if (analizarSemantica) {
                COLUMNAS_P.ambito = COLUMNAS.ambito;
            }
            COLUMNAS_P(COLUMNAS_P);

            if (analizarSemantica) {
                //Inicio de la acción semántica

                //Verificamos que la sub-declaración de
                //columnas esté correcta
                if (COLUMNAS_P.tipo.equals(VACIO)) {
                    //Verificamos que el id sea del tipo columna.
                    if (buscaTipo(id.entrada).contains("COLUMNA")) {
                        //Verificamos que el ambito sea correcto
                        if (buscaAmbito(id.entrada).equals(COLUMNAS.ambito)) {
                            COLUMNAS.tipo = VACIO;
                            columnas.push(id);
                        } else {
                            COLUMNAS.tipo = ERROR_TIPO;
                            errorSemantico("[COLUMNAS]: La columna \"" + id.lexema + "\""
                                    + " no es una columna de la tabla "
                                    + "\"" + COLUMNAS.ambito + "\"."
                                    + " En Linea "
                                    + (cmp.be.preAnalisis.numLinea - 1)
                            );
                        }
                    } else {
                        COLUMNAS.tipo = ERROR_TIPO;
                        errorSemantico("[COLUMNAS]: El id \"" + id.lexema + "\""
                                + " no es una columna. En Linea "
                                + (id.numLinea)
                        );

                    }
                } else {
                    COLUMNAS.tipo = ERROR_TIPO;
                }

                //Fin de la acción semántica
            }
        } else {
            error("[COLUMNAS] Para definir una columna es necesario un "
                    + "identificador " + "Linea " + cmp.be.preAnalisis.numLinea);
        }
    }

//-------------------------------------------------------------------------
//Fernando Alfonso Caldera Olivas                           15130685
//PRIMEROS(COLUMNAS_P) = {,, empty}
    private void COLUMNAS_P(Atributos COLUMNAS_P) {

        Atributos COLUMNAS = new Atributos();

        if (preAnalisis.equals(",")) {
            //COLUMNAS_P -> , COLUMNAS
            emparejar(",");
            if (analizarSemantica) {
                COLUMNAS.ambito = COLUMNAS_P.ambito;
            }
            COLUMNAS(COLUMNAS);

            if (analizarSemantica) {
                COLUMNAS_P.tipo = COLUMNAS.tipo;
            }

        } else //COLUMNAS_P -> empty
        if (analizarSemantica) {
            COLUMNAS_P.tipo = VACIO;
        }
    }

//-------------------------------------------------------------------------
//Fernando Alfonso Caldera Olivas                           15130685
//PRIMEROS(DECLARACION) = {declare, empty}
    private void DECLARACION(Atributos DECLARACION) {

        Atributos TIPO = new Atributos();
        Atributos DECLARACION1 = new Atributos();
        Linea_BE idvar = new Linea_BE();

        if (preAnalisis.equals("declare")) {
            //DECLARACION -> declare idvar TIPO DECLARACION1
            emparejar("declare");
            if (analizarSemantica) {
                idvar = cmp.be.preAnalisis;
            }
            emparejar("idvar");
            TIPO(TIPO);
            DECLARACION(DECLARACION1);
            if (analizarSemantica) {
                if (DECLARACION1.tipo.equals(VACIO)) {
                    if (buscaTipo(idvar.entrada).equals("")) {
                        anadeTipo(idvar.entrada, TIPO.tipo);
                        DECLARACION.tipo = VACIO;
                    } else {
                        DECLARACION.tipo = ERROR_TIPO;
                        errorSemantico("[DECLARACION]: No se puede redeclarar el"
                                + " identificador " + idvar.lexema + ". En Linea " + (cmp.be.preAnalisis.numLinea - 1));
                    }
                } else {
                    DECLARACION.tipo = ERROR_TIPO;
                }
            }
        } else //DECLARACION -> empty
        if (analizarSemantica) {
            DECLARACION.tipo = VACIO;
        }
    }

//-------------------------------------------------------------------------
//Fernando Alfonso Caldera Olivas                           15130685
//PRIMEROS(DESPLIEGUE) = {print}
    private void DESPLIEGUE(Atributos DESPLIEGUE) {

        Atributos EXPRARIT = new Atributos();

        if (preAnalisis.equals("print")) {
            //DESPLIEGUE -> print EXPRARIT
            emparejar("print");
            EXPRARIT(EXPRARIT);

            if (analizarSemantica) {
                //Si hay un error en la expresión, ERROR_TIPO
                if (EXPRARIT.tipo.equals(ERROR_TIPO)) {
                    DESPLIEGUE.tipo = ERROR_TIPO;
                } else {
                    DESPLIEGUE.tipo = VACIO;
                }
            }

        } else {
            error("[DESPLIEGUE]: Se esperaba \"print\" en linea " + cmp.be.preAnalisis.numLinea);
        }
    }

    //--------------------------------------------------------------------------
    //Pedro Gutiérrez Castillo
    //EJERCICIOS DELREG,EXPRESIONES Y EXPRESIONES' 
    //Primeros(DELREG) = {delete}
    //Primeros(EXPRESIONES) = {Primeros(EXPRARIT)}
    //                      = {num,num.num,idvar,literal,id,(}
    //Primeros(EXPRESIONES_P) = {, , empty}
    private void DELREG(Atributos DELREG) {

        Atributos EXPRCOND = new Atributos();
        Linea_BE id = new Linea_BE();

        if (preAnalisis.equals("delete")) {
            // DELREG -> delete from id where EXPRCOND
            emparejar("delete");
            emparejar("from");
            if (analizarSemantica) {
                id = cmp.be.preAnalisis;
            }
            emparejar("id");
            emparejar("where");
            if (analizarSemantica) {
                EXPRCOND.ambito = id.lexema;
                if (!checarArchivo(id.lexema + ".db")) {
                    DELREG.tipo = ERROR_TIPO;
                    errorSemantico("[DELREG]: La tabla \"" + id.lexema + "\""
                            + " no existe. En Linea "
                            + (id.numLinea)
                    );
                    return;
                }
            }
            EXPRCOND(EXPRCOND);
            if (analizarSemantica) {
                //Validamos que exista la tabla
                if (checarArchivo(id.lexema + ".db")) {
                    if (EXPRCOND.tipo.equals("boolean")) {
                        DELREG.tipo = VACIO;
                    } else {
                        DELREG.tipo = ERROR_TIPO;
                    }
                } else {
                    DELREG.tipo = ERROR_TIPO;
                    errorSemantico("[DELREG]: La tabla \"" + id.lexema + "\""
                            + " no existe. En Linea "
                            + (id.numLinea)
                    );
                }
            }

        } else {
            error("[DELREG]: Se esperaba la sentencia delete-from");
        }
    }

    //--------------------------------------------------------------------------
    private void EXPRESIONES(Atributos EXPRESIONES) {

        Atributos EXPRARIT = new Atributos();
        Atributos EXPRESIONES_P = new Atributos();

        if (preAnalisis.equals("num") || preAnalisis.equals("num.num")
                || preAnalisis.equals("idvar") || preAnalisis.equals("literal")
                || preAnalisis.equals("id") || preAnalisis.equals("(")) {
            //EXPRESIONES -> EXPRARIT   EXPRESIONES’
            if (analizarSemantica) {
                EXPRARIT.ambito = EXPRESIONES.ambito;
            }
            EXPRARIT(EXPRARIT);
            if (analizarSemantica) {
                EXPRESIONES_P.ambito = EXPRESIONES.ambito;
            }
            EXPRESIONES_P(EXPRESIONES_P);

            if (analizarSemantica) {
                //Verificamos que las sub-expresiones estén bien
                if (EXPRESIONES_P.tipo.equals(VACIO)) {
                    //Verificamos que la expresión actual no tenga errores
                    if (!EXPRARIT.tipo.equals(ERROR_TIPO)) {
                        //Verificamos que la expresión actual no incluya un
                        //id
                        if (!EXPRARIT.esId) {
                            EXPRESIONES.tipo = VACIO;
                            tiposExpresiones.push(EXPRARIT.tipo);
                        } else {
                            EXPRESIONES.tipo = ERROR_TIPO;
                            errorSemantico("[EXPRESIONES]: No se puede usar un id"
                                    + " dentro de una expresión de la"
                                    + " sentencia insert-into. En Linea "
                                    + (cmp.be.preAnalisis.numLinea - 1));
                        }
                    } else {
                        EXPRESIONES.tipo = ERROR_TIPO;
                    }
                } else {
                    EXPRESIONES.tipo = ERROR_TIPO;
                }
            }

        } else {
            error("[EXPRESIONES] : se esperaba la sentencia num");
        }
    }
    //-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

    private void EXPRESIONES_P(Atributos EXPRESIONES_P) {

        Atributos EXPRESIONES = new Atributos();

        if (preAnalisis.equals(",")) {
            //EXPRESIONES_P -> , EXPRESIONES
            emparejar(",");
            if (analizarSemantica) {
                EXPRESIONES.ambito = EXPRESIONES_P.ambito;
            }
            EXPRESIONES(EXPRESIONES);

            if (analizarSemantica) {
                EXPRESIONES_P.tipo = EXPRESIONES.tipo;
            }
        } else {
            //EXPRESIONES_P -> empty
            EXPRESIONES_P.tipo = VACIO;
        }
    }
    //-----------------------------------
    //OCTAVIO HERNANDEZ AGUILAR No.15130500
//PRIMEROS num,num.num,idvar,literal,id

    private void EXPRARIT(Atributos EXPRARIT) {

        Atributos OPERANDO = new Atributos();
        Atributos EXPRARIT_P = new Atributos();
        Atributos EXPRARIT1 = new Atributos();

        if (preAnalisis.equals("num") || preAnalisis.equals("num.num") || preAnalisis.equals("idvar") || preAnalisis.equals("literal") || preAnalisis.equals("id")) {
            // EXPARIT-> OPERANDO EXPARIT'
            if (analizarSemantica) {
                OPERANDO.ambito = EXPRARIT.ambito;
            }
            OPERANDO(OPERANDO);
            if (analizarSemantica) {
                EXPRARIT_P.ambito = EXPRARIT.ambito;
            }
            EXPRARIT_P(EXPRARIT_P);
            if (analizarSemantica) {
                //Si EXPRARIT_P u OPERANDO tiene error, ERROR_TIPO
                if (EXPRARIT_P.tipo.equals(ERROR_TIPO)
                        || OPERANDO.tipo.equals(ERROR_TIPO)) {
                    EXPRARIT.tipo = ERROR_TIPO;
                } else {
                    //Si EXPRARIT_P es VACIO, solo se considera OPERANDO en el
                    //retorno de la expresión
                    if (EXPRARIT_P.tipo.equals(VACIO)) {
                        EXPRARIT.tipo = OPERANDO.tipo;
                        EXPRARIT.valor = OPERANDO.valor;
                        EXPRARIT.esId = OPERANDO.esId;
                        EXPRARIT.esIdvar = OPERANDO.esIdvar;
                    } else {
                        //Si EXPRARIT_P tiene un tipo que no sea VACIO, se
                        //verifica que los tipos sean compatibles, si no, 
                        //ERROR_TIPO
                        if (tiposCompatibles(OPERANDO.tipo, EXPRARIT_P.tipo)) {
                            //Si son compatibles, se verifica el tipo
                            //Y se asigna el tipo con mayor precedencia.
                            //También se suman los valores o se multiplican.

                            //Si hay un real, el tipo resultante es real
                            //Se suman los valores numéricamente.
                            if (OPERANDO.tipo.equals("real")
                                    || EXPRARIT_P.tipo.equals("real")) {
                                EXPRARIT.tipo = "real";
                                //Si los operandos no son ids (tienen valor), 
                                //Se sintetiza su valor.
                                if (!(OPERANDO.esId || EXPRARIT_P.esId)) {
                                    //Si la operación es una suma, se suman los valores
                                    if (EXPRARIT_P.operacion == SUMA) {
                                        EXPRARIT.valor = (Double.parseDouble(OPERANDO.valor)
                                                + Double.parseDouble(EXPRARIT_P.valor)) + "";
                                    } //Si la operación es una multiplicación, se multiplican los valores.
                                    else if (EXPRARIT_P.operacion == MULT) {
                                        EXPRARIT.valor = (Double.parseDouble(OPERANDO.valor)
                                                * Double.parseDouble(EXPRARIT_P.valor)) + "";
                                    }
                                }
                                EXPRARIT.esId = OPERANDO.esId || EXPRARIT_P.esId;
                                EXPRARIT.esIdvar = OPERANDO.esIdvar || EXPRARIT_P.esIdvar;
                            } //Si no hay reales implicados, o los 2 son integer
                            //o los 2 son char.
                            else if (OPERANDO.tipo.equals("integer")) {
                                EXPRARIT.tipo = "integer";

                                //Si los operandos no son ids (tienen valor), 
                                //Se sintetiza su valor.
                                if (!(OPERANDO.esId || EXPRARIT_P.esId)) {
                                    //Si la operación es suma
                                    if (EXPRARIT_P.operacion == SUMA) {
                                        EXPRARIT.valor = (Long.parseLong(OPERANDO.valor)
                                                + Long.parseLong(EXPRARIT_P.valor)) + "";
                                    }//Si la operación es multiplicación
                                    else if (EXPRARIT_P.operacion == MULT) {
                                        EXPRARIT.valor = (Long.parseLong(OPERANDO.valor)
                                                * Long.parseLong(EXPRARIT_P.valor)) + "";
                                    }
                                }
                                EXPRARIT.esId = OPERANDO.esId || EXPRARIT_P.esId;
                                EXPRARIT.esIdvar = OPERANDO.esIdvar || EXPRARIT_P.esIdvar;
                            } //Si no fue real ni integer, es char.
                            //En este caso, concatenamos los lexemas y
                            //sumamos las longitudes para el tipo
                            //de dato resultante.
                            else {
                                //Si la operación es multiplicación,
                                //ERROR_TIPO.
                                if (EXPRARIT_P.operacion == MULT) {
                                    EXPRARIT.tipo = ERROR_TIPO;
                                    errorSemantico("[EXPRARIT]: No se puede usar el"
                                            + " operador * con operandos del tipo"
                                            + " char(n). En Linea "
                                            + cmp.be.preAnalisis.numLinea);
                                } else {
                                    //Si los operandos no son ids (tienen valor), 
                                    //Se sintetiza su valor.
                                    if (!(OPERANDO.esId || EXPRARIT_P.esId)) {
                                        EXPRARIT.valor = OPERANDO.valor + EXPRARIT_P.valor;
                                    }

                                    EXPRARIT.tipo = "char("
                                            + (OPERANDO.valor.length()
                                            + EXPRARIT_P.valor.length())
                                            + ")";
                                    EXPRARIT.esId = OPERANDO.esId || EXPRARIT_P.esId;
                                    EXPRARIT.esIdvar = OPERANDO.esIdvar || EXPRARIT_P.esIdvar;
                                }
                            }
                        } else {
                            EXPRARIT.tipo = ERROR_TIPO;
                            errorSemantico("[EXPRARIT]: Los tipos no son compatibles."
                                    + " En Linea " + cmp.be.preAnalisis.numLinea);
                        }
                    }
                }
            }
        } else if (preAnalisis.equals("(")) {

            emparejar("(");
            if (analizarSemantica) {
                EXPRARIT1.ambito = EXPRARIT.ambito;
            }
            EXPRARIT(EXPRARIT1);
            emparejar(")");
            if (analizarSemantica) {
                EXPRARIT_P.ambito = EXPRARIT.ambito;
            }
            EXPRARIT_P(EXPRARIT_P);

            if (analizarSemantica) {
                //Si EXPRARIT1 o EXPRARIT_P es ERROR_TIPO, ERROR_TIPO, 
                if (EXPRARIT1.tipo.equals(ERROR_TIPO)
                        || EXPRARIT_P.tipo.equals(ERROR_TIPO)) {
                    EXPRARIT.tipo = ERROR_TIPO;
                } //Si EXPRARIT_P es VACIO, solo se considera
                //EXPRARIT1 en la expresión de retorno
                else {
                    if (EXPRARIT_P.tipo.equals(VACIO)) {
                        EXPRARIT.tipo = EXPRARIT1.tipo;
                        EXPRARIT.valor = EXPRARIT1.valor;
                        EXPRARIT.esId = EXPRARIT1.esId;
                        EXPRARIT.esIdvar = EXPRARIT1.esIdvar;
                    } else {
                        //Si los tipos son compatibles, continua, si no,
                        //ERROR_TIPO
                        if (tiposCompatibles(EXPRARIT1.tipo, EXPRARIT_P.tipo)) {
                            //Si hay un real, el tipo resultante es real, por
                            //Tener mayor precedencia
                            if (EXPRARIT1.tipo.equals("real")
                                    || EXPRARIT_P.tipo.equals("real")) {
                                EXPRARIT.tipo = "real";

                                //Solo se sintetizan los valores si ninguna de las
                                //expresiones es id.
                                if (!EXPRARIT1.esId && !EXPRARIT_P.esId) {
                                    //Si la operación es mult, se multiplican.
                                    if (EXPRARIT_P.operacion == MULT) {
                                        EXPRARIT.valor = (Double.parseDouble(EXPRARIT1.valor)
                                                * Double.parseDouble(EXPRARIT_P.valor)) + "";
                                    } //Si la operación es suma, se suman.
                                    else if (EXPRARIT_P.operacion == SUMA) {
                                        EXPRARIT.valor = (Double.parseDouble(EXPRARIT1.valor)
                                                + Double.parseDouble(EXPRARIT_P.valor)) + "";
                                    }
                                }
                                EXPRARIT.esId = EXPRARIT1.esId || EXPRARIT_P.esId;
                                EXPRARIT.esIdvar = EXPRARIT1.esIdvar || EXPRARIT_P.esIdvar;
                            } else if (EXPRARIT1.tipo.equals("integer")) {
                                EXPRARIT.tipo = "integer";

                                //Solo se sintetizan los valores si ninguna de las
                                //expresiones es id.
                                if (!EXPRARIT1.esId && !EXPRARIT_P.esId) {
                                    //Si la operación es mult, se multiplican.
                                    if (EXPRARIT_P.operacion == MULT) {
                                        EXPRARIT.valor = (Long.parseLong(EXPRARIT1.valor)
                                                * Long.parseLong(EXPRARIT_P.valor)) + "";
                                    } //Si la operación es suma, se suman.
                                    else if (EXPRARIT_P.operacion == SUMA) {
                                        EXPRARIT.valor = (Long.parseLong(EXPRARIT1.valor)
                                                + Long.parseLong(EXPRARIT_P.valor)) + "";
                                    }
                                }
                                EXPRARIT.esId = EXPRARIT1.esId || EXPRARIT_P.esId;
                                EXPRARIT.esIdvar = EXPRARIT1.esIdvar || EXPRARIT_P.esIdvar;
                            } else {
                                //Si la operación es MULT, ERROR_TIPO
                                if (EXPRARIT_P.operacion == MULT) {
                                    errorSemantico("[EXPRARIT]: No se puede usar el"
                                            + " operador * con operandos del tipo"
                                            + " char(n). En Linea "
                                            + cmp.be.preAnalisis.numLinea);
                                } //Si no, se concatenan los valores
                                else {
                                    EXPRARIT.tipo = "char("
                                            + (EXPRARIT1.valor.length()
                                            + EXPRARIT_P.valor.length())
                                            + ")";
                                    //Solo se sintetizan los valores si ninguna de las
                                    //expresiones es id.
                                    if (!EXPRARIT1.esId && !EXPRARIT_P.esId) {
                                        EXPRARIT.valor = EXPRARIT1.valor + EXPRARIT_P.valor;
                                    }
                                    EXPRARIT.esId = EXPRARIT1.esId || EXPRARIT_P.esId;
                                    EXPRARIT.esIdvar = EXPRARIT1.esIdvar || EXPRARIT_P.esIdvar;
                                }
                            }
                        } else {
                            EXPRARIT.tipo = ERROR_TIPO;
                            errorSemantico("[EXPRARIT]: Los tipos no son compatbiles. En Linea " + cmp.be.preAnalisis.numLinea);
                        }
                    }
                }
            }
        } else {
            error("[EXPARIT]: inicio no correcto " + "linea" + cmp.be.preAnalisis.numLinea);
        }
    }

//OCTAVIO HERNANDEZ AGUILAR No.15130500--------------------------------------------------------
//PRIMEROS opsuma, opmult , empty
    private void EXPRARIT_P(Atributos EXPRARIT_P) {

        Atributos EXPRARIT = new Atributos();

        if (preAnalisis.equals("opsuma")) {
            //EXPARIT_P -> opsuma EXPRARIT
            emparejar("opsuma");
            if (analizarSemantica) {
                EXPRARIT.ambito = EXPRARIT_P.ambito;
            }
            EXPRARIT(EXPRARIT);
            if (analizarSemantica) {
                EXPRARIT_P.operacion = SUMA;
                EXPRARIT_P.tipo = EXPRARIT.tipo;
                EXPRARIT_P.valor = EXPRARIT.valor;
                EXPRARIT_P.esId = EXPRARIT.esId;
                EXPRARIT_P.esIdvar = EXPRARIT.esIdvar;
            }
        } else if (preAnalisis.equals("opmult")) {
            //EXPERIT_P -> opmult EXPRARIT
            emparejar("opmult");
            EXPRARIT(EXPRARIT);

            if (analizarSemantica) {
                EXPRARIT_P.operacion = MULT;
                EXPRARIT_P.tipo = EXPRARIT.tipo;
                EXPRARIT_P.valor = EXPRARIT.valor;
                EXPRARIT_P.esId = EXPRARIT.esId;
                EXPRARIT_P.esIdvar = EXPRARIT.esIdvar;
            }
        } else // EXPARIT_P -> empty
        if (analizarSemantica) {
            EXPRARIT_P.tipo = VACIO;
            EXPRARIT_P.valor = "";
        }
    }

//OCTAVIO HERNANDEZ AGUILAR No.15130500---------------------------------------------------------
//PRIMEROS (EXPRCOND )= {num | num.num | idvar | literal | id}
    private void EXPRCOND(Atributos EXPRCOND) {

        Atributos EXPRLOG = new Atributos();
        Atributos EXPRREL = new Atributos();

        if (preAnalisis.equals("num") || preAnalisis.equals("num.num") || preAnalisis.equals("idvar") || preAnalisis.equals("literal") || preAnalisis.equals("id")) {
            // EXPRCOND -> EXPRREL EXPRLOG
            if (analizarSemantica) {
                EXPRREL.ambito = EXPRCOND.ambito;
            }
            EXPRREL(EXPRREL);
            if (analizarSemantica) {
                EXPRLOG.valor = EXPRREL.valor;
                EXPRLOG.tipo = EXPRREL.tipo;
                EXPRLOG.esId = EXPRREL.esId;
                EXPRLOG.esIdvar = EXPRREL.esIdvar;
                EXPRLOG.ambito = EXPRCOND.ambito;
            }
            EXPRLOG(EXPRLOG);

            if (analizarSemantica) {
                //Si EXPRLOG es ERROR_TIPO, ERROR_TIPO
                if (EXPRLOG.tipo.equals(ERROR_TIPO)) {
                    EXPRCOND.tipo = ERROR_TIPO;
                    EXPRCOND.valor = "";
                } else {
                    EXPRCOND.tipo = "boolean";
                    //Si EXPRLOG es VACIO, solo se considera EXPRREL en el
                    //resultado, si no, el valor final de la expresión viene
                    //sintetizado en EXPRLOG
                    if (EXPRLOG.tipo.equals(VACIO)) {
                        EXPRCOND.valor = EXPRREL.valor;
                    } else {
                        EXPRCOND.valor = EXPRLOG.valor;
                    }
                }
            }
        } else {
            error("[EXPRCOND]: inicio no correcto " + "linea" + cmp.be.preAnalisis.numLinea);
        }

    }
//----------------------------------------------------
    //14130579 Luis Alfredo Hernandez Montelongo
// Metodo del procedimiento EXPRREL
//******************************************************** 

    private void EXPRREL(Atributos EXPRREL) {

        Atributos EXPRARIT1 = new Atributos();
        Atributos EXPRARIT2 = new Atributos();
        Linea_BE oprel = new Linea_BE();

        if (preAnalisis.equals("num") || preAnalisis.equals("num.num") || preAnalisis.equals("idvar") || preAnalisis.equals("literal") || preAnalisis.equals("id")) {
            //EXPRREL -> EXPRARIT oprel EXPRARIT
            if (analizarSemantica) {
                EXPRARIT1.ambito = EXPRREL.ambito;
            }
            EXPRARIT(EXPRARIT1);
            if (analizarSemantica) {
                oprel = cmp.be.preAnalisis;
            }
            emparejar("oprel");
            if (analizarSemantica) {
                EXPRARIT2.ambito = EXPRREL.ambito;
            }
            EXPRARIT(EXPRARIT2);
            if (analizarSemantica) {
                //Acción semántica
                //Si alguna de las 2 expresiones tiene error, ERROR_TIPO
                if (EXPRARIT1.tipo.equals(ERROR_TIPO)
                        || EXPRARIT2.tipo.equals(ERROR_TIPO)) {
                    EXPRREL.tipo = ERROR_TIPO;
                } else {
                    //Si los tipos de las expresiones son compatibles, continua, si no,
                    //ERROR_TIPO
                    if (tiposCompatibles(EXPRARIT1.tipo, EXPRARIT2.tipo)) {
                        //Verificamos que el operador y los tipos sean compatibles.
                        if (opeTiposCompatibles(oprel.lexema, EXPRARIT1.tipo)) {

                            //Validamos que no se comparen valores compatibles
                            //En expresiones aritméticas, pero incompatibles en
                            //Expresiones relacionales.
                            //Solo hay un caso. comparar integer con real cuando
                            //hay un id (columna) que es de tipo integer
                            if ((EXPRARIT1.tipo.equals("integer") && EXPRARIT1.esId
                                    && EXPRARIT2.tipo.equals("real"))
                                    || (EXPRARIT1.tipo.equals("real") && EXPRARIT2.esId
                                    && EXPRARIT2.tipo.equals("integer"))) {

                                EXPRREL.tipo = ERROR_TIPO;
                                errorSemantico("[EXPRREL]: Los tipos de las expresiones no son"
                                        + " compatibles. En Linea" + (cmp.be.preAnalisis.numLinea - 1));
                            } else {

                                //Comparamos el lexema de oprel para sintetizar el valor resultante
                                //de la expresión.
                                //Solo sintetizamos el valor si ni EXPRARIT1 ni
                                //EXPRARIT2 son ids (porque en ese caso, no tienen valores
                                //para poder sintetizar)
                                if (!(EXPRARIT1.esId || EXPRARIT2.esId)) {
                                    if (oprel.lexema.equals("<")) {
                                        if (Double.parseDouble(EXPRARIT1.valor)
                                                < Double.parseDouble(EXPRARIT2.valor)) {
                                            EXPRREL.valor = "true";
                                        } else {
                                            EXPRREL.valor = "false";
                                        }
                                    } else if (oprel.lexema.equals("<=")) {
                                        if (Double.parseDouble(EXPRARIT1.valor)
                                                <= Double.parseDouble(EXPRARIT2.valor)) {
                                            EXPRREL.valor = "true";
                                        } else {
                                            EXPRREL.valor = "false";
                                        }
                                    } else if (oprel.lexema.equals("=")) {
                                        if (EXPRARIT1.valor.equals(EXPRARIT2.valor)) {
                                            EXPRREL.valor = "true";
                                        } else {
                                            EXPRREL.valor = "false";
                                        }
                                    } else if (oprel.lexema.equals(">=")) {
                                        if (Double.parseDouble(EXPRARIT1.valor)
                                                >= Double.parseDouble(EXPRARIT2.valor)) {
                                            EXPRREL.valor = "true";
                                        } else {
                                            EXPRREL.valor = "false";
                                        }
                                    } else if (oprel.lexema.equals(">")) {
                                        if (Double.parseDouble(EXPRARIT1.valor)
                                                > Double.parseDouble(EXPRARIT2.valor)) {
                                            EXPRREL.valor = "true";
                                        } else {
                                            EXPRREL.valor = "false";
                                        }
                                    } else if (oprel.lexema.equals("!=") || oprel.lexema.equals("<>")) {
                                        if (!EXPRARIT1.valor.equals(EXPRARIT2.valor)) {
                                            EXPRREL.valor = "true";
                                        } else {
                                            EXPRREL.valor = "false";
                                        }
                                    }
                                }
                                EXPRREL.tipo = "boolean";
                                EXPRREL.esId = EXPRARIT1.esId || EXPRARIT2.esId;
                                EXPRREL.esIdvar = EXPRARIT1.esIdvar || EXPRARIT2.esIdvar;
                            }
                        } else {
                            EXPRREL.tipo = ERROR_TIPO;
                            errorSemantico("[EXPRREL]: El operador \"" + oprel.lexema + "\""
                                    + " no es compatible con expresiones de tipo"
                                    + " \"" + EXPRARIT1.tipo + "\". En Linea "
                                    + (cmp.be.preAnalisis.numLinea - 1));
                        }
                    } else {
                        EXPRREL.tipo = ERROR_TIPO;
                        errorSemantico("[EXPRREL]: Los tipos de las expresiones no son"
                                + " compatibles. En Linea" + (cmp.be.preAnalisis.numLinea - 1));
                    }
                }
                //Fin acción semántica
            }
        } else {
            error("[EXPRREL]: Se esperaba la sentencia exprrel " + " No. Linea " + cmp.be.preAnalisis.numLinea);
        }
    }

//14130579 Luis Alfredo Hernandez Montelongo     
// Metodo del procedimiento EXPRLOG 
//******************************************************** 
    private void EXPRLOG(Atributos EXPRLOG) {
        Atributos EXPRREL = new Atributos();

        if (preAnalisis.equals("and")) {
            //EXPRLOG-->and EXPRREL
            emparejar("and");
            if (analizarSemantica) {
                EXPRREL.ambito = EXPRLOG.ambito;
            }
            EXPRREL(EXPRREL);
            if (analizarSemantica) {
                //Acción semántica
                //Si heredo un error, ERROR_TIPO
                if (EXPRLOG.tipo.equals(ERROR_TIPO)) {
                    EXPRLOG.tipo = ERROR_TIPO;
                    EXPRLOG.valor = "";
                } else {
                    //Si la expresión derecha tiene errores, ERROR_TIPO
                    if (EXPRREL.tipo.equals(ERROR_TIPO)) {
                        EXPRLOG.tipo = ERROR_TIPO;
                        EXPRLOG.valor = "";
                    } else {
                        //Si las expresiones contenían un id, no se
                        //sintetizan sus valores
                        if (!EXPRLOG.esId && !EXPRREL.esId) {
                            if (EXPRLOG.valor.equals("true")
                                    && EXPRREL.valor.equals("true")) {
                                EXPRLOG.valor = "true";
                            } else {
                                EXPRLOG.valor = "false";
                            }
                        }
                        EXPRLOG.tipo = "boolean";
                        EXPRLOG.esId = EXPRLOG.esId || EXPRREL.esId;
                        EXPRLOG.esIdvar = EXPRLOG.esIdvar || EXPRREL.esIdvar;
                    }
                }
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("or")) {
            //EXPRLOG-->or EXPRREL
            emparejar("or");
            if (analizarSemantica) {
                EXPRREL.ambito = EXPRLOG.ambito;
            }
            EXPRREL(EXPRREL);
            if (analizarSemantica) {
                //Acción semántica
                //Si heredo un error, ERROR_TIPO
                if (EXPRLOG.tipo.equals(ERROR_TIPO)) {
                    EXPRLOG.tipo = ERROR_TIPO;
                    EXPRLOG.valor = "";
                } else {
                    //Si la expresión derecha tiene errores, ERROR_TIPO
                    if (EXPRREL.tipo.equals(ERROR_TIPO)) {
                        EXPRLOG.tipo = ERROR_TIPO;
                        EXPRLOG.valor = "";
                    } else {
                        //Si las expresiones contenían un id, no se
                        //sintetizan sus valores
                        if (!EXPRLOG.esId && !EXPRREL.esId) {
                            if (EXPRLOG.valor.equals("true")
                                    || EXPRREL.valor.equals("true")) {
                                EXPRLOG.valor = "true";
                            } else {
                                EXPRLOG.valor = "false";
                            }
                        }
                        EXPRLOG.tipo = "boolean";
                        EXPRLOG.esId = EXPRLOG.esId || EXPRREL.esId;
                        EXPRLOG.esIdvar = EXPRLOG.esIdvar || EXPRREL.esIdvar;
                    }
                }
                //Fin acción semántica
            }
        } else { //EXPRLOG--> empty 
            if (analizarSemantica) {
                //Acción semántica
                EXPRLOG.tipo = VACIO;
                EXPRLOG.valor = "";
                //Fin acción semántica
            }
        }
    }

//14130579 Luis Alfredo Hernandez Montelongo 
// Metodo del procedimiento ELIMTAB
//******************************************************** 
    private void ELIMTAB(Atributos ELIMTAB) {

        Linea_BE id = new Linea_BE();

        if (preAnalisis.equals("drop")) {
            //ELIMTAB-->drop table id
            emparejar("drop");
            emparejar("table");
            if (analizarSemantica) {
                id = cmp.be.preAnalisis;
            }
            emparejar("id");
            if (analizarSemantica) {
                //Acción semántica
                //Si la tabla existe, continua, si no, ERROR_TIPO
                if (checarArchivo(id.lexema + ".db")) {
                    //Si el tipo de id es "tabla", continua, si no,
                    //ERROR_TIPO
                    if (buscaTipo(id.entrada).equals("tabla")) {
                        ELIMTAB.tipo = VACIO;
                        eliminarArchivo(id.lexema + ".db");
                    } else {
                        ELIMTAB.tipo = ERROR_TIPO;
                        errorSemantico("[ELIMTAB]: El id \"" + id.lexema
                                + "\" no es una tabla. En Linea "
                                + (cmp.be.preAnalisis.numLinea - 1));
                    }
                } else {
                    ELIMTAB.tipo = ERROR_TIPO;
                    errorSemantico("[ELIMTAB]: No existe la tabla \""
                            + id.lexema + "\". En Linea "
                            + (cmp.be.preAnalisis.numLinea - 1));
                }
                //Fin acción semántica
            }
        } else {
            error("[ELIMTAB]: Se esperaba la sentencia elimtab " + " No. Linea " + cmp.be.preAnalisis.numLinea);
        }
    }

    //--------------------------------------------------------------------
    // Nombre: JOSE ENRIQUE IBARRA MANRIQUEZ. No. Control: 15130713
//PRIMEROS(IFELSE) = { if }
    private void IFELSE(Atributos IFELSE) {
        Atributos EXPRCOND = new Atributos();
        Atributos SENTENCIAS = new Atributos();
        Atributos IFELSE_P = new Atributos();

        if (preAnalisis.equals("if")) {
            //IFELSE -> if EXPRCOND begin SENTENCIAS end IFELSE_P
            emparejar("if");
            EXPRCOND(EXPRCOND);
            emparejar("begin");
            SENTENCIAS(SENTENCIAS);
            emparejar("end");
            IFELSE_P(IFELSE_P);
            if (analizarSemantica) {
                //Acción semántica
                if (EXPRCOND.tipo.equals("boolean") && SENTENCIAS.tipo.equals(VACIO)
                        && IFELSE_P.tipo.equals(VACIO)) {
                    IFELSE.tipo = VACIO;
                } else {
                    IFELSE.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else {
            error("[IFELSE]: SE ESPERABA UNA SENTENCIA DEL TIPO IF-ELSE " + "No. Línea: " + cmp.be.preAnalisis.numLinea);
        }
    }

//PRIMEROS(IFELSE_P) = { else, empty }
    private void IFELSE_P(Atributos IFELSE_P) {
        Atributos SENTENCIAS = new Atributos();

        if (preAnalisis.equals("else")) {
            //IFELSE_P -> else begin SENTENCIAS end
            emparejar("else");
            emparejar("begin");
            SENTENCIAS(SENTENCIAS);
            emparejar("end");
            if (analizarSemantica) {
                //Acción semántica
                IFELSE_P.tipo = SENTENCIAS.tipo;
                //Fin acción semántica
            }
        } else //IFELSE_P produce empty
        if (analizarSemantica) {
            //Acción semántica
            IFELSE_P.tipo = VACIO;
            //Fin acción semántica
        }
    }

//PRIMEROS(IGUALACION) = { id }
    private void IGUALACION(Atributos IGUALACION) {
        Atributos EXPRARIT = new Atributos();
        Atributos IGUALACIONP = new Atributos();
        Linea_BE id = new Linea_BE();

        if (preAnalisis.equals("id")) {
            //IGUALACION -> id opasig EXPRARIT IGUALACIONP
            if (analizarSemantica) {
                id = cmp.be.preAnalisis;
            }
            emparejar("id");
            emparejar("opasig");
            if (analizarSemantica) {
                EXPRARIT.ambito = IGUALACION.ambito;
            }
            EXPRARIT(EXPRARIT);
            if (analizarSemantica) {
                IGUALACIONP.ambito = IGUALACION.ambito;
            }
            IGUALACIONP(IGUALACIONP);
            if (analizarSemantica) {
                //Acción semántica
                //Verificamos que el sub-arbol no tenga errores
                if (IGUALACIONP.tipo.equals(VACIO)) {
                    //Verificamos que la expresión actual no tenga errores
                    if (!EXPRARIT.tipo.equals(ERROR_TIPO)) {
                        //Verificamos que id sea de tipo columna
                        if (buscaTipo(id.entrada).contains("COLUMNA")) {
                            //Verificamos que la columna pertenezca a la tabla
                            //que se está actualizando.
                            if (buscaAmbito(id.entrada).equals(IGUALACION.ambito)) {
                                //Verificamos que la expresión no contenga un id
                                if (!EXPRARIT.esId) {
                                    //Verificamos que el tipo de la expresión sea compatible
                                    //con el tipo del id
                                    String tipoId = buscaTipo(id.entrada);
                                    tipoId = tipoId.substring(tipoId.indexOf("(") + 1, tipoId.length() - 1);
                                    if (tiposCompatibles(tipoId, EXPRARIT.tipo)) {
                                        //Verificamos el caso en que se asigne una expresión real
                                        //a un id integer
                                        if (tipoId.equals("integer") && EXPRARIT.tipo.equals("real")) {
                                            IGUALACION.tipo = ERROR_TIPO;
                                            errorSemantico("[IGUALACION]: El tipo de la columna \"" + id.lexema + "\""
                                                    + " y la expresión no son compatibles. En Linea"
                                                    + " " + id.numLinea);
                                        } else {
                                            IGUALACION.tipo = VACIO;
                                        }
                                    } else {
                                        IGUALACION.tipo = ERROR_TIPO;
                                        errorSemantico("[IGUALACION]: El tipo de la columna \"" + id.lexema + "\""
                                                + " y la expresión no son compatibles. En Linea"
                                                + " " + id.numLinea);
                                    }
                                } else {
                                    IGUALACION.tipo = ERROR_TIPO;
                                    errorSemantico("[IGUALACION]: Una expresión de la sentencia"
                                            + " update-set no puede usar ids como"
                                            + " operandos. En Linea"
                                            + " " + cmp.be.preAnalisis.numLinea);
                                }

                            } else {
                                IGUALACION.tipo = ERROR_TIPO;
                                errorSemantico("[IGUALACION]: La columna \"" + id.lexema + "\""
                                        + " no pertenece a la tabla \"" + IGUALACION.ambito + "\"."
                                        + " En Linea " + id.numLinea);
                            }
                        } else {
                            IGUALACION.tipo = ERROR_TIPO;
                            errorSemantico("[IGUALACION]: El id \"" + id.lexema + "\""
                                    + " no es una columna. En Linea "
                                    + (id.numLinea));
                        }
                    } else {
                        IGUALACION.tipo = ERROR_TIPO;
                    }
                } else {
                    IGUALACION.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else {
            error("[IGUALACION]: SE ESPERABA UNA SENTENCIA DE IGUALACIÓN " + "No. Línea: " + cmp.be.preAnalisis.numLinea);
        }
    }

//---------------------------
//YAIR EMMANUEL MIERELES ORTIZ No.Ctrl: 14130078
    private void IGUALACIONP(Atributos IGUALACIONP) {
        Atributos IGUALACION = new Atributos();

        if (preAnalisis.equals(",")) {
            //IGUALACIONP -> , IGUALACION 
            emparejar(",");
            if (analizarSemantica) {
                IGUALACION.ambito = IGUALACIONP.ambito;
            }
            IGUALACION(IGUALACION);
            if (analizarSemantica) {
                //Acción semántica
                IGUALACIONP.tipo = IGUALACION.tipo;
                //Fin acción semántica
            }
        } else {
            //IGUALACIONP -> empty
            if (analizarSemantica) {
                //Acción semántica
                IGUALACIONP.tipo = VACIO;
                //Fin acción semántica
            }
        }
    }

    //Yair Emmanuel Mireles Ortiz 14130078
    private void INSERCION(Atributos INSERCION) {
        Atributos COLUMNAS = new Atributos();
        Atributos EXPRESIONES = new Atributos();
        Linea_BE id = new Linea_BE();

        if (preAnalisis.equals("insert")) {
            //INCERCION -> { insert into id ( COLUMNAS ) values ( EXPRESION )}
            if (analizarSemantica) {
                columnas.clear();
                tiposExpresiones.clear();
            }
            emparejar("insert");
            emparejar("into");
            if (analizarSemantica) {
                id = cmp.be.preAnalisis;
                if (!checarArchivo(id.lexema + ".db")) {
                    INSERCION.tipo = ERROR_TIPO;
                    errorSemantico("[INSERCION]: La tabla \"" + id.lexema + "\""
                            + " no existe. En Linea "
                            + (id.numLinea)
                    );
                    return;
                }
            }
            emparejar("id");
            emparejar("(");
            if (analizarSemantica) {
                COLUMNAS.ambito = id.lexema;
            }
            COLUMNAS(COLUMNAS);
            emparejar(")");
            emparejar("values");
            emparejar("(");
            if (analizarSemantica) {
                EXPRESIONES.ambito = id.lexema;
            }
            EXPRESIONES(EXPRESIONES);
            emparejar(")");
            if (analizarSemantica) {
                //Acción semántica

                //Verificamos que la tabla exista
                if (checarArchivo(id.lexema + ".db")) {

                    //Verificamos que no hubo errores en la
                    //definición de columnas
                    if (COLUMNAS.tipo.equals(VACIO)) {

                        //Verificamos que no hubo errores
                        //en las expresiones
                        if (EXPRESIONES.tipo.equals(VACIO)) {
                            //Verificamos que no hay errores en el
                            //mapeo de columnas y expresiones
                            if (paresColsExprsCorrectos("INSERCION")) {
                                INSERCION.tipo = VACIO;
                            } else {
                                INSERCION.tipo = ERROR_TIPO;
                            }
                        } else {
                            INSERCION.tipo = ERROR_TIPO;
                        }
                    } else {
                        INSERCION.tipo = ERROR_TIPO;
                    }
                } else {
                    INSERCION.tipo = ERROR_TIPO;
                    errorSemantico("[INSERCION]: La tabla \"" + id.lexema + "\""
                            + " no existe. En Linea "
                            + (id.numLinea)
                    );
                }
                //Fin acción semántica
            }
        } else {
            //nein
            error("[ INCERCION ]: Para realizar INSERCION es necesario la siguiente sentencia insert into id ( COLUMNAS ) values ( EXPRESION ) "
                    + "No.Linea" + cmp.be.preAnalisis.numLinea);
        }
    }

    private void LISTAIDS(Atributos LISTAIDS1) { //Yair Emmanuel Mireles Ortiz 14130078
        Atributos LISTAIDS2 = new Atributos();

        if (preAnalisis.equals(",")) {//LISTAIDS -> { , id LISTAIDS}
            emparejar(",");
            emparejar("id");
            LISTAIDS(LISTAIDS2);
            if (analizarSemantica) {
                //Acción semántica
                LISTAIDS1.tipo = LISTAIDS2.tipo;
                //Fin acción semántica
            }
        } else if (analizarSemantica) {
            //Acción semántica
            LISTAIDS1.tipo = VACIO;
            //Fin acción semántica
        }
    }

//------------------------------------------------------------------------------------------------------------------------
    //----------------------------------------------------
    //MONTES QUIROZ SABINO RUBEN    15130056
    //primeros de NUL(null,not,empty) 
    private void NULO(Atributos NULO) {
        if (preAnalisis.equals("null")) {
            //NULO -> null
            emparejar("null");
            if (analizarSemantica) {
                //Acción semántica
                NULO.tipo = VACIO;
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("not")) {
            //NULO ->not null
            emparejar("not");
            emparejar("null");
            if (analizarSemantica) {
                //Acción semántica
                NULO.tipo = VACIO;
                //Fin acción semántica
            }
        } else {
            //NULO ->empty
            if (analizarSemantica) {
                //Acción semántica
                NULO.tipo = VACIO;
                //Fin acción semántica
            }
        }
    }

    //----------------------------------------------------
    //MONTES QUIROZ SABINO RUBEN    15130056
    //primeros de OPERANDO(num,num.num,idvar,literal,id)
    private void OPERANDO(Atributos OPERANDO) {
        Linea_BE num = new Linea_BE();
        Linea_BE numnum = new Linea_BE();
        Linea_BE idvar = new Linea_BE();
        Linea_BE literal = new Linea_BE();
        Linea_BE id = new Linea_BE();

        if (preAnalisis.equals("num")) {
            //OPERANDO -> num
            if (analizarSemantica) {
                num = cmp.be.preAnalisis;
            }
            emparejar("num");
            if (analizarSemantica) {
                //Acción semántica
                OPERANDO.tipo = "integer";
                OPERANDO.valor = buscaValor(num.entrada);
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("num.num")) {
            //operando -> num.num
            if (analizarSemantica) {
                numnum = cmp.be.preAnalisis;
            }
            emparejar("num.num");
            if (analizarSemantica) {
                //Acción semántica
                OPERANDO.tipo = "real";
                OPERANDO.valor = buscaValor(numnum.entrada);
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("idvar")) {
            //operando -> invar
            idvar = cmp.be.preAnalisis;
            emparejar("idvar");
            if (analizarSemantica) {
                //Acción semántica
                //Si el idvar no ha sido declarado, ERROR_TIPO
                if (buscaTipo(idvar.entrada).equals("")) {
                    OPERANDO.tipo = ERROR_TIPO;
                    errorSemantico("[OPERANDO]: El idvar " + idvar.lexema + " no"
                            + " ha sido declarado. En Linea " + cmp.be.preAnalisis.numLinea);
                } //Si el idvar no se ha inicializado, ERROR_TIPO
                else if (buscaValor(idvar.entrada).equals("")) {
                    OPERANDO.tipo = ERROR_TIPO;
                    errorSemantico("[OPERANDO]: El idvar " + idvar.lexema + " no"
                            + " ha sido inicializado. En Linea " + cmp.be.preAnalisis.numLinea);
                } //Se obtiene el tipo, el valor y se establece la bandera
                //que es idvar
                else {
                    OPERANDO.tipo = buscaTipo(idvar.entrada);
                    OPERANDO.valor = buscaValor(idvar.entrada);
                    OPERANDO.esIdvar = true;
                }
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("literal")) {
            //operando -> literal 
            if (analizarSemantica) {
                literal = cmp.be.preAnalisis;
            }
            emparejar("literal");
            if (analizarSemantica) {
                //Acción semántica
                OPERANDO.tipo = "char(" + (literal.lexema.length() - 2) + ")";
                OPERANDO.valor = buscaValor(literal.entrada);
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("id")) {
            //operando -> id
            if (analizarSemantica) {
                id = cmp.be.preAnalisis;
            }
            emparejar("id");
            if (analizarSemantica) {
                //Acción semántica
                //Buscamos el tipo del id
                String tipo = buscaTipo(id.entrada);
                //Si es una tabla, ERROR_TTIPO
                if (tipo.equals("tabla")) {
                    OPERANDO.tipo = ERROR_TIPO;
                    errorSemantico("[OPERANDO]: No se pueden hacer operaciones"
                            + " aritméticas con operandos de tipo \"tabla\"."
                            + " En Linea " + (cmp.be.preAnalisis.numLinea - 1));
                } else {
                    String ambito = buscaAmbito(id.entrada);
                    //Se verifica que el ambito de la columna
                    //sea el correcto.
                    if (ambito.equals(OPERANDO.ambito)) {
                        OPERANDO.ambito = ambito;
                        tipo = tipo.substring(tipo.indexOf("(") + 1, tipo.length() - 1);
                        OPERANDO.tipo = tipo;
                        OPERANDO.esId = true;
                        OPERANDO.valor = "";
                    } else {
                        OPERANDO.tipo = ERROR_TIPO;
                        errorSemantico("[OPERANDO]: La columna \"" + id.lexema + "\""
                                + " no pertenece a la tabla \"" + OPERANDO.ambito + "\"."
                                + " En Linea " + (cmp.be.preAnalisis.numLinea - 1));
                    }

                }
                //Fin acción semántica
            }
        } else {
            error("[OPERANDO]: Se esperaba \"num | mun.num | invar | literal| id \" en linea " + cmp.be.preAnalisis.numLinea);
        }
        //
    }

    //----------------------------------------------------
    //MONTES QUIROZ SABINO RUBEN    15130056
    //primeros de SENTENCEA(sentencia(), empty)
    private void SENTENCIAS(Atributos SENTENCIAS) {
        Atributos SENTENCIA = new Atributos();
        Atributos SENTENCIAS1 = new Atributos();

        if (preAnalisis.equals("if")
                || preAnalisis.equals("while")
                || preAnalisis.equals("print")
                || preAnalisis.equals("assign")
                || preAnalisis.equals("select")
                || preAnalisis.equals("delete")
                || preAnalisis.equals("insert")
                || preAnalisis.equals("update")
                || preAnalisis.equals("create")
                || preAnalisis.equals("drop")
                || preAnalisis.equals("case")) {
            //sentencias -> sentencia sentencias 
            SENTENCIA(SENTENCIA);
            SENTENCIAS(SENTENCIAS1);
            if (analizarSemantica) {
                //Acción semántica
                if (SENTENCIA.tipo.equals(VACIO) && SENTENCIAS1.tipo.equals(VACIO)) {
                    SENTENCIAS.tipo = VACIO;
                } else {
                    SENTENCIAS.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else //sentencias -> empty
        if (analizarSemantica) {
            //Acción semántica
            SENTENCIAS.tipo = VACIO;
            //Fin acción semántica
        }
    }

    //---------------
    //Autor: Alexis Enrique Noyola Saenz - 14131193
    private void SELECTIVA(Atributos SELECTIVA) {

        Atributos SELWHEN = new Atributos();
        Atributos SELELSE = new Atributos();

        if (preAnalisis.equals("case")) {
            //SELECTIVA -> case SELWHEN SELELSE end 
            emparejar("case");
            SELWHEN(SELWHEN);
            SELELSE(SELELSE);
            emparejar("end");
            if (analizarSemantica) {
                //Acción semántica
                if (SELWHEN.tipo.equals(VACIO) && SELELSE.tipo.equals(VACIO)) {
                    SELECTIVA.tipo = VACIO;
                } else {
                    SELECTIVA.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else {
            error("[SELECTIVA] : < Se esperaba la sentencia 'case' >." + "No Linea: " + cmp.be.preAnalisis.numLinea);
        }
    }

    private void SELWHEN(Atributos SELWHEN) {

        Atributos EXPRCOND = new Atributos();
        Atributos SENTENCIA = new Atributos();
        Atributos SELWHEN_P = new Atributos();

        if (preAnalisis.equals("when")) {
            //SELWHEN -> when EXPRCOND then SENTENCIA SELWHEN'
            emparejar("when");
            EXPRCOND(EXPRCOND);
            emparejar("then");
            SENTENCIA(SENTENCIA);
            SELWHEN_P(SELWHEN_P);
            if (analizarSemantica) {
                //Acción semántica
                if (EXPRCOND.tipo.equals("boolean")
                        && SENTENCIA.tipo.equals(VACIO)
                        && SELWHEN_P.tipo.equals(VACIO)) {
                    SELWHEN.tipo = VACIO;
                } else {
                    SELWHEN.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else {
            error("[SELWHEN] : < Se esperaba la sentencia 'when' >." + "No Linea: " + cmp.be.preAnalisis.numLinea);
        }
    }

    private void SENTENCIA(Atributos SENTENCIA) {
        if (preAnalisis.equals("if")) {
            //SENTENCIA -> IFELSE
            Atributos IFELSE = new Atributos();
            IFELSE(IFELSE);
            if (analizarSemantica) {
                //Acción semántica
                if (IFELSE.tipo.equals(VACIO)) {
                    SENTENCIA.tipo = VACIO;
                } else {
                    SENTENCIA.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("while")) {
            //SENTENCIA -> SENREP
            Atributos SENREP = new Atributos();
            SENREP(SENREP);
            if (analizarSemantica) {
                //Acción semántica
                if (SENREP.tipo.equals(VACIO)) {
                    SENTENCIA.tipo = VACIO;
                } else {
                    SENTENCIA.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("print")) {
            //SENTENCIA -> DESPLIEGUE
            Atributos DESPLIEGUE = new Atributos();
            DESPLIEGUE(DESPLIEGUE);
            if (analizarSemantica) {
                //Acción semántica
                if (DESPLIEGUE.tipo.equals(VACIO)) {
                    SENTENCIA.tipo = VACIO;
                } else {
                    SENTENCIA.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("assign")) {
            //SENTENCIA -> SENTASIG
            Atributos SENTASIG = new Atributos();
            SENTASIG(SENTASIG);
            if (analizarSemantica) {
                //Acción semántica
                if (SENTASIG.tipo.equals(VACIO)) {
                    SENTENCIA.tipo = VACIO;
                } else {
                    SENTENCIA.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("select")) {
            //SENTENCIA -> SENTSELECT
            Atributos SENTSELECT = new Atributos();
            SENTSELECT(SENTSELECT);
            if (analizarSemantica) {
                //Acción semántica
                if (SENTSELECT.tipo.equals(VACIO)) {
                    SENTENCIA.tipo = VACIO;
                } else {
                    SENTENCIA.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("delete")) {
            //SENTENCIA -> DELREG
            Atributos DELREG = new Atributos();
            DELREG(DELREG);
            if (analizarSemantica) {
                //Acción semántica
                if (DELREG.tipo.equals(VACIO)) {
                    SENTENCIA.tipo = VACIO;
                } else {
                    SENTENCIA.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("insert")) {
            //SENTENCIA -> INSERCION
            Atributos INSERCION = new Atributos();
            INSERCION(INSERCION);
            if (analizarSemantica) {
                //Acción semántica
                if (INSERCION.tipo.equals(VACIO)) {
                    SENTENCIA.tipo = VACIO;
                } else {
                    SENTENCIA.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("update")) {
            //SENTENCIA -> ACTREGS
            Atributos ACTREGS = new Atributos();
            ACTREGS(ACTREGS);
            if (analizarSemantica) {
                //Acción semántica
                if (ACTREGS.tipo.equals(VACIO)) {
                    SENTENCIA.tipo = VACIO;
                } else {
                    SENTENCIA.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("create")) {
            //SENTENCIA -> TABLA
            Atributos TABLA = new Atributos();
            TABLA(TABLA);
            if (analizarSemantica) {
                //Acción semántica
                if (TABLA.tipo.equals(VACIO)) {
                    SENTENCIA.tipo = VACIO;
                } else {
                    SENTENCIA.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("drop")) {
            //SENTENCIA -> ELIMTAB
            Atributos ELIMTAB = new Atributos();
            ELIMTAB(ELIMTAB);
            if (analizarSemantica) {
                //Acción semántica
                if (ELIMTAB.tipo.equals(VACIO)) {
                    SENTENCIA.tipo = VACIO;
                } else {
                    SENTENCIA.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("case")) {
            //SENTENCIA -> SELECTIVA
            Atributos SELECTIVA = new Atributos();
            SELECTIVA(SELECTIVA);
            if (analizarSemantica) {
                //Acción semántica
                if (SELECTIVA.tipo.equals(VACIO)) {
                    SENTENCIA.tipo = VACIO;
                } else {
                    SENTENCIA.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else {
            error("[SENTENCIA] : < Se esperaba la sentencia SQL valida >." + "No Linea: " + cmp.be.preAnalisis.numLinea);
        }
    }

//-------------------------------
    //Agustín Pérez Calderón    14130042
    //PRIMEROS(SELWHEN_P) = {PRIMEROS(SELWHEN),empty }
    //----------------------PRIMEROS(SELWHEN) = {when}
    private void SELWHEN_P(Atributos SELWHEN_P) {

        Atributos SELWHEN = new Atributos();

        if (preAnalisis.equals("when")) {
            //SELWHEN’ -> SELWHEN
            SELWHEN(SELWHEN);
            if (analizarSemantica) {
                //Acción semántica
                SELWHEN_P.tipo = SELWHEN.tipo;
                //Fin acción semántica
            }
        } else //SELWHEN’ -> empty
        if (analizarSemantica) {
            //Acción semántica
            SELWHEN_P.tipo = VACIO;
            //Fin acción semántica
        }
    }

    //PRIMEROS(SELELSE) = {else,empty}
    private void SELELSE(Atributos SELELSE) {

        Atributos SENTENCIA = new Atributos();

        if (preAnalisis.equals("else")) {
            //SELELSE -> else SENTENCIA
            emparejar("else");
            SENTENCIA(SENTENCIA);
            if (analizarSemantica) {
                //Acción semántica
                SELELSE.tipo = SENTENCIA.tipo;
                //Fin acción semántica
            }
        } else //SELELSE -> empty
        if (analizarSemantica) {
            //Acción semántica
            SELELSE.tipo = VACIO;
            //Fin acción semántica
        }
    }

    //PRIMEROS(SENREP) = {while}
    private void SENREP(Atributos SENREP) {

        Atributos EXPRCOND = new Atributos();
        Atributos SENTENCIAS = new Atributos();

        if (preAnalisis.equals("while")) {
            //SENREP -> while EXPRCOND begin SENTENCIAS end
            emparejar("while");
            EXPRCOND(EXPRCOND);
            emparejar("begin");
            SENTENCIAS(SENTENCIAS);
            emparejar("end");
            if (analizarSemantica) {
                //Acción semántica
                if (EXPRCOND.tipo.equals("boolean") && SENTENCIAS.tipo.equals(VACIO)) {
                    SENREP.tipo = VACIO;
                } else {
                    SENREP.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else {
            error("[SENREP] : Se esperaba la sentencia while" + " Linea " + cmp.be.preAnalisis.numLinea);
        }
    }
//-----------------------------------------------------------
//Wendy Guadalupe Ramirez Lucio		#14131244

//Primeros (SENTASIG) = {assign}
//Primeros (SENTSELECT) = {select}
    private void SENTASIG(Atributos SENTASIG) {

        Atributos EXPRARIT = new Atributos();
        Linea_BE idvar = new Linea_BE();

        if (preAnalisis.equals("assign")) {
            //SENTASIG -> assign idvar opasig EXPRARIT
            emparejar("assign");
            if (analizarSemantica) {
                idvar = cmp.be.preAnalisis;
            }
            emparejar("idvar");
            emparejar("opasig");
            EXPRARIT(EXPRARIT);
            if (analizarSemantica) {
                //Acción semántica
                //Si hay un error en la expresión aritmética,
                //el error ya se cachó y se asigna ERROR_TIPO
                if (EXPRARIT.tipo.equals(ERROR_TIPO)) {
                    SENTASIG.tipo = ERROR_TIPO;
                } else {
                    //Si la expresión es correcta, se verifica que el
                    //idvar haya sido declarado, si no, ERROR_TIPO
                    if (buscaTipo(idvar.entrada).equals("")) {
                        SENTASIG.tipo = ERROR_TIPO;
                        errorSemantico("[SENTASIG]: El identificador \"" + idvar.lexema + "\" no ha"
                                + " sido declarado. En Linea " + (cmp.be.preAnalisis.numLinea - 1));
                    } //Si el identificador está declarado, se verifican sus tipos.
                    else {
                        if (tiposCompatibles(buscaTipo(idvar.entrada), EXPRARIT.tipo)) {
                            //Si son compatibles...
                            //Si el tipo de la expresión es real y
                            //el tipo del identificador es integer, ERROR_TIPO
                            if (EXPRARIT.tipo.equals("real")
                                    && buscaTipo(idvar.entrada).equals("integer")) {
                                SENTASIG.tipo = ERROR_TIPO;
                                errorSemantico("[SENTASIG]: El tipo del identificador \""
                                        + idvar.lexema
                                        + "\" y la expresión"
                                        + " no son compatibles. En Linea " + (cmp.be.preAnalisis.numLinea - 1));
                            } //Si no, VACIO y se agrega el valor a la
                            //Tabla de Simbolos
                            else {
                                SENTASIG.tipo = VACIO;
                                anadeValor(idvar.entrada, EXPRARIT.valor);
                            }
                        } else {
                            SENTASIG.tipo = ERROR_TIPO;
                            errorSemantico("[SENTASIG]: El tipo del identificador \""
                                    + idvar.lexema
                                    + "\" y la expresión"
                                    + " no son compatibles. En Linea " + (cmp.be.preAnalisis.numLinea - 1));
                        }
                    }
                }
                //Fin acción semántica
            }
        } else {
            error("[SENTASIG]: Se esperaba la sentencia assign"
                    + " No. Linea " + cmp.be.preAnalisis.numLinea);
        }
    }

    private void SENTSELECT(Atributos SENTSELECT) {

        Atributos SENTSELECTC = new Atributos();
        Atributos EXPRCOND = new Atributos();
        Linea_BE idvar = new Linea_BE();
        Linea_BE id1 = new Linea_BE();
        Linea_BE id2 = new Linea_BE();

        if (preAnalisis.equals("select")) {
            //SENTSELECT -> select idvar opasig id SENTSELECTC from id where EXPRCOND
            if (analizarSemantica) {
                paresIdvarId.clear();
            }
            emparejar("select");
            if (analizarSemantica) {
                idvar = cmp.be.preAnalisis;
            }
            emparejar("idvar");
            emparejar("opasig");
            if (analizarSemantica) {
                id1 = cmp.be.preAnalisis;
            }
            emparejar("id");
            SENTSELECTC(SENTSELECTC);
            emparejar("from");
            if (analizarSemantica) {
                id2 = cmp.be.preAnalisis;
                if (!checarArchivo(id2.lexema + ".db")) {
                    SENTSELECT.tipo = ERROR_TIPO;
                    errorSemantico("[SENTSELECT]: La tabla \"" + id2.lexema + "\""
                            + " no existe. En Linea "
                            + (cmp.be.preAnalisis.numLinea - 1)
                    );
                    return;
                }
            }
            emparejar("id");
            emparejar("where");
            if (analizarSemantica) {
                EXPRCOND.ambito = id2.lexema;
            }
            EXPRCOND(EXPRCOND);
            if (analizarSemantica) {
                //Acción semántica
                //Si existe la tabla, continua, si no, ERROR_TIPO
                if (checarArchivo(id2.lexema + ".db")) {
                    //Si EXPRCOND es boolean, continua, si no, ERROR_TIPO
                    if (EXPRCOND.tipo.equals("boolean")) {
                        //Si idvar no está declarado, ERROR_TIPO
                        if (buscaTipo(idvar.entrada).equals("")) {
                            SENTSELECT.tipo = ERROR_TIPO;
                            errorSemantico("[SENTSELECT]: El idvar \"" + idvar.lexema + "\""
                                    + " no está declarado. En Linea "
                                    + (cmp.be.preAnalisis.numLinea - 1)
                            );
                        } else {
                            //Si id1 no es una columna, ERROR_TIPO
                            if (buscaTipo(id1.entrada).contains("COLUMNA")) {
                                //Si id1 no es una columna de la tabla id2
                                //(Que el ambito sea correcto), ERROR_TIPO
                                if (buscaAmbito(id1.entrada).equals(id2.lexema)) {
                                    //Si los tipos de la columna y el idvar son compatibles,
                                    //continua, si no, ERROR_TIPO
                                    if (tiposCompatibles(buscaTipo(idvar.entrada),
                                            buscaTipo(id1.entrada).
                                                    substring(
                                                            buscaTipo(id1.entrada).indexOf("(") + 1,
                                                            buscaTipo(id1.entrada).length() - 1)
                                    )) {

                                        //Validamos asignación de un valor real a un idvar integer.
                                        String tidvar = buscaTipo(idvar.entrada);
                                        String texpr = buscaTipo(id1.entrada);
                                        texpr = texpr.substring(texpr.indexOf("(") + 1, texpr.length() - 1);
                                        if (tidvar.equals("integer") && texpr.equals("real")) {
                                            SENTSELECT.tipo = ERROR_TIPO;
                                            errorSemantico("[SENTSELECT]: El tipo del idvar \"" + idvar.lexema + "\""
                                                    + " y la columna \"" + id1.lexema + "\" no son"
                                                    + " compatibles. En Linea " + (cmp.be.preAnalisis.numLinea - 1));
                                        } else {
                                            //Ahora, si todas las demás asignaciones son correctas, VACIO,
                                            //si no, ERROR_TIPO
                                            if (paresIdvarIdCorrectos(id2.lexema, "SENTSELECT")) {
                                                SENTSELECT.tipo = VACIO;
                                            } else {
                                                SENTSELECT.tipo = ERROR_TIPO;
                                            }
                                        }
                                    } else {
                                        SENTSELECT.tipo = ERROR_TIPO;
                                        errorSemantico("[SENTSELECT]: El tipo del idvar \"" + idvar.lexema + "\""
                                                + " y la columna \"" + id1.lexema + "\" no son"
                                                + " compatibles. En Linea " + (cmp.be.preAnalisis.numLinea - 1));
                                    }
                                } else {
                                    SENTSELECT.tipo = ERROR_TIPO;
                                    errorSemantico("[SENTSELECT]: La columna \"" + id1.lexema + "\""
                                            + " no es una columna de la tabla "
                                            + "\"" + id2.lexema + "\"."
                                            + " En Linea "
                                            + (cmp.be.preAnalisis.numLinea - 1)
                                    );
                                }
                            } else {
                                SENTSELECT.tipo = ERROR_TIPO;
                                errorSemantico("[SENTSELECT]: El id \"" + id1.lexema + "\""
                                        + " no es una columna. En Linea "
                                        + (cmp.be.preAnalisis.numLinea - 1)
                                );
                            }
                        }
                    } else {
                        SENTSELECT.tipo = ERROR_TIPO;
                    }
                } else {
                    SENTSELECT.tipo = ERROR_TIPO;
                    errorSemantico("[SENTSELECT]: La tabla \"" + id2.lexema + "\""
                            + " no existe. En Linea "
                            + (cmp.be.preAnalisis.numLinea - 1)
                    );
                }
                //Fin acción semántica
            }
        } else {
            error("[SENTSELECT]: Se esperaba la sentencia select" + " No. Linea " + cmp.be.preAnalisis.numLinea);
        }
    }

//--- Autor: Jose Eduardo Rodriguez Diaz 13130453
    //Primeros (SENTSELECT)= {, , empty}
    private void SENTSELECTC(Atributos SENTSELECTC) {

        Atributos SENTSELECTC1 = new Atributos();
        Linea_BE idvar = new Linea_BE();
        Linea_BE id = new Linea_BE();

        if (preAnalisis.equals(",")) {
            //SENTSELECTC -> , idvar opasig id SENTSELECTC
            emparejar(",");
            if (analizarSemantica) {
                idvar = cmp.be.preAnalisis;
            }
            emparejar("idvar");
            emparejar("opasig");
            if (analizarSemantica) {
                id = cmp.be.preAnalisis;
            }
            emparejar("id");
            SENTSELECTC(SENTSELECTC1);
            if (analizarSemantica) {
                //Acción semántica
                if (SENTSELECTC1.tipo.equals(VACIO)) {
                    SENTSELECTC.tipo = VACIO;
                } else {
                    SENTSELECTC.tipo = ERROR_TIPO;
                }
                paresIdvarId.push(new Pair<>(idvar, id));
                //Fin acción semántica
            }
        } else //SENTSELECTC -> empty
        if (analizarSemantica) {
            //Acción semántica
            SENTSELECTC.tipo = VACIO;
            //Fin acción semántica
        }
    }
    //Primeros ( TIPO ) = {int , float , char}

    private void TIPO(Atributos TIPO) {
        Linea_BE num = new Linea_BE();
        if (preAnalisis.equals("int")) {
            // TIPO ---> int
            emparejar("int");
            if (analizarSemantica) {
                //Acción semántica
                TIPO.tipo = "integer";
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("float")) {
            // TIPO ---> float
            emparejar("float");
            if (analizarSemantica) {
                //Acción semántica
                TIPO.tipo = "real";
                //Fin acción semántica
            }
        } else if (preAnalisis.equals("char")) {
            //TIPO ---> char (num)
            emparejar("char");
            emparejar("(");
            if (analizarSemantica) {
                num = cmp.be.preAnalisis;
            }
            emparejar("num");
            emparejar(")");

            if (analizarSemantica) {
                //Acción semántica
                TIPO.tipo = "char(" + num.lexema + ")";
                //Fin acción semántica
            }
        } else {
            error("[TIPO] Se esperaba un tipo de dato int, float , char "
                    + "Linea " + cmp.be.preAnalisis.numLinea);
        }
    }

    private void TABLA(Atributos TABLA) {
        //PRIMEROS TABLA = {create}
        Atributos TABCOLUMNAS = new Atributos();
        Linea_BE id = new Linea_BE();

        if (preAnalisis.equals("create")) {
            //TABLA ---> create table id (TABCOLUMNAS)
            emparejar("create");
            emparejar("table");
            if (analizarSemantica) {
                id = cmp.be.preAnalisis;
            }
            emparejar("id");
            emparejar("(");
            if (analizarSemantica) {
                TABCOLUMNAS.ambito = id.lexema;
            }
            TABCOLUMNAS(TABCOLUMNAS);
            emparejar(")");
            if (analizarSemantica) {
                //Acción semántica
                //Si no hubo errores semánticos en la
                //definición de las columnas...
                if (TABCOLUMNAS.tipo.equals(VACIO)) {
                    //Si ya existía esa tabla, ERROR_TIPO
                    if (checarArchivo(id.lexema + ".db")) {
                        TABLA.tipo = ERROR_TIPO;
                        errorSemantico("[TABLA]: Ya existe la tabla \""
                                + id.lexema + "\". En Linea "
                                + (cmp.be.preAnalisis.numLinea - 1));
                    } //Si no, VACIO, guardamos la definición de la tabla
                    //En un archivo y agregamos el tipo del id
                    else {
                        TABLA.tipo = VACIO;
                        crearArchivo(id);
                        anadeTipo(id.entrada, "tabla");
                    }
                } else {
                    TABLA.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }

        } else {
            error("[TABLA] Para crear un tabla es necesario utilizar create"
                    + " Linea " + cmp.be.preAnalisis.numLinea);
        }
    }

    //-------------------------------------------------------
    // David Soto Rodriguez     #14130602
    //Primero(TABCOLUMAS) = { id TIPO NULO TABCOLUMNAS_P }
    private void TABCOLUMNAS(Atributos TABCOLUMNAS) {
        Atributos TIPO = new Atributos();
        Atributos NULO = new Atributos();
        Atributos TABCOLUMNAS_P = new Atributos();
        Linea_BE id = new Linea_BE();

        if (preAnalisis.equals("id")) {
            //TABCOLUMNAS -> { id TIPO NULO TABCOLUMNAS_P }
            if (analizarSemantica) {
                id = cmp.be.preAnalisis;
            }
            emparejar("id");
            TIPO(TIPO);
            NULO(NULO);
            if (analizarSemantica) {
                TABCOLUMNAS_P.ambito = TABCOLUMNAS.ambito;
            }
            TABCOLUMNAS_P(TABCOLUMNAS_P);
            if (analizarSemantica) {
                //Acción semántica
                //Si el tipo de TIPO, NULO y TABCOLUMNAS_P es VACIO, VACIO,
                //Si no, ERROR_TIPO
                if (!TIPO.tipo.equals(ERROR_TIPO) && NULO.tipo.equals(VACIO)
                        && TABCOLUMNAS_P.tipo.equals(VACIO)) {
                    //Si la columna ya había sido especificada, ERROR_TIPO
                    if (existeSimbolo(id.lexema, TABCOLUMNAS.ambito)) {
                        TABCOLUMNAS.tipo = ERROR_TIPO;
                        errorSemantico("[TABCOLUMNAS]: La columna \""
                                + id.lexema
                                + "\"ya existe. En Linea "
                                + (cmp.be.preAnalisis.numLinea - 1));
                    } else {
                        TABCOLUMNAS.tipo = VACIO;
                        anadeTipo(id.entrada, "COLUMNA("
                                + TIPO.tipo
                                + ")");
                        anadeAmbito(id.entrada, TABCOLUMNAS.ambito);
                    }
                } else {
                    TABCOLUMNAS.tipo = ERROR_TIPO;
                }
                //Fin acción semántica
            }
        } else {
            //error( "En TABCOLUMNAS" );
            //error("[<nombre-procedure> ]: <descripcion del error>"+ " No.Linea" + cmp.be.preAnalisis.numLinea
            //);
            error("[ TABCOLUMAS ]: Para definir TABCOLUMNAS es necesario un identificador "
                    + "No.Linea" + cmp.be.preAnalisis.numLinea);
        }
    }

    //---------------------------------------------------------
    // David Soto Rodriguez     #14130602
    //Primero(TABCOLUMAS_P) = { , TABCOLUMNAS | empty }
    private void TABCOLUMNAS_P(Atributos TABCOLUMNAS_P) {
        Atributos TABCOLUMNAS = new Atributos();
        if (preAnalisis.equals(",")) {
            //TABCOLUMNAS_P -> {, TABCOLUMNAS }
            emparejar(",");
            if (analizarSemantica) {
                TABCOLUMNAS.ambito = TABCOLUMNAS_P.ambito;
            }
            TABCOLUMNAS(TABCOLUMNAS);
            if (analizarSemantica) {
                //Acción semántica
                TABCOLUMNAS_P.tipo = TABCOLUMNAS.tipo;
                //Fin acción semántica
            }
        } else //TABCOLUMNAS_P -> empty
        if (analizarSemantica) {
            //Acción semántica
            TABCOLUMNAS_P.tipo = VACIO;
            //Fin acción semántica
        }
    }
}
//------------------------------------------------------------------------------
//::
