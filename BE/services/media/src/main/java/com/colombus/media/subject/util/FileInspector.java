package com.colombus.media.subject.util;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import com.colombus.common.utility.crypto.Hashes;

@Component
public class FileInspector {
    
    private final Tika tika;
    public FileInspector(Tika tika) { this.tika = tika; }

    private static final long MAX_SIZE = 5L * 1024 * 1024;

    private static final long MAX_PIXELS = 20_000_000L;
    private static final int  MAX_WIDTH  = 10_000;
    private static final int  MAX_HEIGHT = 10_000;

    // 허용 이미지
    private static final Set<String> ALLOWED_IMAGE_MIME = Set.of(
        "image/jpeg", "image/png", "image/webp"
    );

    // “실행파일/스크립트/일반 문서/압축” 등 주요 금지 계열(감지 전용)
    private static final Set<String> FORBIDDEN_MIME_PREFIXES = Set.of(
        "application/x-executable", "application/x-dosexec", "application/x-msdownload",
        "application/x-mach-binary", "application/x-sharedlib",
        "application/java-archive", "application/zip", "application/pdf",
        "text/x-shellscript", "text/x-script", "text/x-python", "text/x-perl"
    );

    public void assertSizeLimit(Long sizeFromHead) {
        if (sizeFromHead == null) return; // S3는 보통 null 아님. 방어적으로 통과.
        assertSizeLimit(sizeFromHead.longValue(), "S3 HEAD");
    }

    public void assertSizeLimit(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Empty file");
        if (bytes.length > MAX_SIZE) throw new IllegalArgumentException("File exceeds 5MB");
        assertSizeLimit((long) bytes.length, "downloaded body");
    }

    private void assertSizeLimit(long sizeBytes, String source) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Empty file (" + source + ")");
        }
        if (sizeBytes > MAX_SIZE) {
            throw new IllegalArgumentException(
                "File exceeds " + human(MAX_SIZE) +
                " (" + source + " reported " + human(sizeBytes) + ")"
            );
        }
    }

    private static String human(long bytes) {
        // 간단한 MiB 표기
        if (bytes < 1024) return bytes + " B";
        double kib = bytes / 1024.0;
        if (kib < 1024) return String.format("%.1f KiB", kib);
        double mib = kib / 1024.0;
        return String.format("%.2f MiB", mib);
    }

    /** 파일명 힌트를 함께 주면 감지 정확도가 올라감 */
    public String detectMime(byte[] bytes, String originalFilename) {
        String mime = (originalFilename != null && !originalFilename.isBlank())
                ? tika.detect(bytes, originalFilename)
                : tika.detect(bytes);
        return (mime == null) ? "application/octet-stream" : mime.toLowerCase(Locale.ROOT);
    }

    /** 실행파일/스크립트/문서/압축 등 명시 차단(로그/모니터링 용도로도 유용) */
    public void assertNotExecutableOrForbidden(String mime) {
        String m = mime.toLowerCase(Locale.ROOT);
        for (String p : FORBIDDEN_MIME_PREFIXES) {
            String pl = p.toLowerCase(Locale.ROOT);
            if (m.equals(pl) || m.startsWith(pl) || m.contains("script")) {
                throw new IllegalArgumentException("Forbidden file type: " + mime);
            }
        }
    }

    /** 화이트리스트 이미지만 허용 */
    public void assertAllowedImage(String mime) {
        if (!ALLOWED_IMAGE_MIME.contains(mime)) {
            throw new IllegalArgumentException("Unsupported image type: " + mime);
        }
    }

    public Dimension probeImageDimensionOrThrow(byte[] bytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) throw new IllegalArgumentException("Unrecognized image format");
            ImageReader r = readers.next();
            try {
                r.setInput(iis, true, true); // read metadata only when possible
                int w = r.getWidth(0);
                int h = r.getHeight(0);
                long px = (long) w * (long) h;
                if (w <= 0 || h <= 0) throw new IllegalArgumentException("Invalid image dimension");
                if (w > MAX_WIDTH || h > MAX_HEIGHT || px > MAX_PIXELS) {
                    throw new IllegalArgumentException("Image too large: " + w + "x" + h);
                }
                return new Dimension(w, h);
            } finally {
                r.dispose();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid image header", e);
        }
    }

    /** 실제 디코딩으로 위변조/스푸핑 2차 방어 */
    public BufferedImage decodeImageOrThrow(byte[] bytes) {
        try (var in = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0)
                throw new IllegalArgumentException("Invalid image data");
            return img;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid image data", e);
        }
    }

    /** 시각 정보 중심 비교를 위해: sRGB + 흰 배경 합성으로 평면화 */
    public BufferedImage normalizeForVisualHash(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB); // 알파 제거
        Graphics2D g = out.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            g.setColor(Color.WHITE);             // 고정 배경(화이트)
            g.fillRect(0, 0, w, h);
            // TODO(옵션): EXIF Orientation 적용 필요 시 여기서 회전/뒤집기 후 drawImage
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    public String sha256(byte[] bytes) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var d = md.digest(bytes);
            return Hashes.toHex(d);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String pixelSha256(BufferedImage img) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            BufferedImage norm = normalizeForVisualHash(img);
            int w = norm.getWidth(), h = norm.getHeight();
            int[] rgb = norm.getRGB(0, 0, w, h, null, 0, w);

            var buf = new byte[rgb.length << 2];
            int j = 0;
            for (int v : rgb) {
                buf[j++] = (byte) (v >>> 24);
                buf[j++] = (byte) (v >>> 16);
                buf[j++] = (byte) (v >>> 8);
                buf[j++] = (byte) v;
            }
            var d = md.digest(buf);
            return Hashes.toHex(d);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
