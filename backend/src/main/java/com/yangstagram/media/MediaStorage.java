package com.yangstagram.media;

import com.yangstagram.common.error.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드한 사진을 로컬 폴더에 저장한다 (기획서 §10: MVP는 로컬 디스크, 나중에 MinIO).
 * 항상 JPEG로 다시 저장하므로 사진 속 위치 정보(EXIF) 같은 메타데이터는 함께 지워진다.
 */
@Service
public class MediaStorage {

    public static final int MAX_SIDE = 1440;
    public static final int THUMB_WIDTH = 480;

    private final MediaProperties props;

    public MediaStorage(MediaProperties props) {
        this.props = props;
    }

    public StoredImage saveImage(MultipartFile file) {
        BufferedImage source;
        try (InputStream in = file.getInputStream()) {
            source = ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (source == null) {
            throw ApiException.badRequest("UNSUPPORTED_IMAGE", "JPG 또는 PNG 사진만 올릴 수 있습니다.");
        }
        return save(source);
    }

    StoredImage save(BufferedImage source) {
        BufferedImage full = resize(source, MAX_SIDE);
        BufferedImage thumb = resizeToWidth(full, THUMB_WIDTH);

        LocalDate today = LocalDate.now();
        String folder = "%d/%02d".formatted(today.getYear(), today.getMonthValue());
        String name = UUID.randomUUID().toString().replace("-", "");
        Path dir = props.uploadDir().resolve(folder);
        try {
            Files.createDirectories(dir);
            writeJpeg(full, dir.resolve(name + ".jpg"), 0.88f);
            writeJpeg(thumb, dir.resolve(name + "_t.jpg"), 0.82f);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String base = "/media/u/" + folder + "/" + name;
        return new StoredImage(base + ".jpg", base + "_t.jpg", full.getWidth(), full.getHeight());
    }

    private static BufferedImage resize(BufferedImage src, int maxSide) {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, (double) maxSide / Math.max(w, h));
        return draw(src, Math.max(1, (int) Math.round(w * scale)), Math.max(1, (int) Math.round(h * scale)));
    }

    private static BufferedImage resizeToWidth(BufferedImage src, int width) {
        if (src.getWidth() <= width) {
            return src;
        }
        int height = (int) Math.round(src.getHeight() * (double) width / src.getWidth());
        return draw(src, width, height);
    }

    /** RGB로 다시 그린다 (투명 PNG는 흰 배경). */
    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static void writeJpeg(BufferedImage img, Path path, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (OutputStream os = Files.newOutputStream(path);
             ImageOutputStream out = ImageIO.createImageOutputStream(os)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
