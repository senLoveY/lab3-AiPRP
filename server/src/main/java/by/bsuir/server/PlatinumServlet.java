package by.bsuir.server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class PlatinumServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        boolean plain = "text".equals(request.getParameter("format"));
        response.setContentType(plain ? "text/plain;charset=UTF-8" : "text/html;charset=UTF-8");

        try {
            PlatinumQuoteService.Quote quote = PlatinumQuoteService.fetch();
            String price = quote.formattedPrice() + " USD за тройскую унцию";
            String time = quote.marketTime();
            if (plain) {
                response.getWriter().printf("Цена фьючерса на платину: %s; %s (Yahoo Finance).", price, time);
                return;
            }

            response.getWriter().println("""
                    <!DOCTYPE html><html lang="ru"><head><meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Цена платины</title><link rel="stylesheet" href="dictionary.css">
                    </head><body><main><header><h1>Цена платины</h1></header>
                    <section class="quote-card"><p class="quote-label">Фьючерс на платину</p>
                    <p class="quote-price">%s</p><p class="hint">%s · Источник: Yahoo Finance</p>
                    <p class="hint">Рыночная цена фьючерса; значение может быть задержано источником.</p>
                    <p class="quote-actions"><a class="button-link" href="PlatinumServlet">Обновить цену</a>
                    <a href="dictionary.html">Вернуться к словарю</a></p></section>
                    </main></body></html>
                    """.formatted(escape(price), escape(time)));
        } catch (Exception e) {
            getServletContext().log("Не удалось получить цену фьючерса на платину", e);
            writeError(response, plain, "Не удалось получить цену платины. Попробуйте обновить страницу позже.");
        }
    }

    private static void writeError(HttpServletResponse response, boolean plain, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
        if (plain) {
            response.getWriter().print(message);
            return;
        }
        response.getWriter().println("<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"UTF-8\">");
        response.getWriter().println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        response.getWriter().println("<title>Цена платины</title><link rel=\"stylesheet\" href=\"dictionary.css\"></head><body><main>");
        response.getWriter().println("<header><h1>Цена платины</h1></header><section class=\"quote-card\"><p class=\"error\">"
                + escape(message) + "</p><p><a class=\"button-link\" href=\"PlatinumServlet\">Попробовать снова</a> "
                + "<a href=\"dictionary.html\">Вернуться к словарю</a></p></section></main></body></html>");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
