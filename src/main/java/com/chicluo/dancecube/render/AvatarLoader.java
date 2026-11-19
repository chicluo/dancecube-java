package com.chicluo.dancecube.render;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 头像加载（对标 Python 版 {@code image.load_avatar}）：下载 → 居中裁剪方形 → 缩放 → 圆形遮罩。
 */
public final class AvatarLoader {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36";

    private AvatarLoader() {
    }

    /** 失败返回 null，调用方回退到首字占位。 */
    public static BufferedImage load(String url, int size) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", UA)
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                return null;
            }
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(resp.body()));
            if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) {
                return null;
            }
            int s = Math.min(src.getWidth(), src.getHeight());
            BufferedImage square = src.getSubimage((src.getWidth() - s) / 2, (src.getHeight() - s) / 2, s, s);

            int big = size * 3;
            BufferedImage scaled = new BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = scaled.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            sg.drawImage(square, 0, 0, big, big, null);
            sg.dispose();

            BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setClip(new Ellipse2D.Double(0, 0, size, size));
            g.drawImage(scaled, 0, 0, size, size, null);
            g.dispose();
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
