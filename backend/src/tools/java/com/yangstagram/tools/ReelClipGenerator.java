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
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Rational;

/**
 * 가짜 릴스용 짧은 세로 영상 만들기 (기획서 §8.3 "텍스트·색상 클립").
 * 받아 둔 시드 사진(photos/manifest.json의 세로 사진)에 천천히 확대·이동하는 효과(켄 번스)와
 * 주제에 맞는 글자를 얹어 MP4(H.264)로 저장한다. manifest에 주제·대상을 남겨서 시드가 관심사에 맞는 릴스를 고른다.
 * ffmpeg 없이 순수 Java(JCodec)로 만들기 때문에 별도 설치가 필요 없다.
 *
 * <pre>
 *   gradlew reelClips              # 기본 100개 (주제마다 5개)
 *   gradlew reelClips --args="160" # 개수 지정
 * </pre>
 * 결과: seed-media/reels/reel-NNN.mp4 + reel-NNN.jpg(첫 화면) + manifest.json (Git 제외)
 */
public final class ReelClipGenerator {

    private static final int W = 360;
    private static final int H = 640;
    private static final int FPS = 15;

    /** 주제별 영상 위 글자 (Java2D·맑은 고딕은 컬러 이모지를 못 그리므로 글자만) */
    private static final Map<String, String[]> LINES = Map.ofEntries(
            Map.entry("FOOD", new String[] {"웨이팅 1시간 맛집", "이 가격 실화?", "먹방 브이로그", "재방문 100%"}),
            Map.entry("CAFE", new String[] {"성수 신상 카페", "카페 투어 3곳", "디저트 맛집", "라떼 아트 도전"}),
            Map.entry("TRAVEL", new String[] {"여행 3일 차", "여기 꼭 가세요", "숙소 뷰 실화", "공항 가는 길"}),
            Map.entry("FASHION", new String[] {"OOTD", "일주일 출근룩", "하울 언박싱", "가을 코디 3가지"}),
            Map.entry("FITNESS", new String[] {"오운완", "5분 홈트", "아침 러닝 기록", "하체 루틴"}),
            Map.entry("PETS", new String[] {"댕냥이 근황", "산책 가자!", "간식 앞 집중력", "우리 집 막내"}),
            Map.entry("PHOTO", new String[] {"노을 맛집", "필름 카메라 일상", "밤 산책", "사진 잘 찍는 법"}),
            Map.entry("MUSIC", new String[] {"요즘 플레이리스트", "공연 다녀옴", "LP 언박싱", "기타 연습 30일"}),
            Map.entry("DEV", new String[] {"개발자의 하루", "데스크 셋업", "배포 성공", "재택 브이로그"}),
            Map.entry("PARENTING", new String[] {"육아 브이로그", "첫걸음 성공", "주말 나들이", "아기 웃음 모음"}),
            Map.entry("BEAUTY", new String[] {"GRWM", "데일리 메이크업", "드럭스토어 하울", "피부 루틴"}),
            Map.entry("BOOKS", new String[] {"이번 달 읽은 책", "독립서점 투어", "밑줄 문장", "북 언박싱"}),
            Map.entry("ART", new String[] {"드로잉 타임랩스", "작업실 일상", "오늘의 그림", "전시 다녀옴"}),
            Map.entry("NATURE", new String[] {"등산 브이로그", "초록 충전", "정상 도착!", "숲길 산책"}),
            Map.entry("SPORTS", new String[] {"직관 브이로그", "역전골 순간", "주말 풋살", "서핑 첫 도전"}),
            Map.entry("GAMING", new String[] {"엔딩 봤다", "게임방 셋업", "한 판만 더", "레트로 오락실"}),
            Map.entry("MOVIE", new String[] {"영화관 데이트", "인생 영화 추천", "팝콘은 반반", "심야 영화"}),
            Map.entry("COOKING", new String[] {"1분 레시피", "자취 요리", "빵 굽는 주말", "냉장고 파먹기"}),
            Map.entry("INTERIOR", new String[] {"랜선 집들이", "방 꾸미기", "원룸 인테리어", "조명 바꿨어요"}),
            Map.entry("CAR", new String[] {"주말 드라이브", "세차 브이로그", "야간 드라이브", "로드트립"}));

    /** manifest.json의 사진 한 장 (download.mjs 형식, 라이브러리 없이 읽는다) */
    private record Source(Path file, String topic, String label) {
    }

    private static final Pattern OBJECT = Pattern.compile("\\{[^}]*\\}");

    private static List<Source> readManifest(Path photos) throws IOException {
        String text = Files.readString(photos.resolve("manifest.json"));
        List<Source> list = new ArrayList<>();
        Matcher m = OBJECT.matcher(text);
        while (m.find()) {
            String o = m.group();
            if (!"4:5".equals(field(o, "ratio"))) continue; // 세로 사진만
            list.add(new Source(photos.resolve(field(o, "file")), field(o, "topic"), field(o, "label")));
        }
        return list;
    }

