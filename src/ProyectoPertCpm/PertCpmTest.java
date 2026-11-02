package ProyectoPertCpm;

import java.util.*;

/**
 * ============================================================
 *  SOLUCIONADOR GENERAL DE REDES PERT / CPM  -  PARTE 2 
 * ============================================================
 *  Contiene: probabilidad (tabla normal estandar), salida de resultados,
 *  resolucion completa, analisis PERT y el programa principal (main).
 *
 *  Hereda de PertCpm, por eso puede usar directamente Actividad, EPS,
 *  leerReal, leerEntero, pasoAdelante, pasoAtras, etc.
 *
 *  Compilar:  javac PertCpm.java PertCpmTest.java
 *  Ejecutar:  java PertCpmTest
 */
public class PertCpmTest extends PertCpm {

    // ------------------------------------------------------------------
    //  PROBABILIDAD (solo PERT)
    // ------------------------------------------------------------------

    /**
     * Distribucion normal estandar acumulada EXACTA P(Z <= z).
     * Se usa UNICAMENTE para construir la tabla (igual a las de los libros).
     * Serie: Phi(z) = 0.5 + phi(z) * sum( z^(2k+1) / (1*3*5*...*(2k+1)) )
     */
    static double normalAcumulada(double z) {
        if (z < -8) return 0;
        if (z > 8) return 1;
        double termino = z, suma = z;
        for (int k = 1; k < 200; k++) {
            termino *= z * z / (2 * k + 1);
            suma += termino;
            if (Math.abs(termino) < 1e-15) break;
        }
        return 0.5 + suma * Math.exp(-z * z / 2) / Math.sqrt(2 * Math.PI);
    }

    /**
     * TABLA NORMAL ESTANDAR: filas = z de 0.0 a 3.4 (paso 0.1),
     * columnas = segundo decimal 0.00 a 0.09. Cada valor es P(Z <= z)
     * redondeado a 4 decimales, como en la tabla impresa.
     */
    static final double[][] TABLA = construirTabla();

    static double[][] construirTabla() {
        double[][] t = new double[35][10];
        for (int fila = 0; fila < 35; fila++)
            for (int col = 0; col < 10; col++) {
                double z = fila / 10.0 + col / 100.0;
                t[fila][col] = Math.round(normalAcumulada(z) * 10000.0) / 10000.0;
            }
        return t;
    }

    /**
     * Busca P(Z <= z) EN LA TABLA: redondea |z| a 2 decimales, toma la fila
     * (primer decimal) y la columna (segundo decimal). Para z negativo usa
     * la simetria: P(Z <= -z) = 1 - P(Z <= z). Si |z| >= 3.50 devuelve 1 o 0.
     */
    static double probabilidadTabla(double z) {
        int cent = (int) Math.round(Math.abs(z) * 100);
        if (cent > 349) return z >= 0 ? 1.0 : 0.0;
        double p = TABLA[cent / 10][cent % 10];
        return z >= 0 ? p : 1.0 - p;
    }

    /** Busqueda inversa: devuelve el z de la tabla cuyo valor es el mas cercano a prob. */
    static double zDesdeTabla(double prob) {
        double objetivo = prob >= 0.5 ? prob : 1.0 - prob;
        double mejorZ = 0, mejorDif = Double.MAX_VALUE;
        for (int r = 0; r < 35; r++)
            for (int c = 0; c < 10; c++) {
                double dif = Math.abs(TABLA[r][c] - objetivo);
                if (dif < mejorDif - 1e-12) { mejorDif = dif; mejorZ = r / 10.0 + c / 100.0; }
            }
        return prob >= 0.5 ? mejorZ : -mejorZ;
    }

    /** Imprime la tabla normal estandar completa. */
    static void imprimirTablaNormal() {
        System.out.println("\nTABLA NORMAL ESTANDAR  P(Z <= z)");
        System.out.print("  z  ");
        for (int c = 0; c < 10; c++) System.out.printf(Locale.US, "  0.%02d ", c);
        System.out.println();
        for (int r = 0; r < 35; r++) {
            System.out.printf(Locale.US, "%4.1f ", r / 10.0);
            for (int c = 0; c < 10; c++) System.out.printf(Locale.US, " %.4f", TABLA[r][c]);
            System.out.println();
        }
    }

