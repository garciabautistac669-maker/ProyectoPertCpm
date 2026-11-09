package ProyectoMODI;

import java.util.*;

/**
 * Método MODI (Distribución Modificada / u-v) para el problema de transporte.
 * Minimiza el costo total de enviar oferta -> demanda.
 */
public class Modi {

    public static class Resultado {
        public double[][] x;
        public double costo;
        public double[] u, v;
    }

    // Datos del problema ya balanceado
    private double[] oferta, demanda;
    private double[][] costos;
    private double[][] costosOrig;   // costos reales (antes de convertir maximización)
    private int m, n;

    /** Agrega origen o destino ficticio (costo 0) si no está balanceado. */
    private void balancear(double[] o, double[] d, double[][] c) {
        double so = Arrays.stream(o).sum(), sd = Arrays.stream(d).sum();
        int mm = o.length, nn = d.length;
        if (so > sd) {            // sobra oferta -> destino ficticio
            nn++;
        } else if (so < sd) {     // sobra demanda -> origen ficticio
            mm++;
        }
        oferta = Arrays.copyOf(o, mm);
        demanda = Arrays.copyOf(d, nn);
        if (so > sd) demanda[nn - 1] = so - sd;
        if (so < sd) oferta[mm - 1] = sd - so;
        costos = new double[mm][nn];
        for (int i = 0; i < o.length; i++)
            for (int j = 0; j < d.length; j++)
                costos[i][j] = c[i][j];
        m = mm;
        n = nn;
    }

    private int raiz(int[] padre, int a) {
        while (padre[a] != a) {
            padre[a] = padre[padre[a]];
            a = padre[a];
        }
        return a;
    }

    /** Costo mínimo + completado de base (m+n-1 celdas) para degeneración. */
    private double[][] solucionInicial(boolean[][] base) {
        double[][] x = new double[m][n];
        double[] o = oferta.clone(), d = demanda.clone();

        List<int[]> celdas = new ArrayList<>();
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++)
                celdas.add(new int[]{i, j});
        celdas.sort(Comparator.comparingDouble(c -> costos[c[0]][c[1]]));

        for (int[] c : celdas) {
            double q = Math.min(o[c[0]], d[c[1]]);
            if (q > 0) {
                x[c[0]][c[1]] = q;
                o[c[0]] -= q;
                d[c[1]] -= q;
                base[c[0]][c[1]] = true;
            }
        }

