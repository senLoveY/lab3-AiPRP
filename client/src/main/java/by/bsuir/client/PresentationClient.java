package by.bsuir.client;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PresentationClient {
    private static final String MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    private PresentationClient() { }

    public static Path download() throws IOException {
        URI url = URI.create(System.getProperty("presentation.url",
                URI.create(ApacheClient.URL).resolve("PresentationServlet").toString()));
        RequestConfig config = RequestConfig.custom().setConnectTimeout(5000)
                .setSocketTimeout(30000).setConnectionRequestTimeout(5000).build();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config).build();
             CloseableHttpResponse response = client.execute(new HttpGet(url))) {
            int status = response.getStatusLine().getStatusCode();
            if (status != 200) {
                if (status == 404) {
                    throw new IOException("Презентация не найдена. Добавьте presentation.pptx на сервер "
                            + "и выполните package и Redeploy. Адрес: " + url);
                }
                throw new IOException("Не удалось скачать презентацию: HTTP " + status);
            }
            if (response.getEntity() == null
                    || !MIME.equalsIgnoreCase(ContentType.getOrDefault(response.getEntity()).getMimeType())) {
                throw new IOException("Сервер вернул ответ, который не является презентацией PowerPoint.");
            }
            Path directory = Files.createTempDirectory("lab3-presentation-");
            Path file = directory.resolve("presentation.pptx");
            try (var stream = response.getEntity().getContent()) {
                Files.copy(stream, file);
                if (Files.size(file) == 0) throw new IOException("Сервер вернул пустую презентацию.");
                return file;
            } catch (IOException e) {
                Files.deleteIfExists(file);
                Files.deleteIfExists(directory);
                throw e;
            }
        }
    }

    public static List<BufferedImage> downloadAndRender() throws IOException {
        Path file = download();
        List<BufferedImage> images = new ArrayList<>();
        try (FileInputStream input = new FileInputStream(file.toFile());
             XMLSlideShow presentation = new XMLSlideShow(input)) {
            Dimension pageSize = presentation.getPageSize();
            List<XSLFSlide> slides = presentation.getSlides();
            if (slides.isEmpty()) throw new IOException("В презентации нет слайдов.");

            double scale = Math.min(1.0, 900.0 / pageSize.width);
            int width = Math.max(1, (int) (pageSize.width * scale));
            int height = Math.max(1, (int) (pageSize.height * scale));
            for (XSLFSlide slide : slides) {
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, width, height);
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    graphics.scale(scale, scale);
                    slide.draw(graphics);
                } finally {
                    graphics.dispose();
                }
                images.add(image);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Не удалось отобразить слайды презентации.", e);
        }
        return images;
    }
}