    // ------------------------------------------------------------------
    //  SALIDA DE RESULTADOS
    // ------------------------------------------------------------------

    /** Formatea un numero quitando decimales innecesarios (5.0 -> "5", 5.5 -> "5.50"). */
    static String f(double v) {
        return Math.abs(v - Math.rint(v)) < EPS ? String.valueOf((long) Math.rint(v))
                                                : String.format(Locale.US, "%.2f", v);
    }

    /** Formatea con 2 decimales (varianzas, z, desviaciones). */
    static String fv(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    /** Imprime la tabla: Act, Te (o Dur), IT, ITar, FT, FTar, H, Critica (y Var en PERT). */
    static void imprimirTabla(List<Actividad> orden, boolean modoPert) {
        System.out.println("\n================ TABLA DE RESULTADOS ================");
        System.out.println("IT=Inicio temprano  ITar=Inicio tardio  FT=Fin temprano  FTar=Fin tardio  H=Holgura");
        System.out.println(String.format("%-5s %-6s %-6s %-6s %-6s %-6s %-6s %-8s",
                "Act", modoPert ? "Te" : "Dur", "IT", "ITar", "FT", "FTar", "H", "Critica")
                + (modoPert ? " Var" : ""));
        for (Actividad a : orden) {
            System.out.println(String.format("%-5s %-6s %-6s %-6s %-6s %-6s %-6s %-8s",
                    a.id, f(a.duracion), f(a.ES), f(a.LS), f(a.EF), f(a.LF), f(a.holgura),
                    a.esCritica() ? "SI" : "NO")
                    + (modoPert ? " " + fv(a.varianza) : ""));
        }
    }

    /** PERT: muestra la formula de te y de la varianza con los numeros de cada actividad. */
    static void imprimirDetallePert(Map<String, Actividad> mapa) {
        System.out.println("\n---- Tiempo esperado y varianza por actividad ----");
        for (Actividad a : mapa.values()) {
            System.out.println(a.id + ": te = (" + f(a.a) + " + 4*" + f(a.m) + " + " + f(a.b)
                    + ")/6 = " + f(a.duracion)
                    + "   |   var = ((" + f(a.b) + " - " + f(a.a) + ")/6)^2 = " + fv(a.varianza));
        }
    }

    /** Escribe una ruta como A -> C -> E. */
    static String rutaATexto(List<Actividad> ruta) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ruta.size(); i++) {
            if (i > 0) sb.append(" -> ");
            sb.append(ruta.get(i).id);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    //  RESOLUCION COMPLETA DE UN PROBLEMA
    // ------------------------------------------------------------------
    static void resolver(boolean modoPert) {
        Map<String, Actividad> mapa = leerActividades(modoPert);

        // 1. Validar y construir el grafo
        if (!enlazar(mapa)) return;
        List<Actividad> orden = ordenTopologico(mapa);
        if (orden == null) return;

        if (modoPert) imprimirDetallePert(mapa);

        // 2. Calculo CPM
        double duracionProyecto = pasoAdelante(orden);
        pasoAtras(orden, duracionProyecto);

        // 3. Mostrar tabla
        imprimirTabla(orden, modoPert);
        System.out.println("\nDuracion del proyecto: " + f(duracionProyecto));

        // 4. Rutas criticas: se inicia en actividades criticas sin predecesoras
        List<List<Actividad>> rutas = new ArrayList<>();
        for (Actividad a : orden)
            if (a.pred.isEmpty() && a.esCritica())
                buscarRutas(a, new ArrayDeque<>(), rutas);

        System.out.println("\nRuta(s) critica(s):");
        for (List<Actividad> r : rutas) System.out.println("  " + rutaATexto(r));

        // 5. Analisis probabilistico (solo PERT)
        if (modoPert && !rutas.isEmpty()) analisisPert(duracionProyecto, rutas);
    }

    /**
     * Analisis PERT completo:
     *   Varianza del proyecto = suma de varianzas de la ruta critica
     *   Desviacion estandar   = raiz de la varianza
     *   Z = (T - mu) / sigma  ->  se busca en la TABLA NORMAL ESTANDAR
     */
    static void analisisPert(double mu, List<List<Actividad>> rutas) {
        // Si hay varias rutas criticas se usa la de mayor varianza (criterio conservador)
        List<Actividad> ruta = rutas.get(0);
        double varProy = -1;
        for (List<Actividad> r : rutas) {
            double v = 0;
            for (Actividad a : r) v += a.varianza;
            if (v > varProy + EPS) { varProy = v; ruta = r; }
        }
        double sigma = Math.sqrt(varProy);

        System.out.println("\n================ ANALISIS PERT ================");
        if (rutas.size() > 1)
            System.out.println("(Hay varias rutas criticas: se usa la de mayor varianza)");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ruta.size(); i++) {
            if (i > 0) sb.append(" + ");
            sb.append(fv(ruta.get(i).varianza));
        }
        System.out.println("Ruta usada            : " + rutaATexto(ruta));
        System.out.println("Duracion esperada (mu): " + f(mu));
        System.out.println("Varianza del proyecto = " + sb + " = " + fv(varProy));
        System.out.println("Desviacion estandar   = raiz(" + fv(varProy) + ") = " + fv(sigma));

        boolean seguir = true;
        while (seguir) {
            System.out.println("\n1) Probabilidad de terminar en un plazo T");
            System.out.println("2) Plazo T para una probabilidad dada");
            System.out.println("3) Ver tabla normal estandar");
            System.out.println("4) Terminar analisis");
            int op = leerEntero("Opcion: ", 1);
            switch (op) {
                case 1: {
                    double plazo = leerReal("Plazo T: ", 0);
                    double prob;
                    if (sigma < EPS) {
                        prob = plazo >= mu - EPS ? 1.0 : 0.0;
                        System.out.println("(Desviacion = 0: resultado determinista)");
                    } else {
                        double z = (plazo - mu) / sigma;
                        System.out.println("Z = (T - mu) / sigma = (" + f(plazo) + " - " + f(mu)
                                + ") / " + fv(sigma) + " = " + fv(z));
                        int cent = (int) Math.round(Math.abs(z) * 100);
                        if (cent > 349) {
                            System.out.println("|Z| >= 3.50: fuera de la tabla (probabilidad ~ 1 o 0).");
                        } else {
                            System.out.printf(Locale.US,
                                    "Tabla normal: fila %.1f, columna 0.%02d -> %.4f%n",
                                    (cent / 10) / 10.0, cent % 10, TABLA[cent / 10][cent % 10]);
                            if (z < 0) System.out.println("Z negativo: P = 1 - valor de la tabla");
                        }
                        prob = probabilidadTabla(z);
                    }
                    System.out.println("P(terminar en <= " + f(plazo) + ") = "
                            + String.format(Locale.US, "%.2f%%", prob * 100));
                    System.out.println("P(terminar en >  " + f(plazo) + ") = "
                            + String.format(Locale.US, "%.2f%%", (1 - prob) * 100));
                    break;
                }
                case 2: {
                    double pct = leerReal("Probabilidad deseada en % (0-100, ej. 95): ", 0);
                    if (pct <= 0 || pct >= 100) {
                        System.out.println("Debe estar entre 0 y 100 (sin incluirlos).");
                        break;
                    }
                    double z = zDesdeTabla(pct / 100.0);
                    double plazo = mu + z * sigma;
                    System.out.println("Z buscado en la tabla = " + fv(z));
                    System.out.println("T = mu + Z * sigma = " + f(mu) + " + " + fv(z) + " * "
                            + fv(sigma) + " = " + fv(plazo));
                    break;
                }
                case 3: imprimirTablaNormal(); break;
                case 4: seguir = false; break;
                default: System.out.println("Opcion invalida.");
            }
        }
    }

    // ------------------------------------------------------------------
    //  PROGRAMA PRINCIPAL: menu que se repite hasta que el usuario salga
    // ------------------------------------------------------------------
    public static void main(String[] args) {
        System.out.println("=== SOLUCIONADOR PERT / CPM ===");
        boolean seguir = true;
        while (seguir) {
            System.out.println("\n1) CPM  (duracion unica por actividad)");
            System.out.println("2) PERT (tiempos a, m, b por actividad)");
            System.out.println("3) Salir");
            int op = leerEntero("Opcion: ", 1);
            switch (op) {
                case 1: resolver(false); break;
                case 2: resolver(true);  break;
                case 3: seguir = false;  break;
                default: System.out.println("Opcion invalida.");
            }
        }
        System.out.println("Fin del programa.");
    }
}