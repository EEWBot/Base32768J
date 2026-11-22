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

    // Covers the full char range so that DECODE[c] for any char c needs no bounds check
    // (C2 eliminates the range check) and never throws ArrayIndexOutOfBoundsException.
    private static final int TABLE_SIZE = Character.MAX_VALUE + 1; // 65536
    private static final char[] DECODE = new char[TABLE_SIZE];

    // Inputs shorter than this have no full 8-char block, so the copy to char[] for the fast path does not pay off.
    // Measured (JMH, JDK 24): Small Path is ~17% faster at 5 chars, but already ~14% slower at 9 chars.
    private static final int SMALL_THRESHOLD = 9;

    private static final VarHandle VH_LONG_BE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    static {
        Arrays.fill(DECODE, INVALID);

        // Build reverse lookup tables from Unicode ranges
        int idx = 0;

        // 7-bit blocks (valid only at end of input)
        for (int[] range : Base32768Encoder.CODES_7_RANGES) {
            for (int cp = range[0]; cp <= range[1]; cp++) {
                DECODE[cp] = (char) (FLAG7 | idx);
                idx++;
            }
        }

        // 15-bit blocks (valid anywhere)
        idx = 0;
        for (int[] range : Base32768Encoder.CODES_15_RANGES) {
            for (int cp = range[0]; cp <= range[1]; cp++) {
                DECODE[cp] = (char) idx;
                idx++;
            }
        }
    }

    private static int calcBufferLength(String src) {
        if (src.isEmpty()) return 0;
        final int n = src.length();
        final char last = src.charAt(n - 1);
        return ((n - 1) * 15 + lastBits(n - 1, last, DECODE[last])) >>> 3;
    }

    /**
     * Returns the number of bits carried by the final character (7 or 15).
     * INVALID (0xFFFF) also has FLAG7 set, so it must be checked first.
     */
    private static int lastBits(int position, char last, int v) {
        if (v == INVALID) throw new IllegalBase32768TextException(position, last);
        return (v & FLAG7) != 0 ? 7 : 15;
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
        // Absolute get: the position is advanced only after decoding succeeds, as documented above.
        byte[] src = new byte[buffer.remaining()];
        buffer.get(buffer.position(), src);
        ByteBuffer result = ByteBuffer.wrap(decode(src));
        buffer.position(buffer.limit());
        return result;
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

        final char[] decode = DECODE;

        final char last = src.charAt(n - 1);
        final int lastValue = decode[last];
        final int lastBits = lastBits(n - 1, last, lastValue);

        final int outLen = ((n - 1) * 15 + lastBits) >>> 3;
        final byte[] out = new byte[outLen];

        int oi = 0;
        int si = 0;
        long acc = 0L;
        int bitCount = 0;
        final int end = n - 1; // last は含めない

        if (n < SMALL_THRESHOLD) {
            // ---- Small Path: 短い入力は配列確保を避け、charAt で1文字ずつ処理 ----
            for (; si < end; si++) {
                final char c = src.charAt(si);
                final int v = decode[c];
                if ((v & 0x8000) != 0) {
                    throwForInvalidValue(si, c, v);
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
        } else {
            // String.charAt reloads value/coder and re-checks bounds and Latin1 on every call inside the hot loop
            // (C2 does not hoist them), which also causes heavy register spills. Copying once is much cheaper
            // except for tiny inputs, which take the Small Path above.
            final char[] s = src.toCharArray();

            // ---- Fast Path: 8文字(=120bit) -> 15バイト固定出力 ----
            // end までのうち、8文字単位で回す
            final int fastEnd = end & ~7;
            while (si < fastEnd) {
                int v0 = decode[s[si]];
                int v1 = decode[s[si + 1]];
                int v2 = decode[s[si + 2]];
                int v3 = decode[s[si + 3]];
                int v4 = decode[s[si + 4]];
                int v5 = decode[s[si + 5]];
                int v6 = decode[s[si + 6]];
                int v7 = decode[s[si + 7]];

                int m = v0 | v1 | v2 | v3 | v4 | v5 | v6 | v7;
                if ((m & 0x8000) != 0) {
                    throwDetailedException(s, si);
                }

                // w0: v0, v1, v2, v3, v4上位4ビット
                long w0 = ((long) v0 << 49)
                    | ((long) v1 << 34)
                    | ((long) v2 << 19)
                    | ((long) v3 << 4)
                    | (v4 >>> 11);

                // w1: out[7]と同じバイトから始める（ブロックの bit 56..119）
                // 最上位バイト out[7] = v3 の下位4ビット + v4 の上位4ビットなので、
                // w0 を経由せず v3 から直接組む（w0 とのデータ依存をなくす）
                long w1 = ((long) v3 << 60)
                    | ((long) v4 << 45)
                    | ((long) v5 << 30)
                    | ((long) v6 << 15)
                    | (long) v7;

                VH_LONG_BE.set(out, oi, w0);
                VH_LONG_BE.set(out, oi + 7, w1);

                si += 8;
                oi += 15;
            }

            // ---- Fast Path (2文字): 2文字(=30bit) -> 3バイト + 余り6bit ----
            final int fast2Limit = end - 1; // 2文字取れる限界（lastは除外）
            while (si < fast2Limit) {
                final int v0 = decode[s[si]];
                final int v1 = decode[s[si + 1]];

                // INVALID == 0xFFFF, valid values are 0..0x7FFF only
                if (((v0 | v1) & 0x8000) != 0) {
                    int offset = (v0 & 0x8000) != 0 ? 0 : 1;
                    int v = offset == 0 ? v0 : v1;
                    throwForInvalidValue(si + offset, s[si + offset], v);
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
                final int v = decode[s[si]];
                if ((v & 0x8000) != 0) {
                    throwForInvalidValue(si, s[si], v);
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
        }

        {
            final int v = lastValue & 0x7FFF; // strip 7-bit flag (validated above)

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

    // Re-decodes the block on the (cold) error path so that the hot loop keeps neither src nor v0..v7 alive for it.
    private static void throwDetailedException(char[] s, int si) {
        for (int i = 0; i < 8; i++) {
            final char ch = s[si + i];
            final int v = DECODE[ch];
            if ((v & 0x8000) != 0) {
                throwForInvalidValue(si + i, ch, v);
            }
        }
        // Should never reach here
        throw new IllegalBase32768TextException("Invalid Base32768 text");
    }
}
