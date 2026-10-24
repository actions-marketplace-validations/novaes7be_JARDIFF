package loja;

import java.util.ArrayList;
import java.util.List;

// v2: variável local renomeada (total -> soma) e linhas deslocadas: mudança só cosmética.
public class Pedido {
    private final List<Double> itens = new ArrayList<>();

    public void adicionar(double preco) {
        itens.add(preco);
    }

    public double subtotal() {
        double soma = 0;
        for (double p : itens) {
            soma += p;
        }
        return soma;
    }

    // v2: Desconto agora é chamado por reflexão.
    public double total() {
        double sub = subtotal();
        double desc;
        try {
            desc = (Double) Class.forName("loja.Desconto")
                    .getMethod("calcular", double.class)
                    .invoke(null, sub);
        } catch (Exception e) {
            desc = 0;
        }
        return sub - desc + Frete.calcular(sub);
    }
    // v2: resumo() foi removido.
}
