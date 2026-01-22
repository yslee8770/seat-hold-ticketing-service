package com.example.ticket.domain.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public final class SeatIdsCodec {

    private static final HexFormat HEX = HexFormat.of();

    private SeatIdsCodec() {}

    /** 중복 제거 + 오름차순 정렬 */
    public static List<Long> normalize(List<Long> seatIds) {
        Objects.requireNonNull(seatIds, "seatIds");
        return seatIds.stream().distinct().sorted().toList();
    }

    /** canonical string: "12,35,90" (항상 동일 입력이면 동일 문자열) */
    public static String toCanonicalString(List<Long> seatIds) {
        List<Long> normalized = normalize(seatIds);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < normalized.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(normalized.get(i));
        }
        return sb.toString();
    }

    /** sha256 hex of canonical string */
    public static String sha256HexOfCanonical(List<Long> seatIds) {
        String canonical = toCanonicalString(seatIds);
        return sha256Hex(canonical);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

