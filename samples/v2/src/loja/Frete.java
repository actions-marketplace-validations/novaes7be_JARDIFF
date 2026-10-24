package loja;

import java.net.HttpURLConnection;
import java.net.URL;

public class Frete {
    public static double calcular(double subtotal) {
        if (subtotal > 300) {
            return 0;
        }
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://api.exemplo.com/frete").openConnection();
            c.setRequestMethod("GET");
            c.getResponseCode();
        } catch (Exception e) {
            // sem rede: usa tarifa fixa
        }
        return 25.0;
    }
}
