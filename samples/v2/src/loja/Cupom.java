package loja;

public class Cupom {
    public static boolean valido(String codigo) {
        return codigo != null && codigo.startsWith("PROMO");
    }
}
