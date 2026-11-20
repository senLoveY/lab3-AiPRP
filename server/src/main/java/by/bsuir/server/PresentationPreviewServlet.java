package by.bsuir.server;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class PresentationPreviewServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (InputStream file = getClass().getResourceAsStream("/presentations/presentation.pptx")) {
            if (file == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().print("Презентация ещё не добавлена на сервер.");
                return;
            }

            try (XMLSlideShow presentation = new XMLSlideShow(file)) {
                PrintWriter out = response.getWriter();
                out.println("<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"UTF-8\">");
                out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
                out.println("<title>Просмотр презентации</title><link rel=\"stylesheet\" href=\"dictionary.css\">");
                out.println("</head><body><main><header><h1>Текст презентации</h1></header>");
                out.println("<p class=\"hint\">Показан извлечённый текст слайдов. Оформление и изображения PPTX здесь не отображаются.</p>");

                int slideNumber = 0;
                for (var slide : presentation.getSlides()) {
                    slideNumber++;
                    List<String> texts = new ArrayList<>();
                    for (XSLFShape shape : slide.getShapes()) {
                        collectText(shape, texts);
                    }
                    out.println("<section class=\"slide\"><h2>Слайд " + slideNumber + "</h2>");
                    if (texts.isEmpty()) {
                        out.println("<p class=\"hint\">На слайде нет извлекаемого текста. Возможно, содержимое представлено изображением.</p>");
                    } else {
                        for (String text : texts) {
                            out.println("<p>" + escape(text).replace("\r\n", "<br>").replace("\n", "<br>") + "</p>");
                        }
                    }
                    out.println("</section>");
                }

                out.println("<p><a href=\"dictionary.html\">Вернуться к словарю</a> · ");
                out.println("<a href=\"PresentationServlet\">Скачать исходный PPTX</a></p></main></body></html>");
            }
        } catch (Exception e) {
            getServletContext().log("Не удалось прочитать презентацию для HTML-просмотра", e);
            response.reset();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().print("Не удалось подготовить просмотр презентации.");
        }
    }

    private static void collectText(XSLFShape shape, List<String> texts) {
        if (shape instanceof XSLFTextShape textShape) {
            String text = textShape.getText();
            if (text != null && !text.isBlank()) texts.add(text.strip());
        } else if (shape instanceof XSLFTable table) {
            for (var row : table.getRows()) {
                List<String> cells = new ArrayList<>();
                for (var cell : row.getCells()) {
                    String text = cell.getText();
                    if (text != null && !text.isBlank()) cells.add(text.strip());
                }
                if (!cells.isEmpty()) texts.add(String.join(" | ", cells));
            }
        } else if (shape instanceof XSLFGroupShape group) {
            for (XSLFShape child : group.getShapes()) collectText(child, texts);
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
