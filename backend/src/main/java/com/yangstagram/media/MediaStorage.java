package com.yangstagram.media;

import com.yangstagram.common.error.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
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
 * 업로드한 사진을 로컬 폴더에 저장한다 (기획서 §10: MVP는 로컬 디스크).
 * 배포에서는 yangstagram.media.remote를 주면 Supabase Storage에 올린다 (RemoteMedia).
 * 항상 JPEG로 다시 저장하므로 사진 속 위치 정보(EXIF) 같은 메타데이터는 함께 지워진다.
 */
@Service
public class MediaStorage {

    public static final int MAX_SIDE = 1440;
    public static final int THUMB_WIDTH = 480;

    private final MediaProperties props;
    private final RemoteMedia remote;

    public MediaStorage(MediaProperties props, RemoteMedia remote) {
        this.props = props;
        this.remote = remote;
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

    /** 영상 저장 결과 (재생 주소) */
    public record StoredVideo(String url) {
    }

    /**
     * 영상(릴스)은 변환하지 않고 그대로 저장한다 (브라우저가 재생할 수 있는 MP4·WebM만).
     * 첫 화면 사진(poster)은 화면에서 잘라 saveImage로 따로 보낸다.
     */
    public StoredVideo saveVideo(MultipartFile file) {
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String ext = switch (type) {
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";
            default -> throw ApiException.badRequest("UNSUPPORTED_VIDEO", "MP4 또는 WebM 영상만 올릴 수 있습니다.");
        };
        return new StoredVideo(putFile(file, ext, type));
    }

    public static final long MAX_AUDIO_BYTES = 3L * 1024 * 1024;

    /** 음성 메시지: 브라우저가 녹음한 그대로 저장 (WebM·Ogg·MP4 오디오) */
    public StoredVideo saveAudio(MultipartFile file) {
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        int semi = type.indexOf(';');
        if (semi >= 0) type = type.substring(0, semi).strip();
        String ext = switch (type) {
            case "audio/webm", "video/webm" -> ".webm";
            case "audio/ogg" -> ".ogg";
            case "audio/mp4", "audio/m4a", "audio/x-m4a" -> ".m4a";
            case "audio/mpeg" -> ".mp3";
            default -> throw ApiException.badRequest("UNSUPPORTED_AUDIO", "녹음 파일 형식을 알 수 없습니다.");
        };
        if (file.getSize() == 0 || file.getSize() > MAX_AUDIO_BYTES) {
            throw ApiException.badRequest("BAD_AUDIO", "녹음 파일이 비어 있거나 너무 큽니다.");
        }
        return new StoredVideo(putFile(file, ext, type));
    }

    StoredImage save(BufferedImage source) {
        BufferedImage full = resize(source, MAX_SIDE);
        BufferedImage thumb = resizeToWidth(full, THUMB_WIDTH);
        String key = newKey();
        try {
            put(key + ".jpg", jpeg(full, 0.88f), "image/jpeg");
            put(key + "_t.jpg", jpeg(thumb, 0.82f), "image/jpeg");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String base = "/media/u/" + key;
        return new StoredImage(base + ".jpg", base + "_t.jpg", full.getWidth(), full.getHeight());
    }

    /** 업로드 파일의 저장 위치: 연/월/무작위 이름 (확장자 제외, uploads/ 또는 버킷의 u/ 아래) */
    private static String newKey() {
        LocalDate today = LocalDate.now();
        return "%d/%02d/%s".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID().toString().replace("-", ""));
    }

    /** 영상·음성: 메모리에 다 올리지 않고 파일째로 저장 → 주소 */
    private String putFile(MultipartFile file, String ext, String contentType) {
        String key = newKey() + ext;
        try {
            if (props.remote().enabled()) {
                Path tmp = Files.createTempFile("upload-", ext);
                try {
                    file.transferTo(tmp);
                    remote.upload("u/" + key, HttpRequest.BodyPublishers.ofFile(tmp), contentType);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            } else {
                Path target = props.uploadDir().resolve(key);
                Files.createDirectories(target.getParent());
                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, target);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return "/media/u/" + key;
    }

    private void put(String key, byte[] data, String contentType) throws IOException {
        if (props.remote().enabled()) {
            remote.upload("u/" + key, HttpRequest.BodyPublishers.ofByteArray(data), contentType);
        } else {
            Path target = props.uploadDir().resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        }
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

    private static byte[] jpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(os)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return os.toByteArray();
    }
}
