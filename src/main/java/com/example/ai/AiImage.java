package com.example.ai;

import java.util.Base64;

/**
 * A picture sent with a request: a web address the provider fetches, or the image's bytes.
 * Claude and OpenAI connections can see pictures; the others cannot.
 */
public record AiImage(String url, String mediaType, String base64) {

    /** Largest picture sent as bytes (providers refuse bigger ones). */
    public static final int MAX_BYTES = 4_500_000;

    public static AiImage ofUrl(String url) {
        return new AiImage(url.trim(), null, null);
    }

    /** From the file's bytes; the type is read from the bytes (png, jpeg, gif or webp). */
    public static AiImage ofBytes(byte[] data) {
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("the picture is too big (" + data.length / 1000 + " KB; at most " + MAX_BYTES / 1000 + " KB)");
        }
        String type = mediaType(data);
        if (type == null) {
            throw new IllegalArgumentException("not a png, jpeg, gif or webp picture");
        }
        return new AiImage(null, type, Base64.getEncoder().encodeToString(data));
    }

    public boolean isUrl() {
        return url != null;
    }

    static String mediaType(byte[] d) {
        if (d.length > 8 && (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "image/png";
        }
        if (d.length > 3 && (d[0] & 0xff) == 0xFF && (d[1] & 0xff) == 0xD8) {
            return "image/jpeg";
        }
        if (d.length > 6 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F') {
            return "image/gif";
        }
        if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
