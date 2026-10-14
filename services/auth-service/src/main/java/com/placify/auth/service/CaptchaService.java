package com.placify.auth.service;

import com.placify.auth.config.AuthProperties;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Image CAPTCHA drawn with plain Java 2D (no external library).
 * The answer is kept in Redis for 5 minutes and can be used once.
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String KEY_PREFIX = "placify:captcha:";
    // no 0/O, 1/I/l to avoid confusing characters
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int WIDTH = 180;
    private static final int HEIGHT = 60;

    private final StringRedisTemplate redis;
    private final SecureRandom random;
    private final AuthProperties props;

    public record Captcha(String captchaId, String imageBase64) {
    }

    public Captcha generate() {
        String text = randomText(5);
        String id = UUID.randomUUID().toString();
        redis.opsForValue().set(KEY_PREFIX + id, text, Duration.ofMinutes(5));
        return new Captcha(id, "data:image/png;base64," + Base64.getEncoder().encodeToString(draw(text)));
    }

    /** Validates and consumes the captcha. */
    public void verify(String captchaId, String answer) {
        if (!props.captchaEnabled()) {
            return;
        }
        if (captchaId == null || answer == null) {
            throw ApiException.badRequest("CAPTCHA_REQUIRED", "Please enter the captcha");
        }
        String key = KEY_PREFIX + captchaId;
        String expected = redis.opsForValue().get(key);
        redis.delete(key);
        if (expected == null) {
            throw ApiException.badRequest("CAPTCHA_EXPIRED", "Captcha expired, please refresh it");
        }
        if (!expected.equalsIgnoreCase(answer.trim())) {
            throw ApiException.badRequest("CAPTCHA_INVALID", "Captcha does not match");
        }
    }

    private String randomText(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    private byte[] draw(String text) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(240, 244, 250));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // background noise lines
            for (int i = 0; i < 10; i++) {
                g.setColor(randomColor(150, 210));
                g.setStroke(new BasicStroke(1 + random.nextFloat()));
                g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            int x = 14;
            for (char c : text.toCharArray()) {
                AffineTransform original = g.getTransform();
                double angle = Math.toRadians(random.nextInt(40) - 20);
                g.rotate(angle, x + 12, 40);
                g.setColor(randomColor(20, 110));
                g.drawString(String.valueOf(c), x, 44 + random.nextInt(6) - 3);
                g.setTransform(original);
                x += 31;
            }

            // dots
            for (int i = 0; i < 120; i++) {
                g.setColor(randomColor(100, 200));
                g.fillRect(random.nextInt(WIDTH), random.nextInt(HEIGHT), 1, 1);
            }
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Color randomColor(int min, int max) {
        int range = max - min;
        return new Color(min + random.nextInt(range), min + random.nextInt(range), min + random.nextInt(range));
    }
}
