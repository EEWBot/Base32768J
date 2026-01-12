package net.eewbot.base32768j;

import net.eewbot.base32768j.exception.BufferTooSmallException;
import net.eewbot.base32768j.exception.IllegalBase32768TextException;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Base32768Decoder {
    Base32768Decoder() {}

    private static final int SEVEN_BITS_CP_FINAL = 0x29F;

    private static final char INVALID = 0xFFFF;
    private static final char FLAG7   = 0x8000;

    private static final char[] DECODE = new char[1 << 16];
    private static final byte[] LAST_BITS = new byte[1 << 16];

    static {
        Arrays.fill(DECODE, INVALID);

        // 7-bit blocks（末尾のみ有効）
        for (int i = 0; i < Base32768Encoder.CODES_7.length; i++) {
            int base = i << 5;                 // 0..127
            int cp0  = Base32768Encoder.CODES_7[i];
            for (int lo = 0; lo < 32; lo++) {
                int cp = cp0 + lo;
                DECODE[cp] = (char) (FLAG7 | (base + lo)); // bit15=1 を7bitフラグに
                LAST_BITS[cp] = 7;
            }
        }

        // 15-bit blocks（非末尾/末尾とも有効）
        for (int i = 0; i < Base32768Encoder.CODES_15.length; i++) {
            int base = i << 5;                 // 0..32767
            int cp0  = Base32768Encoder.CODES_15[i];
            for (int lo = 0; lo < 32; lo++) {
                int cp = cp0 + lo;
                DECODE[cp] = (char) (base + lo);        // bit15=0
                LAST_BITS[cp] = 15;
            }
        }

        // surrogate 明示無効化
        for (int cp = 0xD800; cp <= 0xDFFF; cp++) {
            DECODE[cp] = INVALID;
            LAST_BITS[cp] = 0;
        }
    }

    private static int calcBufferLength(String src) {
        int srcCodePointCount = src.codePointCount(0, src.length());

        int offset = src.offsetByCodePoints(0, srcCodePointCount - 1);
        int lastCodePoint = src.codePointAt(offset);
        boolean isSevenBitsCode = lastCodePoint <= SEVEN_BITS_CP_FINAL;

        return (isSevenBitsCode ? (srcCodePointCount - 1) * 15 + 7 : srcCodePointCount * 15) / 8;
    }

    /**
     * Decodes all bytes from the input byte array using the {@link Base32768} encoding scheme, writing the results into
     * a newly-allocated output byte array. The returned byte array is of the length of the resulting bytes.
     * @param src the byte array to decode
     * @return A newly-allocated byte array containing the decoded bytes.
     * @throws IllegalBase32768TextException if src is not in valid Base32768 scheme.
     */
    public byte[] decode(byte[] src) {
        return decode(new String(src, StandardCharsets.UTF_8));
    }

    /**
     * Decodes all bytes from the input byte array using the {@link Base32768} encode scheme, writing the results into
     * the given output byte array, starting at offset 0.<br>
     * It is the responsibility of the invoker of this method to make sure the output byte array dst has enough space
     * for decoding all bytes from the input byte array. No bytes will be written to the output byte array if the output
     * byte array is not big enough.
     * @param src the byte array to decode
     * @param dst the output byte array
     * @return The number of bytes written to the output byte array
     * @throws IllegalBase32768TextException if src is not in valid Base32768 scheme.
     * @throws BufferTooSmallException if dst does not have enough space for decoding all input bytes.
     */
    public int decode(byte[] src, byte[] dst) {
        String srcString = new String(src, StandardCharsets.UTF_8);
        int bufferLength = calcBufferLength(srcString);

        if (dst.length < bufferLength) throw new BufferTooSmallException(bufferLength, dst.length);

        byte[] result = decode(srcString);
        System.arraycopy(result, 0, dst, 0, bufferLength);

        return bufferLength;
    }

    /**
     * Decodes all bytes from the input byte buffer using the {@link Base32768} encoding scheme, writing the results
     * into a newly-allocated ByteBuffer.<br>
     * Upon return, the source buffer's position will be updated to its limit; its limit will not have been changed.
     * The returned output buffer's position will be zero and its limit will be the number of resulting decoded bytes.
     * IllegalBase32768TextException is thrown if the input buffer is not in valid Base32768 encoding scheme.
     * The position of the input buffer will not be advanced in this case.
     * @param buffer the ByteBuffer to decode
     * @return A newly-allocated byte buffer containing the decoded bytes
     * @throws IllegalBase32768TextException if src is not in valid Base32768 scheme.
     */
    public ByteBuffer decode(ByteBuffer buffer) {
        byte[] src = new byte[buffer.remaining()];
        buffer.get(src);
        return ByteBuffer.wrap(decode(src));
    }

    /**
     * Decode a Base32768 encoded String into a newly-allocated byte array using the {@link Base32768} encoding scheme.
     * @param src the string to decode
     * @return A newly-allocated byte array containing the decoded bytes.
     * @throws IllegalBase32768TextException if src is not in valid Base32768 scheme
     */
    public byte[] decode(String src) {
        final int n = src.length();
        if (n == 0) return new byte[0];

        final char last = src.charAt(n - 1);
        final int lastBits = LAST_BITS[last] & 0xFF;
        if (lastBits == 0) throw new IllegalBase32768TextException("Invalid Base32768 text");

        final int outLen = ((n - 1) * 15 + lastBits) >>> 3;
        final byte[] out = new byte[outLen];

        final char[] decode = DECODE;

        int oi = 0;
        int si = 0;

        // ---- Fast Path: 8文字(=120bit) -> 15バイト固定出力 ----
        // end までのうち、8文字単位で回す（last は含めない）
        final int end = n - 1; // last は別処理
        final int fastEnd = end & ~7; // 8の倍数に切り下げ
        while (si < fastEnd) {
            final int v0 = decode[src.charAt(si)];
            final int v1 = decode[src.charAt(si + 1)];
            final int v2 = decode[src.charAt(si + 2)];
            final int v3 = decode[src.charAt(si + 3)];
            final int v4 = decode[src.charAt(si + 4)];
            final int v5 = decode[src.charAt(si + 5)];
            final int v6 = decode[src.charAt(si + 6)];
            final int v7 = decode[src.charAt(si + 7)];

            // check invalid
            final int m = v0 | v1 | v2 | v3 | v4 | v5 | v6 | v7;
            if ((m & 0x8000) != 0) {
                throw new IllegalBase32768TextException("Invalid Base32768 text");
            }

            // 15バイトを直接生成（MSB→LSBの順）
            // 各 v は 0..0x7FFF（15bit）
            out[oi]      = (byte) (v0 >>> 7);
            out[oi + 1]  = (byte) ((v0 << 1) | (v1 >>> 14));
            out[oi + 2]  = (byte) (v1 >>> 6);
            out[oi + 3]  = (byte) ((v1 << 2) | (v2 >>> 13));
            out[oi + 4]  = (byte) (v2 >>> 5);
            out[oi + 5]  = (byte) ((v2 << 3) | (v3 >>> 12));
            out[oi + 6]  = (byte) (v3 >>> 4);
            out[oi + 7]  = (byte) ((v3 << 4) | (v4 >>> 11));
            out[oi + 8]  = (byte) (v4 >>> 3);
            out[oi + 9]  = (byte) ((v4 << 5) | (v5 >>> 10));
            out[oi + 10] = (byte) (v5 >>> 2);
            out[oi + 11] = (byte) ((v5 << 6) | (v6 >>> 9));
            out[oi + 12] = (byte) (v6 >>> 1);
            out[oi + 13] = (byte) ((v6 << 7) | (v7 >>> 8));
            out[oi + 14] = (byte) (v7);

            oi += 15;
            si += 8;
        }

        long acc = 0L;
        int bitCount = 0;

        final int fast2Limit = end - 1; // 2文字取れる限界（lastは除外）
        while (si < fast2Limit) {
            final int v0 = decode[src.charAt(si)];
            final int v1 = decode[src.charAt(si + 1)];

            // INVALID == 0xFFFF, valid values are 0..0x7FFF only
            if (((v0 | v1) & 0x8000) != 0) {
                throw new IllegalBase32768TextException("Invalid Base32768 text");
            }

            acc = (acc << 30) | ((long) v0 << 15) | (long) v1;
            bitCount += 30;

            out[oi]     = (byte) (acc >>> (bitCount - 8));
            out[oi + 1] = (byte) (acc >>> (bitCount - 16));
            out[oi + 2] = (byte) (acc >>> (bitCount - 24));
            oi += 3;
            bitCount -= 24;

            if (bitCount >= 8) {
                out[oi++] = (byte) (acc >>> (bitCount - 8));
                bitCount -= 8;
            }

            si += 2;
        }

        if (si < end) {
            final int v = decode[src.charAt(si)];
            if ((v & 0x8000) != 0) throw new IllegalBase32768TextException("Invalid Base32768 text");

            acc = (acc << 15) | v;
            bitCount += 15;

            out[oi++] = (byte) (acc >>> (bitCount - 8));
            bitCount -= 8;
            if (bitCount >= 8) {
                out[oi++] = (byte) (acc >>> (bitCount - 8));
                bitCount -= 8;
            }
        }

        {
            int v = decode[last];
            if (v == INVALID) {
                throw new IllegalBase32768TextException("Invalid Base32768 text");
            }
            v &= 0x7FFF; // strip 7-bit flag if present

            acc = (acc << lastBits) | (long) v;
            bitCount += lastBits;

            while (bitCount >= 8) {
                bitCount -= 8;
                out[oi++] = (byte) (acc >>> bitCount);
            }
        }

        if (bitCount > 0 && (acc & ((1L << bitCount) - 1)) != ((1L << bitCount) - 1)) {
            throw new IllegalBase32768TextException("Bad padding");
        }

        return out;
    }

    public InputStream wrap(InputStream is) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
