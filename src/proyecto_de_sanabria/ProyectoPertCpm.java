package proyecto_de_sanabria;

import java.util.*;

/**
 * ============================================================
 *  SOLUCIONADOR GENERAL DE REDES PERT / CPM  -  PARTE 1 
 * ============================================================
 *  Contiene: clase Actividad, entrada de datos, construccion/validacion
 *  de la red, orden topologico y calculo CPM (paso adelante, paso atras
 *  y rutas criticas).
 *
 *  La Parte 2 (PertCpmTest.java) hereda de esta clase y trae el resto.
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

            // 2) Descripcion (opcional)
            System.out.print("Descripcion (opcional, Enter para omitir): ");
            act.descripcion = sc.nextLine().trim();

            // 3) Duraciones segun el metodo
            if (modoPert) {
                while (true) {
                    act.a = leerReal("  Tiempo optimista  (a): ", 0);
                    act.m = leerReal("  Tiempo mas probable (m): ", 0);
                    act.b = leerReal("  Tiempo pesimista  (b): ", 0);
                    if (act.a <= act.m && act.m <= act.b) break;
                    System.out.println("  Debe cumplirse a <= m <= b. Reingrese.");
                }
                // Formulas PERT (distribucion Beta):
                act.duracion = (act.a + 4 * act.m + act.b) / 6.0;      // tiempo esperado te
                act.varianza = Math.pow((act.b - act.a) / 6.0, 2);     // varianza
            } else {
                act.duracion = leerReal("  Duracion: ", 0);
                act.a = act.m = act.b = act.duracion;
                act.varianza = 0;
            }

            // 4) Predecesoras: lista separada por comas, o vacio / "-" si no tiene
            System.out.print("Predecesoras (ej. A,B ; Enter o '-' si no tiene): ");
            String linea = sc.nextLine().trim().toUpperCase();
            if (!linea.isEmpty() && !linea.equals("-")) {
                for (String p : linea.split(",")) {
                    p = p.trim();
                    if (!p.isEmpty() && !act.idsPred.contains(p)) act.idsPred.add(p);
                }
            }
            mapa.put(act.id, act);
        }
        return mapa;
    }

    // ------------------------------------------------------------------
    //  CONSTRUCCION Y VALIDACION DE LA RED
    // ------------------------------------------------------------------

    /**
     * Convierte los IDs de predecesoras en referencias a objetos y arma las
     * listas de sucesoras. Devuelve false si hay un ID inexistente o una
     * actividad que depende de si misma.
     */
    static boolean enlazar(Map<String, Actividad> mapa) {
        for (Actividad act : mapa.values()) {
            for (String idP : act.idsPred) {
                Actividad p = mapa.get(idP);
                if (p == null) {
                    System.out.println("ERROR: la actividad " + act.id
                            + " tiene una predecesora inexistente: " + idP);
                    return false;
                }
                if (p == act) {
                    System.out.println("ERROR: la actividad " + act.id
                            + " no puede ser predecesora de si misma.");
                    return false;
                }
                act.pred.add(p);
                p.suc.add(act);
            }
        }
        return true;
    }

    /**
     * Orden topologico (algoritmo de Kahn): ordena las actividades de modo que
     * cada una aparezca despues de todas sus predecesoras.
     * Si no se pueden ordenar todas, existe un CICLO (red invalida).
     * @return lista ordenada, o null si hay ciclo
     */
    static List<Actividad> ordenTopologico(Map<String, Actividad> mapa) {
        Map<Actividad, Integer> gradoEntrada = new HashMap<>();
        Deque<Actividad> cola = new ArrayDeque<>();

        for (Actividad a : mapa.values()) {
            gradoEntrada.put(a, a.pred.size());
            if (a.pred.isEmpty()) cola.add(a);   // actividades iniciales
        }

        List<Actividad> orden = new ArrayList<>();
        while (!cola.isEmpty()) {
            Actividad a = cola.poll();
            orden.add(a);
            for (Actividad s : a.suc) {
                int g = gradoEntrada.get(s) - 1;
                gradoEntrada.put(s, g);
                if (g == 0) cola.add(s);
            }
        }

        if (orden.size() != mapa.size()) {
            System.out.print("ERROR: hay un ciclo en las dependencias. Actividades involucradas: ");
            for (Actividad a : mapa.values())
                if (!orden.contains(a)) System.out.print(a.id + " ");
            System.out.println();
            return null;
        }
        return orden;
    }

    // ------------------------------------------------------------------
    //  CALCULO CPM (nucleo del algoritmo)
    // ------------------------------------------------------------------

    /**
     * Paso hacia adelante:
     *   ES = max(EF de las predecesoras)   (0 si no tiene)
     *   EF = ES + duracion
     * @return duracion total del proyecto = max(EF)
     */
    static double pasoAdelante(List<Actividad> orden) {
        double fin = 0;
        for (Actividad a : orden) {
            double max = 0;
            for (Actividad p : a.pred) max = Math.max(max, p.EF);
            a.ES = max;
            a.EF = a.ES + a.duracion;
            fin = Math.max(fin, a.EF);
        }
        return fin;
    }

    /**
     * Paso hacia atras (se recorre el orden al reves):
     *   LF = min(LS de las sucesoras)      (= duracion del proyecto si no tiene)
     *   LS = LF - duracion
     *   Holgura = LS - ES
     */
    static void pasoAtras(List<Actividad> orden, double duracionProyecto) {
        for (int i = orden.size() - 1; i >= 0; i--) {
            Actividad a = orden.get(i);
            double min = duracionProyecto;
            for (Actividad s : a.suc) min = Math.min(min, s.LS);
            a.LF = min;
            a.LS = a.LF - a.duracion;
            a.holgura = a.LS - a.ES;
            if (Math.abs(a.holgura) < EPS) a.holgura = 0;  // limpia ruido numerico
        }
    }

    /**
     * Encuentra TODAS las rutas criticas con busqueda en profundidad (DFS).
     * Una ruta critica va de una actividad inicial critica a una final critica,
     * pasando solo por actividades criticas consecutivas (EF anterior = ES siguiente).
     */
    static void buscarRutas(Actividad actual, Deque<Actividad> camino,
                            List<List<Actividad>> resultado) {
        camino.addLast(actual);
        boolean avanzo = false;
        for (Actividad s : actual.suc) {
            if (s.esCritica() && Math.abs(s.ES - actual.EF) < EPS) {
                avanzo = true;
                buscarRutas(s, camino, resultado);
            }
        }
        // Si no hay sucesora critica y termina al final del proyecto, es ruta completa
        if (!avanzo) resultado.add(new ArrayList<>(camino));
        camino.removeLast();
    }
}