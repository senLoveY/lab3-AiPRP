package by.bsuir.server;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.SQLException;
import java.util.List;

public class MyServlet extends HttpServlet {
    private DictionaryRepository dictionary;

    @Override public void init() throws ServletException {
        try {
            dictionary = new DictionaryRepository(System.getProperty("dictionary.jdbc.url",
                    "jdbc:h2:file:" + System.getProperty("com.sun.aas.instanceRoot") + "/data/dictionary"));
        } catch (SQLException | IOException e) {
            throw new ServletException("Не удалось открыть базу словаря", e);
        }
    }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException { processRequest(request, response); }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException { processRequest(request, response); }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        boolean plain = "text".equals(request.getParameter("format"));
        response.setContentType(plain ? "text/plain;charset=UTF-8" : "text/html;charset=UTF-8");
        String word = request.getParameter("txt");
        String direction = request.getParameter("direction");
        if (direction == null) direction = "ru-en";
        String message;
        if (word == null || word.isBlank() || word.strip().length() > 100) {
            response.setStatus(400);
            message = "Введите слово длиной от 1 до 100 символов.";
        } else if (!direction.equals("ru-en") && !direction.equals("en-ru")) {
            response.setStatus(400);
            message = "Неизвестное направление перевода.";
        } else {
            try {
                List<String> translations = dictionary.translate(word, direction);
                if (translations.isEmpty()) {
                    response.setStatus(404);
                    message = "Слово не найдено в словаре.";
                } else message = String.join(", ", translations);
            } catch (SQLException e) {
                getServletContext().log("Ошибка поиска перевода", e);
                response.setStatus(500);
                message = "Ошибка базы данных.";
            }
        }
        PrintWriter out = response.getWriter();
        if (plain) out.print(message);
        else {
            out.println("""
                    <!DOCTYPE html><html lang="ru"><head><meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Перевод — Словарь</title><link rel="stylesheet" href="dictionary.css">
                    </head><body><main>
                    <header><h1>Русско-английский словарь</h1></header>
                    <section class="translator" aria-label="Перевод слова">
                    <form name="frm" method="get" action="MyServlet" accept-charset="UTF-8">
                    <label for="direction">Направление</label>
                    <select id="direction" name="direction">
                    """);
            out.println("<option value='ru-en'" + (direction.equals("ru-en") ? " selected" : "") + ">Русский → английский</option>");
            out.println("<option value='en-ru'" + (direction.equals("en-ru") ? " selected" : "") + ">Английский → русский</option></select>");
            out.println("<label for='word'>Слово</label><div class='input-row'><input id='word' name='txt' maxlength='100' required aria-describedby='word-hint' value='"
                    + escape(word == null ? "" : word) + "'><button type='submit'>Перевести</button></div>");
            out.println("<p id='word-hint' class='hint'>Например: кот, книга, мир или cat, book, world.</p></form>");
            if (response.getStatus() == HttpServletResponse.SC_OK) {
                out.println("<div class='result' role='status'><label for='translation'>Перевод</label>"
                        + "<textarea id='translation' name='trans' rows='2' readonly>" + escape(message) + "</textarea></div>");
            } else {
                out.println("<div class='result'><p class='error' role='alert'>" + escape(message) + "</p></div>");
            }
            out.println("""
                    </section>
                    <section class="download" aria-labelledby="presentation-title">
                    <h2 id="presentation-title">Презентация</h2>
                    <a class="button-link" href="PresentationPreviewServlet" target="_blank" rel="noopener">Открыть просмотр в браузере</a>
                    <a href="PresentationServlet">Скачать презентацию (.pptx)</a>
                    <p class="hint">В браузере отображается текст слайдов. Для открытия самого PPTX используйте Java-клиент.</p>
                    </section></main></body></html>
                    """);
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