        // Completar la base sin formar ciclos (union-find sobre grafo bipartito)
        int[] padre = new int[m + n];
        for (int k = 0; k < m + n; k++) padre[k] = k;
        int tam = 0;
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++)
                if (base[i][j]) {
                    padre[raiz(padre, i)] = raiz(padre, m + j);
                    tam++;
                }
        for (int[] c : celdas) {
            if (tam >= m + n - 1) break;
            int i = c[0], j = c[1];
            if (!base[i][j] && raiz(padre, i) != raiz(padre, m + j)) {
                base[i][j] = true;          // celda básica con asignación 0
                padre[raiz(padre, i)] = raiz(padre, m + j);
                tam++;
            }
        }
        return x;
    }
    
        /** Resuelve u_i + v_j = c_ij en las celdas básicas (u_0 = 0). */
    private void calcularUV(boolean[][] base, double[] u, double[] v) {
        boolean[] uConocido = new boolean[m], vConocido = new boolean[n];
        u[0] = 0;
        uConocido[0] = true;
        boolean cambio = true;
        while (cambio) {
            cambio = false;
            for (int i = 0; i < m; i++)
                for (int j = 0; j < n; j++) {
                    if (!base[i][j]) continue;
                    if (uConocido[i] && !vConocido[j]) {
                        v[j] = costos[i][j] - u[i];
                        vConocido[j] = true;
                        cambio = true;
                    } else if (vConocido[j] && !uConocido[i]) {
                        u[i] = costos[i][j] - v[j];
                        uConocido[i] = true;
                        cambio = true;
                    }
                }
        }
    }

    /** Ciclo alternando movimientos horizontales/verticales por celdas básicas. */
    private List<int[]> buscarCiclo(boolean[][] base, int[] inicio) {
        List<int[]> camino = new ArrayList<>();
        camino.add(inicio);
        List<int[]> r = dfs(base, inicio, camino, true);
        if (r != null) return r;
        camino = new ArrayList<>();
        camino.add(inicio);
        return dfs(base, inicio, camino, false);
    }

    private List<int[]> dfs(boolean[][] base, int[] inicio, List<int[]> camino, boolean horizontal) {
        int[] ult = camino.get(camino.size() - 1);
        int total = horizontal ? n : m;
        for (int k = 0; k < total; k++) {
            int i = horizontal ? ult[0] : k;
            int j = horizontal ? k : ult[1];
            if (i == ult[0] && j == ult[1]) continue;
            if (i == inicio[0] && j == inicio[1]) {
                if (camino.size() >= 4) return camino;
                continue;
            }
            if (!base[i][j] || contiene(camino, i, j)) continue;
            camino.add(new int[]{i, j});
            List<int[]> res = dfs(base, inicio, camino, !horizontal);
            if (res != null) return res;
            camino.remove(camino.size() - 1);
        }
        return null;
    }

    private boolean contiene(List<int[]> camino, int i, int j) {
        for (int[] c : camino)
            if (c[0] == i && c[1] == j) return true;
        return false;
    }

    private double costoTotal(double[][] x) {
        double s = 0;
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++)
                s += x[i][j] * costosOrig[i][j];
        return s;
    }

    public Resultado resolver(double[] o, double[] d, double[][] c, boolean verbose) {
        return resolver(o, d, c, false, verbose);
    }

    /** Si maximizar = true, se convierte a minimización usando (maxC - c). */
    public Resultado resolver(double[] o, double[] d, double[][] c, boolean maximizar, boolean verbose) {
        balancear(o, d, c);
        costosOrig = new double[m][n];
        for (int i = 0; i < m; i++) costosOrig[i] = costos[i].clone();
        if (maximizar) {
            double max = 0;
            for (double[] fila : costos)
                for (double val : fila) max = Math.max(max, val);
            for (int i = 0; i < m; i++)
                for (int j = 0; j < n; j++)
                    costos[i][j] = max - costos[i][j];
        }
        
        boolean[][] base = new boolean[m][n];
        double[][] x = solucionInicial(base);
        if (verbose) System.out.println("Costo inicial: " + costoTotal(x));

        double[] u = new double[m], v = new double[n];
        int iteracion = 0;
        while (true) {
            calcularUV(base, u, v);

            // Costos reducidos de celdas no básicas: c_ij - u_i - v_j
            double mejor = 0;
            int[] entrante = null;
            for (int i = 0; i < m; i++)
                for (int j = 0; j < n; j++)
                    if (!base[i][j]) {
                        double red = costos[i][j] - u[i] - v[j];
                        if (red < mejor - 1e-9) {
                            mejor = red;
                            entrante = new int[]{i, j};
                        }
                    }
            if (entrante == null) break;   // óptimo

            List<int[]> ciclo = buscarCiclo(base, entrante);
            double theta = Double.MAX_VALUE;
            int[] saliente = null;
            for (int k = 1; k < ciclo.size(); k += 2) {   // celdas con signo -
                int[] cel = ciclo.get(k);
                if (x[cel[0]][cel[1]] < theta) {
                    theta = x[cel[0]][cel[1]];
                    saliente = cel;
                }
            }
            for (int k = 0; k < ciclo.size(); k++) {
                int[] cel = ciclo.get(k);
                x[cel[0]][cel[1]] += (k % 2 == 0) ? theta : -theta;
            }
            base[entrante[0]][entrante[1]] = true;
            base[saliente[0]][saliente[1]] = false;

            iteracion++;
            if (verbose)
                System.out.printf("Iter %d: entra (%d,%d), sale (%d,%d), theta=%.0f, costo=%.0f%n",
                        iteracion, entrante[0], entrante[1], saliente[0], saliente[1],
                        theta, costoTotal(x));
        }

        Resultado r = new Resultado();
        r.x = x;
        r.costo = costoTotal(x);
        r.u = u;
        r.v = v;
        return r;
    }

    // ---------- Entrada de datos por consola ----------

    private static double leerNumero(Scanner sc, String msg, boolean permitirNegativo) {
        while (true) {
            System.out.print(msg);
            if (!sc.hasNext()) {
                System.out.println("\nNo hay más datos de entrada.");
                System.exit(1);
            }
            String t = sc.next().replace(',', '.');
            try {
                double v = Double.parseDouble(t);
                if (!permitirNegativo && v < 0) {
                    System.out.println("  El valor no puede ser negativo.");
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("  Valor inválido, intenta de nuevo.");
            }
        }
    }

    private static int leerEntero(Scanner sc, String msg) {
        while (true) {
            double v = leerNumero(sc, msg, false);
            if (v >= 1 && v == Math.floor(v)) return (int) v;
            System.out.println("  Debe ser un entero mayor o igual a 1.");
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("=== Método MODI - Problema de transporte ===");

        int m = leerEntero(sc, "Número de orígenes (filas): ");
        int n = leerEntero(sc, "Número de destinos (columnas): ");

        double[] oferta = new double[m];
        for (int i = 0; i < m; i++)
            oferta[i] = leerNumero(sc, "Oferta del origen O" + (i + 1) + ": ", false);

        double[] demanda = new double[n];
        for (int j = 0; j < n; j++)
            demanda[j] = leerNumero(sc, "Demanda del destino D" + (j + 1) + ": ", false);

        double[][] costos = new double[m][n];
        System.out.println("Matriz de costos (o utilidades):");
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++)
                costos[i][j] = leerNumero(sc, "  Costo O" + (i + 1) + " -> D" + (j + 1) + ": ", false);

        double tipo = leerNumero(sc, "Tipo de problema (1 = Minimizar costo, 2 = Maximizar utilidad): ", false);
        boolean maximizar = tipo == 2;

        System.out.println();
        Resultado r = new Modi().resolver(oferta, demanda, costos, maximizar, true);

        int filas = r.x.length, cols = r.x[0].length;
        System.out.println("\nAsignación óptima:");
        StringBuilder cab = new StringBuilder(String.format("%-10s", ""));
        for (int j = 0; j < cols; j++)
            cab.append(String.format("%10s", j < n ? "D" + (j + 1) : "Ficticio"));
        System.out.println(cab);
        for (int i = 0; i < filas; i++) {
            StringBuilder fila = new StringBuilder(String.format("%-10s", i < m ? "O" + (i + 1) : "Ficticio"));
            for (int j = 0; j < cols; j++)
                fila.append(String.format("%10.2f", r.x[i][j]));
            System.out.println(fila);
        }
        if (filas > m) System.out.println("(La fila ficticia indica demanda no satisfecha.)");
        if (cols > n) System.out.println("(La columna ficticia indica oferta no utilizada.)");

        System.out.printf("%n%s total óptimo: %.2f%n", maximizar ? "Utilidad" : "Costo", r.costo);
    }
}