package proyecto_de_sanabria;

import java.util.*;

/**
 * ============================================================
 *  SOLUCIONADOR GENERAL DE REDES PERT / CPM  (por consola)
 * ============================================================
 *  El usuario ingresa TODOS los datos del problema:
 *    - Metodo (CPM o PERT)
 *    - Numero de actividades
 *    - Por actividad: identificador, descripcion, duracion(es)
 *      y actividades predecesoras
 *
 *  El programa calcula:
 *    - Orden topologico (y detecta ciclos / datos invalidos)
 *    - Paso hacia adelante : ES (inicio temprano), EF (fin temprano)
 *    - Paso hacia atras    : LS (inicio tardio),  LF (fin tardio)
 *    - Holgura (slack) = LS - ES
 *    - Ruta(s) critica(s) y duracion del proyecto
 *    - PERT: tiempo esperado, varianza, desviacion estandar del
 *            proyecto y probabilidad de terminar antes de un plazo,
 *            leida de la TABLA NORMAL ESTANDAR (Z = (T - mu)/sigma)
 *
 *  Compilar:  javac PertCpm.java
 *  Ejecutar:  java PertCpm
 */
public class ProyectoPertCpm {

    /** Tolerancia para comparar numeros decimales (evita errores de redondeo). */
    static final double EPS = 1e-9;

    /** Lector unico de teclado para todo el programa. */
    static final Scanner sc = new Scanner(System.in);

    // ------------------------------------------------------------------
    //  CLASE ACTIVIDAD: guarda todos los datos de un nodo/actividad
    // ------------------------------------------------------------------
    static class Actividad {
        String id;            // Identificador unico (ej. "A")
        String descripcion;   // Texto descriptivo (opcional)
        double a, m, b;       // Tiempos optimista, mas probable, pesimista (PERT)
        double duracion;      // Duracion usada en el calculo (CPM: d; PERT: te)
        double varianza;      // Varianza de la actividad (solo PERT)
        List<String> idsPred = new ArrayList<>();      // IDs de predecesoras (texto)
        List<Actividad> pred = new ArrayList<>();      // Predecesoras (objetos)
        List<Actividad> suc  = new ArrayList<>();      // Sucesoras (objetos)
        double ES, EF, LS, LF, holgura;                // Resultados del calculo

        /** Una actividad es critica si su holgura es cero. */
        boolean esCritica() {
            return Math.abs(holgura) < EPS;
        }
    }

    // ------------------------------------------------------------------
    //  METODOS DE ENTRADA (validan lo que escribe el usuario)
    // ------------------------------------------------------------------

    /** Lee un numero real >= min. Acepta coma o punto decimal. */
    static double leerReal(String mensaje, double min) {
        while (true) {
            System.out.print(mensaje);
            String linea = sc.nextLine().trim().replace(',', '.');
            try {
                double v = Double.parseDouble(linea);
                if (v >= min) return v;
                System.out.println("  Debe ser >= " + min + ". Intente de nuevo.");
            } catch (NumberFormatException e) {
                System.out.println("  Numero invalido. Intente de nuevo.");
            }
        }
    }

    /** Lee un entero >= min. */
    static int leerEntero(String mensaje, int min) {
        while (true) {
            System.out.print(mensaje);
            String linea = sc.nextLine().trim();
            try {
                int v = Integer.parseInt(linea);
                if (v >= min) return v;
                System.out.println("  Debe ser >= " + min + ". Intente de nuevo.");
            } catch (NumberFormatException e) {
                System.out.println("  Entero invalido. Intente de nuevo.");
            }
        }
    }

    /**
     * Lee todas las actividades del problema.
     * @param modoPert true = pide a, m, b ; false = pide una sola duracion (CPM)
     */
    static Map<String, Actividad> leerActividades(boolean modoPert) {
        // LinkedHashMap conserva el orden en que se ingresaron.
        Map<String, Actividad> mapa = new LinkedHashMap<>();
        int n = leerEntero("Numero de actividades: ", 1);

        for (int i = 1; i <= n; i++) {
            System.out.println("\n--- Actividad " + i + " de " + n + " ---");
            Actividad act = new Actividad();

            // 1) Identificador unico (se guarda en mayusculas)
            while (true) {
                System.out.print("Identificador (ej. A): ");
                String id = sc.nextLine().trim().toUpperCase();
                if (id.isEmpty() || id.contains(",") || id.contains(" ")) {
                    System.out.println("  Sin espacios ni comas, y no vacio.");
                } else if (mapa.containsKey(id)) {
                    System.out.println("  Ese identificador ya existe.");
                } else {
                    act.id = id;
                    break;
                }
            }