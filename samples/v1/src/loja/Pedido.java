package loja;

import java.util.ArrayList;
import java.util.List;

public class Pedido {
    private final List<Double> itens = new ArrayList<>();

    public void adicionar(double preco) {
        itens.add(preco);
    }

    public double subtotal() {
        double total = 0;
        for (double p : itens) {
            total += p;
        }
        return total;
    }

    public double total() {
        double sub = subtotal();
        return sub - Desconto.calcular(sub) + Frete.calcular(sub);
    }

    public String resumo() {
        return "Pedido com " + itens.size() + " itens";
    }
}
