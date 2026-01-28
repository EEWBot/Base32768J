package net.eewbot.base32768j;

import net.eewbot.base32768j.exception.BufferTooSmallException;
import net.eewbot.base32768j.exception.IllegalBase32768TextException;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Base32768Decoder {
    Base32768Decoder() {}

    private static final char INVALID = 0xFFFF;
    private static final char FLAG7 = 0x8000;

    private static final int TABLE_SIZE = 0xa840 + 32; // 43104 (max CODES_15 codepoint + 32)
    private static final char[] DECODE = new char[TABLE_SIZE];
    private static final int LAST_BITS_SIZE = (0xa840 >> 5) + 1; // 1347
    private static final byte[] LAST_BITS = new byte[LAST_BITS_SIZE];

    private static final VarHandle VH_LONG_BE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    static {
        Arrays.fill(DECODE, INVALID);

        // 7-bit blocks（末尾のみ有効）
        for (int i = 0; i < Base32768Encoder.CODES_7.length; i++) {
            int base = i << 5;                 // 0..127
            int cp0 = Base32768Encoder.CODES_7[i];
            LAST_BITS[cp0 >> 5] = 7;
            for (int lo = 0; lo < 32; lo++) {
                int cp = cp0 + lo;
                DECODE[cp] = (char) (FLAG7 | (base + lo)); // bit15=1 を7bitフラグに
            }
        }

        // 15-bit blocks（非末尾/末尾とも有効）
        for (int i = 0; i < Base32768Encoder.CODES_15.length; i++) {
            int base = i << 5;                 // 0..32767
            int cp0 = Base32768Encoder.CODES_15[i];
            LAST_BITS[cp0 >> 5] = 15;
            for (int lo = 0; lo < 32; lo++) {
                int cp = cp0 + lo;
                DECODE[cp] = (char) (base + lo);        // bit15=0
            }
        }
    }

    private static int calcBufferLength(String src) {
        if (src.isEmpty()) return 0;
        final int n = src.length();
        final char last = src.charAt(n - 1);
        final int block = last >> 5;
        final int lastBits = (block < LAST_BITS_SIZE) ? (LAST_BITS[block] & 0xFF) : 0;
        if (lastBits == 0) throw new IllegalBase32768TextException(n - 1, last);
        return ((n - 1) * 15 + lastBits) >>> 3;
    }

    /**
     * Decodes all bytes from the input byte array using the {@link Base32768} encoding scheme, writing the results into
     * a newly-allocated output byte array. The returned byte array is of the length of the resulting bytes.
     *
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
     *
     * @param src the byte array to decode
     * @param dst the output byte array
     * @return The number of bytes written to the output byte array
     * @throws IllegalBase32768TextException if src is not in valid Base32768 scheme.
     * @throws BufferTooSmallException       if dst does not have enough space for decoding all input bytes.
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
     *
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
     *
     * @param src the string to decode
     * @return A newly-allocated byte array containing the decoded bytes.
     * @throws IllegalBase32768TextException if src is not in valid Base32768 scheme
     */
    public byte[] decode(String src) {
        final int n = src.length();
        if (n == 0) return new byte[0];

        final char last = src.charAt(n - 1);
        final int block = last >> 5;
        final int lastBits = (block < LAST_BITS_SIZE) ? (LAST_BITS[block] & 0xFF) : 0;
        if (lastBits == 0) throw new IllegalBase32768TextException(n - 1, last);

        final int outLen = ((n - 1) * 15 + lastBits) >>> 3;
        final byte[] out = new byte[outLen];

        final char[] decode = DECODE;

        int oi = 0;
        int si = 0;

        // ---- Fast Path: 8文字(=120bit) -> 15バイト固定出力 ----
        // end までのうち、8文字単位で回す（last は含めない）
        final int end = n - 1;
        final int fastEnd = end & ~7;
        while (si < fastEnd) {
            int v0 = decode[src.charAt(si)];
            int v1 = decode[src.charAt(si + 1)];
            int v2 = decode[src.charAt(si + 2)];
            int v3 = decode[src.charAt(si + 3)];
            int v4 = decode[src.charAt(si + 4)];
            int v5 = decode[src.charAt(si + 5)];
            int v6 = decode[src.charAt(si + 6)];
            int v7 = decode[src.charAt(si + 7)];

            int m = v0 | v1 | v2 | v3 | v4 | v5 | v6 | v7;
            if ((m & 0x8000) != 0) {
                throwDetailedException(src, si, v0, v1, v2, v3, v4, v5, v6, v7);
            }

            // w0: v0, v1, v2, v3, v4上位4ビット
            long w0 = ((long) v0 << 49)
                | ((long) v1 << 34)
                | ((long) v2 << 19)
                | ((long) v3 << 4)
                | (v4 >>> 11);

            // w1: out[7]と同じバイトから始める
            // out[7] = w0 & 0xFF なので、それを最上位に
            // 残りは v4(下位11), v5, v6, v7 を詰める
            long w1 = ((w0 & 0xFF) << 56)
                | ((long) (v4 & 0x7FF) << 45)
                | ((long) v5 << 30)
                | ((long) v6 << 15)
                | (long) v7;

            VH_LONG_BE.set(out, oi, w0);
            VH_LONG_BE.set(out, oi + 7, w1);

            si += 8;
            oi += 15;
        }

        long acc = 0L;
        int bitCount = 0;

        // ---- Fast Path (2文字): 2文字(=30bit) -> 3バイト + 余り6bit ----
        final int fast2Limit = end - 1; // 2文字取れる限界（lastは除外）
        while (si < fast2Limit) {
            final int v0 = decode[src.charAt(si)];
            final int v1 = decode[src.charAt(si + 1)];

            // INVALID == 0xFFFF, valid values are 0..0x7FFF only
            if (((v0 | v1) & 0x8000) != 0) {
                int offset = (v0 & 0x8000) != 0 ? 0 : 1;
                int v = offset == 0 ? v0 : v1;
                throwForInvalidValue(si + offset, src.charAt(si + offset), v);
            }

            acc = (acc << 30) | ((long) v0 << 15) | (long) v1;
            bitCount += 30;

            out[oi] = (byte) (acc >>> (bitCount - 8));
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
            if ((v & 0x8000) != 0) {
                throwForInvalidValue(si, src.charAt(si), v);
            }

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
                throw new IllegalBase32768TextException(n - 1, last);
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
            long actual = acc & ((1L << bitCount) - 1);
            throw new IllegalBase32768TextException("Bad padding at position " + (n - 1) + ": expected " + bitCount + " bits of 1s, got 0b" + Long.toBinaryString(actual));
        }

        return out;
    }

    public InputStream wrap(InputStream is) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private static void throwForInvalidValue(int position, char ch, int decodedValue) {
        if (decodedValue == INVALID) {
            throw new IllegalBase32768TextException(position, ch);
        } else {
            throw new IllegalBase32768TextException("7-bit code point at non-final position " + position + ": " + (int) ch);
        }
    }

    private static void throwDetailedException(String src, int si, int v0, int v1, int v2, int v3, int v4, int v5, int v6, int v7) {
        int[] vals = {v0, v1, v2, v3, v4, v5, v6, v7};
        for (int i = 0; i < 8; i++) {
            if ((vals[i] & 0x8000) != 0) {
                throwForInvalidValue(si + i, src.charAt(si + i), vals[i]);
            }
        }
        // Should never reach here
        throw new IllegalBase32768TextException("Invalid Base32768 text");
    }
}