    private static String field(String obj, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\\s*\"([^\"]*)\"").matcher(obj);
        return m.find() ? m.group(1) : null;
    }

    public static void main(String[] args) throws IOException {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        Path root = Path.of(args.length > 1 ? args[1] : "../seed-media").toAbsolutePath().normalize();
        Path photos = root.resolve("photos");
        Path out = root.resolve("reels");
        Files.createDirectories(out);

        // 예전 방식 영상(clip-NN) 정리
        try (var stream = Files.list(out)) {
            for (Path p : stream.filter(p -> p.getFileName().toString().startsWith("clip-")).toList()) {
                Files.delete(p);
            }
        }
        List<Source> all = Files.exists(photos.resolve("manifest.json")) ? readManifest(photos) : List.of();
        // 주제를 돌아가며 고른다 (주제마다 고르게)
        Map<String, List<Source>> byTopic = new TreeMap<>();
        for (Source s : all) byTopic.computeIfAbsent(s.topic(), k -> new ArrayList<>()).add(s);
        List<Source> sources = new ArrayList<>();
        for (int round = 0; ; round++) {
            boolean added = false;
            for (List<Source> l : byTopic.values()) {
                if (round < l.size()) {
                    sources.add(l.get((round * 7) % l.size()));
                    added = true;
                }
            }
            if (!added) break;
        }
        if (sources.isEmpty()) {
            System.err.println("사진이 없습니다. 먼저 node seed-media/download.mjs 를 실행하세요.");
            System.exit(1);
        }

        Random rnd = new Random(42);
        StringBuilder manifest = new StringBuilder("[\n");
        long started = System.currentTimeMillis();
        int skip = 0;
        for (int i = 0; i < count; i++) {
            String name = String.format("reel-%03d", i);
            Source src = sources.get((i + skip) % sources.size());
            // ImageIO가 못 읽는 파일(webp 등)은 건너뛴다
            while (!Files.exists(out.resolve(name + ".mp4")) && ImageIO.read(src.file().toFile()) == null
                    && skip < sources.size()) {
                skip++;
                src = sources.get((i + skip) % sources.size());
            }
            String[] lines = LINES.getOrDefault(src.topic(), new String[] {"오늘의 기록"});
            Path mp4 = out.resolve(name + ".mp4");
            Path poster = out.resolve(name + ".jpg");
            int seconds = 5 + rnd.nextInt(8); // 5~12초
            String line = lines[rnd.nextInt(lines.length)];
            long clipSeed = rnd.nextLong(); // 이미 있는 파일을 건너뛰어도 순서가 같도록 항상 뽑는다
            if (!Files.exists(mp4)) {
                BufferedImage img = ImageIO.read(src.file().toFile());
                render(img, line, seconds, clipSeed, mp4.toFile(), poster.toFile());
            }
            manifest.append(String.format(Locale.ROOT,
                    "  {\"file\": \"%s.mp4\", \"poster\": \"%s.jpg\", \"width\": %d, \"height\": %d, \"durationMs\": %d, "
                            + "\"topic\": \"%s\", \"label\": \"%s\", \"line\": \"%s\"}%s%n",
                    name, name, W, H, seconds * 1000, src.topic(), src.label(), line, i < count - 1 ? "," : ""));
            System.out.printf("%s %s (%d초) 완료%n", name, src.topic(), seconds);
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

        // 약한 색감 + 아래쪽 어둡게
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
        g.setColor(tint);
        g.fillRect(0, 0, W, H);
        g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new GradientPaint(0, H * 0.55f, new Color(0, 0, 0, 0), 0, H, new Color(0, 0, 0, 150)));
        g.fillRect(0, (int) (H * 0.55), W, H);

        // 글자: 릴스 자막처럼 반투명 상자 위에, 0.3초 뒤부터 서서히
        float alpha = (float) Math.max(0, Math.min(1, (t * 10) - 0.5));
        g.setFont(new Font("Malgun Gothic", Font.BOLD, 26));
        int tw = g.getFontMetrics().stringWidth(line);
        int bx = (W - tw) / 2 - 12;
        int by = (int) (H * 0.42);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.85f));
        g.setColor(Color.WHITE);
        g.fillRoundRect(bx, by, tw + 24, 44, 14, 14);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setColor(new Color(20, 20, 20));
        g.drawString(line, bx + 12, by + 31);
        g.setComposite(AlphaComposite.SrcOver);
        g.setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
        g.setColor(new Color(255, 255, 255, 150));
        g.drawString("Yangstagram 연구용 가짜 영상", 12, H - 14);
        g.dispose();
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
