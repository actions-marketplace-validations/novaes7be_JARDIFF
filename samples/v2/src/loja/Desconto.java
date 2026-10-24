package loja;

public class Desconto {
    public static double calcular(double subtotal) {
        if (subtotal >= 200) {
            return subtotal * 0.15;
        }
        return 0;
    }
}
