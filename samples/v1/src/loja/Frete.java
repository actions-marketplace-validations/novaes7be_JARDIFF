package loja;

public class Frete {
    public static double calcular(double subtotal) {
        if (subtotal > 300) {
            return 0;
        }
        return 25.0;
    }
}
