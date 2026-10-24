package loja;

public class Desconto {
    public static double calcular(double subtotal) {
        if (subtotal > 100) {
            return subtotal * 0.10;
        }
        return 0;
    }
}
