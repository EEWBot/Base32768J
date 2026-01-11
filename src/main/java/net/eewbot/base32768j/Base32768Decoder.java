package net.eewbot.base32768j;

import net.eewbot.base32768j.exception.BufferTooSmallException;
import net.eewbot.base32768j.exception.IllegalBase32768TextException;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Base32768Decoder {
    Base32768Decoder() {}

    private static final int SEVEN_BITS_CP_FINAL = 0x29F;
    private static final int[] DECODE_BASE = new int[1 << 16];

    static {
        java.util.Arrays.fill(DECODE_BASE, -1);

        for (int i = 0; i < Base32768Encoder.CODES_7.length; i++) {
            DECODE_BASE[Base32768Encoder.CODES_7[i]] = i << 5; // i*32
        }
        for (int i = 0; i < Base32768Encoder.CODES_15.length; i++) {
            DECODE_BASE[Base32768Encoder.CODES_15[i]] = i << 5; // i*32
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

        // 末尾が 7bit 終端かどうか（char単位で判定）
        final char last = src.charAt(n - 1);
        if (Character.isSurrogate(last)) {
            throw new IllegalBase32768TextException(n, last);
        }
        final boolean lastIs7 = last <= SEVEN_BITS_CP_FINAL;

        // 文字数 = n（BMP前提）。もし surrogate が混じるなら途中で例外にする。
        final int outLen = (lastIs7 ? (n - 1) * 15 + 7 : n * 15) >>> 3;
        final byte[] out = new byte[outLen];

        int oi = 0;
        long acc = 0L;
        int bitCount = 0;

        for (int si = 0; si < n; si++) {
            char ch = src.charAt(si);
            if (Character.isSurrogate(ch)) {
                throw new IllegalBase32768TextException(si + 1, ch);
            }

            final int base = DECODE_BASE[ch & ~31];
            if (base < 0) {
                throw new IllegalBase32768TextException(si + 1, ch);
            }

            final int v = base + (ch & 31);

            if (ch <= SEVEN_BITS_CP_FINAL) {
                // 7bit は最後の1文字のみ許可
                if (si != n - 1) throw new IllegalBase32768TextException(ch);
                acc = (acc << 7) | v;
                bitCount += 7;
            } else {
                acc = (acc << 15) | v;
                bitCount += 15;
            }

            while (bitCount >= 8) {
                bitCount -= 8;
                out[oi++] = (byte) (acc >>> bitCount);
                acc &= (1L << bitCount) - 1L;
            }
        }

        // padding check
        if (bitCount > 0 && acc != ((1L << bitCount) - 1L)) {
            throw new IllegalBase32768TextException("Bad padding");
        }
        return out;
    }

    public InputStream wrap(InputStream is) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
