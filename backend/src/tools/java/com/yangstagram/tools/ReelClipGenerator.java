package com.yangstagram.tools;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.imageio.ImageIO;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Rational;

/**
 * 가짜 릴스용 짧은 세로 영상 만들기 (기획서 §8.3 "텍스트·색상 클립").
 * 이미 받은 시드 사진에 천천히 확대·이동하는 효과(켄 번스)와 글자를 얹어 MP4(H.264)로 저장한다.
 * ffmpeg 없이 순수 Java(JCodec)로 만들기 때문에 별도 설치가 필요 없다.
 *
 * <pre>
 *   gradlew reelClips              # 기본 48개
 *   gradlew reelClips --args="80"  # 개수 지정
 * </pre>
 * 결과: seed-media/reels/clip-NN.mp4 + clip-NN.jpg(첫 화면) + manifest.json (Git 제외)
 */
public final class ReelClipGenerator {

    private static final int W = 360;
    private static final int H = 640;
    private static final int FPS = 15;

    private static final String[] LINES = {
            // Java2D(맑은 고딕)는 컬러 이모지를 그리지 못하므로 글자만 쓴다
            "오늘의 기록", "주말 브이로그", "산책 중", "여기 진짜 좋다", "하루 요약", "OOTD",
            "카페 투어", "운동 끝!", "노을 맛집", "다음엔 같이 가요", "1분 레시피", "작업실 일상",
            "비 오는 날", "여행 3일 차", "새로 산 것", "고양이 근황", "출근길", "밤 산책",
    };

    public static void main(String[] args) throws IOException {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 48;
        Path root = Path.of(args.length > 1 ? args[1] : "../seed-media").toAbsolutePath().normalize();
        Path photos = root.resolve("photos");
        Path out = root.resolve("reels");
        Files.createDirectories(out);

        List<Path> sources = new ArrayList<>();
        try (var stream = Files.list(photos)) {
            stream.filter(p -> p.getFileName().toString().matches("\\d+\\.jpg")).sorted().forEach(sources::add);
        }
        if (sources.isEmpty()) {
            System.err.println("사진이 없습니다. 먼저 node seed-media/download.mjs 를 실행하세요.");
            System.exit(1);
        }

        Random rnd = new Random(42);
        StringBuilder manifest = new StringBuilder("[\n");
        long started = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            String name = String.format("clip-%02d", i);
            Path mp4 = out.resolve(name + ".mp4");
            Path poster = out.resolve(name + ".jpg");
            int seconds = 5 + rnd.nextInt(8); // 5~12초
            String line = LINES[rnd.nextInt(LINES.length)];
            long clipSeed = rnd.nextLong(); // 이미 있는 파일을 건너뛰어도 순서가 같도록 항상 뽑는다
            if (!Files.exists(mp4)) {
                BufferedImage src = ImageIO.read(sources.get((i * 7) % sources.size()).toFile());
                render(src, line, seconds, clipSeed, mp4.toFile(), poster.toFile());
            }
            manifest.append(String.format(Locale.ROOT,
                    "  {\"file\": \"%s.mp4\", \"poster\": \"%s.jpg\", \"width\": %d, \"height\": %d, \"durationMs\": %d}%s%n",
                    name, name, W, H, seconds * 1000, i < count - 1 ? "," : ""));
            System.out.printf("%s (%d초) 완료%n", name, seconds);
        }
        manifest.append("]\n");
        Files.writeString(out.resolve("manifest.json"), manifest.toString());
        System.out.printf("영상 %d개, %.1f초%n", count, (System.currentTimeMillis() - started) / 1000.0);
    }

    private static void render(BufferedImage src, String line, int seconds, long seed, File mp4, File poster)
            throws IOException {
        Random rnd = new Random(seed);
        int frames = seconds * FPS;
        // 시작·끝 확대 비율과 위치를 무작위로 (켄 번스)
        double z0 = 1.0 + rnd.nextDouble() * 0.1;
        double z1 = 1.15 + rnd.nextDouble() * 0.2;
        double x0 = rnd.nextDouble(), x1 = rnd.nextDouble();
        double y0 = rnd.nextDouble(), y1 = rnd.nextDouble();
        Color tint = Color.getHSBColor(rnd.nextFloat(), 0.5f, 0.4f);

        try (SeekableByteChannel ch = NIOUtils.writableChannel(mp4)) {
            AWTSequenceEncoder enc = new AWTSequenceEncoder(ch, Rational.R(FPS, 1));
            BufferedImage frame = new BufferedImage(W, H, BufferedImage.TYPE_3BYTE_BGR);
            for (int f = 0; f < frames; f++) {
                double t = (double) f / (frames - 1);
                double ease = t * t * (3 - 2 * t);
                draw(frame, src, lerp(z0, z1, ease), lerp(x0, x1, ease), lerp(y0, y1, ease), tint, line, t);
                enc.encodeImage(frame);
                if (f == Math.min(frames - 1, FPS)) ImageIO.write(frame, "jpg", poster);
            }
            enc.finish();
        }
    }

    private static void draw(BufferedImage frame, BufferedImage src, double zoom, double px, double py, Color tint,
                             String line, double t) {
        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double scale = Math.max((double) W / src.getWidth(), (double) H / src.getHeight()) * zoom;
        int dw = (int) (src.getWidth() * scale);
        int dh = (int) (src.getHeight() * scale);
        int x = (int) (-(dw - W) * px);
        int y = (int) (-(dh - H) * py);
        g.drawImage(src, x, y, dw, dh, null);

        // 아래쪽 어둡게 + 약한 색감
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
        g.setColor(tint);
        g.fillRect(0, 0, W, H);
        g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new GradientPaint(0, H * 0.55f, new Color(0, 0, 0, 0), 0, H, new Color(0, 0, 0, 150)));
        g.fillRect(0, (int) (H * 0.55), W, H);

        // 글자: 0.3초 뒤부터 서서히 나타남
        float alpha = (float) Math.max(0, Math.min(1, (t * 10) - 0.5));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setColor(Color.WHITE);
        g.setFont(new Font("Malgun Gothic", Font.BOLD, 30));
        int tw = g.getFontMetrics().stringWidth(line);
        g.drawString(line, (W - tw) / 2, (int) (H * 0.5));
        g.setComposite(AlphaComposite.SrcOver);
        g.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        g.setColor(new Color(255, 255, 255, 170));
        g.drawString("Yangstagram 연구용 가짜 영상", 12, H - 14);
        g.dispose();
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
