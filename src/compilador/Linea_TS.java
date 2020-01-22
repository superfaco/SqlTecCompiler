/*:-----------------------------------------------------------------------------
 *:                       INSTITUTO TECNOLOGICO DE LA LAGUNA
 *:                     INGENIERIA EN SISTEMAS COMPUTACIONALES
 *:                         LENGUAJES Y AUTOMATAS II           
 *: 
 *:        SEMESTRE: ______________            HORA: ______________ HRS
 *:                                   
 *:               
 *:    # Clase que representa una localidad de la Tabla de Simbolos
 *                 
 *:                           
 *: Archivo       : Linea_TS.java
 *: Autor         : Fernando Gil  
 *: Fecha         : 03/SEP/2014
 *: Compilador    : Java JDK 7
 *: Descripción   :  
 *:                  
 *:           	     
 *: Ult.Modif.    :
 *:  Fecha      Modificó            Modificacion
 *:=============================================================================
 *:-----------------------------------------------------------------------------
 */


package compilador;

public class Linea_TS 
{
    
        private String complex;
        private String lexema;
        private String tipo;
        private String ambito;
        private String valor;

        //---------------------------------------------------------------------
       //Constructores 
        public Linea_TS()
        {
            complex = "";
            lexema  = "";
            tipo    = "";
            ambito  = "";
            valor = "";
        }

        public Linea_TS( String _complex, String _lexema, String _tipo, String _ambito, String _valor)
        {
            complex = _complex;
            lexema  = _lexema;
            tipo    = _tipo;
            ambito  = _ambito;
            valor = _valor;
        }

        public Linea_TS(Linea_TS T)
        {
            this.complex = T.complex;
            this.lexema  = T.lexema;
            this.tipo    = T.tipo;
            this.ambito  = T.ambito;
            this.valor = T.valor;
        }
        
        //----------------------------------------------------------------------
       //Metodos get/set
       
        public void setComplex ( String Complex )
        {
            complex = Complex;
        }
        
        public String getComplex ( )
        {
            return complex;
        }
        
        //----------------------------------------------------------------------
        
         public void setLExema ( String Lexema )
        {
            lexema = Lexema;
        }
        
        public String getLexema ( )
        {
            return lexema;
        }
        //----------------------------------------------------------------------
        
         public void setTipo ( String Tipo )
        {
            tipo = Tipo;
        }
        
        public String getTipo ( )
        {
            return tipo;
        }
        
       //----------------------------------------------------------------------
       
        public void setAmbito ( String Ambito )
        {
            ambito = Ambito;
        }
        
        public String getAmbito ( )
        {
            return ambito;
        }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    
    
}
