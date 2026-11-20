package by.bsuir.client;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ApacheClient {
    static final String URL = System.getProperty("dictionary.url",
            "http://127.0.0.1:8080/lab_servlet/MyServlet");
    private static final Pattern PLATINUM_REPLY = Pattern.compile(
            "Цена фьючерса на платину: ([0-9]+\\.[0-9]{2}) USD за тройскую унцию; (.+) \\(Yahoo Finance\\)\\.");

    public static String translate(String word, String direction) throws Exception {
        var uri = new URIBuilder(URL).setCharset(StandardCharsets.UTF_8)
                .addParameter("txt", word).addParameter("direction", direction)
                .addParameter("format", "text").build();
        var config = RequestConfig.custom().setConnectTimeout(5000)
                .setSocketTimeout(5000).setConnectionRequestTimeout(5000).build();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config).build();
             CloseableHttpResponse response = client.execute(new HttpGet(uri))) {
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (response.getStatusLine().getStatusCode() != 200) {
                int status = response.getStatusLine().getStatusCode();
                if (status == 404) throw new java.io.IOException("Этого слова пока нет в словаре. Попробуйте другое.");
                if (status == 400 && response.getEntity().getContentType() != null
                        && response.getEntity().getContentType().getValue().startsWith("text/plain")) {
                    throw new java.io.IOException(body);
                }
                throw new java.io.IOException("Сервер не смог выполнить перевод (HTTP " + status + "). Попробуйте позже.");
            }
            return body;
        }
    }

    private record PlatinumQuote(BigDecimal price, String marketTime) { }

    private static PlatinumQuote fetchPlatinumPrice() throws java.io.IOException {
        var url = java.net.URI.create(URL).resolve("PlatinumServlet?format=text");
        var config = RequestConfig.custom().setConnectTimeout(5000)
                .setSocketTimeout(15000).setConnectionRequestTimeout(5000).build();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config).build();
             CloseableHttpResponse response = client.execute(new HttpGet(url))) {
            int status = response.getStatusLine().getStatusCode();
            if (status != 200 || response.getEntity() == null) {
                throw new java.io.IOException("Не удалось получить котировку платины. Попробуйте ещё раз.");
            }
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            var quote = PLATINUM_REPLY.matcher(body.strip());
            if (!quote.matches()) {
                throw new java.io.IOException("Сервер вернул котировку в неожиданном формате.");
            }
            return new PlatinumQuote(new BigDecimal(quote.group(1)), quote.group(2));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { /* Use the standard Swing appearance if unavailable. */ }
            showWindow();
        });
    }

    private static void showWindow() {
        JFrame frame = new JFrame("Русско-английский словарь");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel content = createContent();
        frame.setContentPane(content);
        JButton primary = (JButton) content.getClientProperty("translateButton");
        frame.getRootPane().setDefaultButton(primary);
        setPrimaryText(primary);
        frame.setMinimumSize(new Dimension(620, 460));
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        ((JTextField) content.getClientProperty("wordField")).requestFocusInWindow();
    }

    static JPanel createContent() {
        Color muted = UIManager.getColor("Label.foreground");
        Color error = new Color(166, 42, 42);
        JComboBox<String> direction = new JComboBox<>(new String[]{"Русский → английский", "Английский → русский"});
        JTextField word = new JTextField(24);
        word.setFont(word.getFont().deriveFont(16f));
        word.setToolTipText("Введите слово на выбранном языке");
        JButton translate = new JButton("Перевести");
        JTextArea result = new JTextArea(4, 28);
        result.setEditable(false);
        result.setLineWrap(true);
        result.setWrapStyleWord(true);
        result.setFont(word.getFont().deriveFont(18f));
        result.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JScrollPane resultScroll = new JScrollPane(result);
        resultScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        JButton copy = new JButton("Скопировать");
        copy.setEnabled(false);
        copy.addActionListener(event -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(result.getText()), null);
        });
        JTextArea status = note("Например: кот, книга, мир. Для перевода можно нажать Enter.", 2);
        JLabel languageLabel = new JLabel("Направление");
        languageLabel.setLabelFor(direction);
        JPanel languageRow = new JPanel(new BorderLayout(12, 0));
        languageRow.add(languageLabel, BorderLayout.WEST);
        languageRow.add(direction, BorderLayout.CENTER);
        JLabel wordLabel = new JLabel("Слово");
        wordLabel.setLabelFor(word);
        JPanel inputRow = new JPanel(new BorderLayout(10, 0));
        inputRow.add(word, BorderLayout.CENTER);
        inputRow.add(translate, BorderLayout.EAST);
        JPanel input = new JPanel(new BorderLayout(0, 6));
        input.add(wordLabel, BorderLayout.NORTH);
        input.add(inputRow, BorderLayout.CENTER);
        JPanel controls = new JPanel(new BorderLayout(0, 16));
        controls.add(languageRow, BorderLayout.NORTH);
        controls.add(input, BorderLayout.CENTER);
        JLabel resultLabel = new JLabel("Перевод");
        resultLabel.setLabelFor(result);
        JPanel resultHeader = new JPanel(new BorderLayout());
        resultHeader.add(resultLabel, BorderLayout.WEST);
        resultHeader.add(copy, BorderLayout.EAST);
        JPanel output = new JPanel(new BorderLayout(0, 6));
        output.add(resultHeader, BorderLayout.NORTH);
        output.add(resultScroll, BorderLayout.CENTER);
        JPanel dictionary = new JPanel(new BorderLayout(0, 14));
        dictionary.setBorder(BorderFactory.createEmptyBorder(20, 20, 14, 20));
        dictionary.add(controls, BorderLayout.NORTH);
        dictionary.add(output, BorderLayout.CENTER);
        dictionary.add(status, BorderLayout.SOUTH);
        translate.addActionListener(event -> {
            String inputWord = word.getText().strip();
            result.setText("");
            copy.setEnabled(false);
            if (inputWord.isEmpty() || inputWord.length() > 100) {
                status.setForeground(error);
                status.setText("Введите слово длиной от 1 до 100 символов.");
                word.requestFocusInWindow();
                return;
            }
            String selected = direction.getSelectedIndex() == 0 ? "ru-en" : "en-ru";
            translate.setEnabled(false);
            word.setEnabled(false);
            direction.setEnabled(false);
            status.setForeground(muted);
            status.setText("Поиск перевода…");
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() throws Exception { return translate(inputWord, selected); }
                @Override protected void done() {
                    try {
                        result.setText(get());
                        result.setCaretPosition(0);
                        copy.setEnabled(true);
                        status.setText(" ");
                    } catch (Exception e) {
                        status.setForeground(error);
                        status.setText(errorMessage(e));
                    } finally {
                        translate.setEnabled(true);
                        word.setEnabled(true);
                        direction.setEnabled(true);
                        word.requestFocusInWindow();
                        word.selectAll();
                    }
                }
            }.execute();
        });
        word.addActionListener(event -> translate.doClick());

        JButton loadSlides = new JButton("Загрузить презентацию");
        JTextArea slideStatus = note("Слайды будут показаны прямо в этом окне.", 2);
        JLabel slideImage = new JLabel();
        slideImage.setHorizontalAlignment(SwingConstants.CENTER);
        JScrollPane slideScroll = new JScrollPane(slideImage);
        JButton previousSlide = new JButton("← Назад");
        JButton nextSlide = new JButton("Вперёд →");
        JLabel slideCounter = new JLabel("Слайд 0 из 0");
        previousSlide.setEnabled(false);
        nextSlide.setEnabled(false);
        JPanel slideNavigation = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 5));
        slideNavigation.add(previousSlide);
        slideNavigation.add(slideCounter);
        slideNavigation.add(nextSlide);
        JPanel slideHeader = new JPanel(new BorderLayout(12, 0));
        slideHeader.add(loadSlides, BorderLayout.WEST);
        slideHeader.add(slideStatus, BorderLayout.CENTER);
        JPanel presentation = new JPanel(new BorderLayout(0, 10));
        presentation.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        presentation.add(slideHeader, BorderLayout.NORTH);
        presentation.add(slideScroll, BorderLayout.CENTER);
        presentation.add(slideNavigation, BorderLayout.SOUTH);

        List<BufferedImage> renderedSlides = new ArrayList<>();
        int[] selectedSlide = {0};
        Runnable showSelectedSlide = () -> {
            if (renderedSlides.isEmpty()) return;
            slideImage.setIcon(new ImageIcon(renderedSlides.get(selectedSlide[0])));
            slideCounter.setText("Слайд " + (selectedSlide[0] + 1) + " из " + renderedSlides.size());
            previousSlide.setEnabled(selectedSlide[0] > 0);
            nextSlide.setEnabled(selectedSlide[0] < renderedSlides.size() - 1);
        };
        previousSlide.addActionListener(event -> {
            if (selectedSlide[0] > 0) {
                selectedSlide[0]--;
                showSelectedSlide.run();
            }
        });
        nextSlide.addActionListener(event -> {
            if (selectedSlide[0] < renderedSlides.size() - 1) {
                selectedSlide[0]++;
                showSelectedSlide.run();
            }
        });
        loadSlides.addActionListener(event -> {
            loadSlides.setEnabled(false);
            slideStatus.setForeground(muted);
            slideStatus.setText("Загрузка и подготовка слайдов…");
            new SwingWorker<List<BufferedImage>, Void>() {
                @Override protected List<BufferedImage> doInBackground() throws Exception {
                    return PresentationClient.downloadAndRender();
                }
                @Override protected void done() {
                    try {
                        renderedSlides.clear();
                        renderedSlides.addAll(get());
                        selectedSlide[0] = 0;
                        showSelectedSlide.run();
                        slideStatus.setText("Презентация загружена.");
                    } catch (Exception e) {
                        slideStatus.setForeground(error);
                        slideStatus.setText(errorMessage(e));
                    } finally {
                        loadSlides.setEnabled(true);
                    }
                }
            }.execute();
        });

        JButton updatePrice = new JButton("Обновить цену");
        JPanel quoteContent = createQuoteContent(updatePrice);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Словарь", dictionary);
        tabs.addTab("Презентация", presentation);
        tabs.addTab("Платина", quoteContent);
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        content.add(tabs, BorderLayout.CENTER);
        content.putClientProperty("translateButton", translate);
        content.putClientProperty("wordField", word);
        tabs.addChangeListener(event -> {
            JRootPane root = SwingUtilities.getRootPane(tabs);
            if (root != null) {
                JButton primary = switch (tabs.getSelectedIndex()) {
                    case 0 -> translate;
                    case 1 -> loadSlides;
                    default -> updatePrice;
                };
                root.setDefaultButton(primary);
                setPrimaryText(primary);
            }
        });
        return content;
    }

    private static JPanel createQuoteContent(JButton updatePrice) {
        JLabel title = new JLabel("Фьючерс на платину (PL=F)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        JLabel platinumPrice = new JLabel("—");
        platinumPrice.setFont(platinumPrice.getFont().deriveFont(Font.BOLD, 44f));
        JLabel unit = new JLabel("USD за тройскую унцию");
        JTextArea marketTime = note("Время котировки появится после загрузки", 2);
        JTextArea quoteStatus = note("Нажмите «Обновить цену», чтобы загрузить котировку.", 2);
        JLabel source = new JLabel("Yahoo Finance · возможна задержка данных");

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(UIManager.getColor("TextField.background"));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(24, 28, 16, 28)));
        GridBagConstraints row = new GridBagConstraints();
        row.gridx = 0;
        row.weightx = 1;
        row.anchor = GridBagConstraints.LINE_START;
        row.fill = GridBagConstraints.HORIZONTAL;
        row.insets = new Insets(0, 0, 8, 0);
        card.add(title, row);
        card.add(platinumPrice, row);
        card.add(unit, row);
        card.add(new JSeparator(), row);
        card.add(marketTime, row);
        card.add(source, row);
        row.fill = GridBagConstraints.NONE;
        row.insets = new Insets(8, 0, 8, 0);
        card.add(updatePrice, row);
        row.fill = GridBagConstraints.HORIZONTAL;
        row.insets = new Insets(0, 0, 0, 0);
        card.add(quoteStatus, row);

        JPanel quoteContent = new JPanel(new GridBagLayout());
        quoteContent.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        quoteContent.add(card);

        NumberFormat priceFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));
        priceFormat.setMinimumFractionDigits(2);
        priceFormat.setMaximumFractionDigits(2);
        updatePrice.addActionListener(event -> {
            updatePrice.setEnabled(false);
            quoteStatus.setText("Загрузка котировки…");
            new SwingWorker<PlatinumQuote, Void>() {
                @Override protected PlatinumQuote doInBackground() throws Exception {
                    return fetchPlatinumPrice();
                }
                @Override protected void done() {
                    try {
                        PlatinumQuote quote = get();
                        platinumPrice.setText(priceFormat.format(quote.price()));
                        marketTime.setText(quote.marketTime());
                        quoteStatus.setText("Котировка загружена.");
                    } catch (Exception e) {
                        quoteStatus.setText(errorMessage(e));
                    } finally {
                        updatePrice.setEnabled(true);
                    }
                }
            }.execute();
        });

        return quoteContent;
    }

    private static void setPrimaryText(JButton button) {
        // Aqua uses the system accent colour for the default button.
        if ("Aqua".equals(UIManager.getLookAndFeel().getID())) {
            button.setForeground(Color.WHITE);
        }
    }

    private static JTextArea note(String text, int rows) {
        JTextArea area = new JTextArea(text, rows, 32);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(UIManager.getFont("Label.font"));
        area.setForeground(UIManager.getColor("Label.foreground"));
        // Reserve the status lines before the wrapping text has a measured width.
        int lineHeight = area.getFontMetrics(area.getFont()).getHeight();
        area.setPreferredSize(new Dimension(360, rows * lineHeight + 4));
        return area;
    }

    private static String errorMessage(Exception error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        if (cause instanceof java.net.ConnectException || cause instanceof java.net.UnknownHostException) {
            return "Не удалось подключиться к серверу. Проверьте, что GlassFish запущен и приложение развёрнуто.";
        }
        if (cause instanceof java.net.SocketTimeoutException) {
            return "Сервер долго не отвечает. Попробуйте ещё раз.";
        }
        return cause.getMessage() == null ? "Не удалось выполнить запрос. Попробуйте ещё раз." : cause.getMessage();
    }
}
