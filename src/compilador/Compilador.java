/*:-----------------------------------------------------------------------------
 *:                       INSTITUTO TECNOLOGICO DE LA LAGUNA
 *:                     INGENIERIA EN SISTEMAS COMPUTACIONALES
 *:                         LENGUAJES Y AUTOMATAS II           
 *: 
 *:        SEMESTRE: ______________            HORA: ______________ HRS
 *:                                   
 *:               
 *:    # Clase controladora que representa a todo el compilador
 *                 
 *:                           
 *: Archivo       : Compilador.java
 *: Autor         : Fernando Gil  
 *: Fecha         : 03/SEP/2014
 *: Compilador    : Java JDK 7
 *: Descripción   : Esta clase es la que interactua con las clases Lexico, 
 *:                 BufferEntrada, TablaSimbolos, SintacticoSemantico,
 *:                 GenCodigoInt, ManejErrores, ErroresListener, para
 *:                 realizar las funciones de un compilador para un subconjunto 
 *:                 de sentencias de lenguaje Pascal .
 *:           	     
 *: Ult.Modif.    :
 *:  Fecha      Modificó            Modificacion
 *:=============================================================================
 *:-----------------------------------------------------------------------------
 */


package compilador;


public class Compilador {

	
    // Constantes
    public final static int ERR_LEXICO     = 1;
    public final static int ERR_SINTACTICO = 2;
    public final static int ERR_SEMANTICO  = 3;
    public final static int ERR_CODINT     = 4;
    public final static int WARNING_SEMANT = 5;

    // Composicion del compilador
	Lexico              lexico = new Lexico              ( this );
	SintacticoSemantico ss     = new SintacticoSemantico ( this );
	BufferEntrada       be     = new BufferEntrada       ( this );
	TablaSimbolos       ts     = new TablaSimbolos       ( this );
	ManejErrores        me     = new ManejErrores        ( this );

	ErroresListener erroresListener = null;

	GenCodigoInt gci = new GenCodigoInt(this);
    

	//--------------------------------------------------------------------------
	// Constructor de Default
	
    public Compilador(){
    }
	
	//--------------------------------------------------------------------------
	// Constructor para inicializar el objeto que escuchara los errores de compilacion
    
	public Compilador  ( ErroresListener errList ) {
		erroresListener  = errList;
	}
    
	//--------------------------------------------------------------------------
    
	public void analizarLexico ( String codigoFuente ){
		me.inicializar ();
		be.inicializar (); 
		ts.inicializar ();
		lexico.Inicia  ();
		lexico.Analiza ( codigoFuente );
    }
    
	//--------------------------------------------------------------------------
    
	public void analizarSintaxis(){
		me.inicializar ();
		be.restablecer ();             // Colocar el preAnalisis al inicio del buffer
		ss.analizar    ( false );      // Arrancar el analisis sintactico sin comprobacion semantica
	}
    
	//--------------------------------------------------------------------------
    
	public void analizarSemantica(){
        me.inicializar ();
        be.restablecer ();             // Colocar el preAnalisis al inicio del buffer
        ss.analizar    ( true );       // Arrancar el analisis sintactico con comprobacion semantica
	}
    
	//--------------------------------------------------------------------------
    
	public void generarCodigoInt(){
        me.inicializar ();
        be.restablecer ();             // Colocar el preAnalisis al inicio del buffer
        gci.generar    ();             // Arrancar la generacion de codigo intermedio
	}
    
	//--------------------------------------------------------------------------
    
	public void agregErroresListener ( ErroresListener listener ) {
		erroresListener = listener;
	}
    
	//--------------------------------------------------------------------------
    
	public  Linea_BE[] getBufferEntrada(){
		int tam = be.getTamaño();
		Linea_BE [] buffer = new Linea_BE [ tam ];
        
		for ( int i = 0; i < tam; i++ ) {
			buffer [ i ] = be.obtElemento ( i );
		}
		return buffer;
	}
    
	//--------------------------------------------------------------------------
    
	public  Linea_TS[] getTablaSimbolos(){
        
		int tam = ts.getTamaño();
		Linea_TS [] buffer = new Linea_TS [ tam ];
        
		for ( int i = 0; i < tam; i++ ) {
			buffer [ i ] = ts.obt_elemento( i );
		}
		return buffer;
	}
    
	//--------------------------------------------------------------------------
	
	//--------------------------------------------------------------------------
    // 24/Oct/2012

    public int getTotErrores ( int tipoError ) {
        int toterr = 0;
        switch ( tipoError ) {
            case Compilador.ERR_LEXICO     : toterr =  me.getTotErrLexico      ();
                                             break; 
            case Compilador.ERR_SINTACTICO : toterr =  me.getTotErrSintacticos ();   
                                             break;
            case Compilador.ERR_SEMANTICO  : toterr =  me.getTotErrSemanticos  ();
                                             break;
            case Compilador.ERR_CODINT     : toterr =  me.getTotErrCodInt      ();

            case Compilador.WARNING_SEMANT : toterr =  me.getTotWarningsSem    ();
        }
        return toterr;
    }
    
    //--------------------------------------------------------------------------
    
}
