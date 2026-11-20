package by.bsuir.server;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlatinumQuoteService {
    private static final URL PAGE_URL;
    private static final Pattern PRICE_ELEMENT = Pattern.compile(
            "(?is)<span\\b[^>]*\\bdata-testid=[\\\"']qsp-price[\\\"'][^>]*>(.*?)</span>");
    private static final Pattern TIME_ELEMENT = Pattern.compile(
            "(?is)<span\\b[^>]*class=[\\\"'][^\\\"']*marketTimeNotice[^\\\"']*[\\\"'][^>]*>(.*?)</span>");
    private static final SSLSocketFactory TLS_SOCKET_FACTORY = createTrustFactory();

    static {
        try {
            PAGE_URL = URI.create("https://finance.yahoo.com/quote/PL%3DF/").toURL();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private PlatinumQuoteService() { }

    public static Quote fetch() throws IOException {
        URLConnection connection = PAGE_URL.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setRequestProperty("Accept", "text/html");
        if (connection instanceof HttpsURLConnection secureConnection) {
            secureConnection.setSSLSocketFactory(TLS_SOCKET_FACTORY);
        }

        String html;
        try (InputStream input = connection.getInputStream();
             Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            StringBuilder page = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) page.append(buffer, 0, count);
            html = page.toString();
        } finally {
            if (connection instanceof HttpsURLConnection secureConnection) secureConnection.disconnect();
        }

        String priceText = extract(PRICE_ELEMENT, html, "На странице Yahoo Finance не найден элемент с ценой");
        String time = extract(TIME_ELEMENT, html, "На странице Yahoo Finance не найдено время котировки");
        try {
            double price = Double.parseDouble(priceText.replace(",", "").replace(" ", ""));
            return new Quote(price, time);
        } catch (NumberFormatException e) {
            throw new IOException("Не удалось разобрать цену платины на HTML-странице.", e);
        }
    }

    private static String extract(Pattern pattern, String html, String error) throws IOException {
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) throw new IOException(error);
        return plainText(matcher.group(1));
    }

    private static String plainText(String html) {
        return html.replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&#160;", " ")
                .replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("\\s+", " ").strip();
    }

    private static SSLSocketFactory createTrustFactory() {
        try {
            Path trustStorePath = Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream input = Files.newInputStream(trustStorePath)) {
                trustStore.load(input, null);
            }
            TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            managers.init(trustStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, managers.getTrustManagers(), null);
            return context.getSocketFactory();
        } catch (IOException | GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public record Quote(double price, String marketTime) {
        public String formattedPrice() {
            return String.format(Locale.US, "%.2f", price);
        }
    }
}
