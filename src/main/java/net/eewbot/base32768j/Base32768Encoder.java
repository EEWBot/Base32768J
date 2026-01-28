package net.eewbot.base32768j;

import net.eewbot.base32768j.exception.BufferTooSmallException;

import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * This class implements an encoder for encoding byte data using the Base32768 encoding scheme follows the
 * <a href="https://github.com/qntm/base32768">original Base32768 implementation.</a><br>
 * Instances of {@link Base32768Encoder} class are safe for use by multiple concurrent threads.<br>
 * Unless otherwise noted, passing a null argument to a method of this class will cause a {@link NullPointerException}
 * to be thrown.
 */
public class Base32768Encoder {
    Base32768Encoder() {}

    // Unicode ranges for 7-bit encoding (128 total characters, 4 blocks of 32)
    static final int[][] CODES_7_RANGES = {
        {0x0180, 0x019f},
        {0x0240, 0x029f}
    };

    // Unicode ranges for 15-bit encoding (32768 total characters, 1024 blocks of 32)
    static final int[][] CODES_15_RANGES = {
        {0x04a0, 0x04bf}, {0x0500, 0x051f}, {0x0680, 0x06bf}, {0x0760, 0x079f},
        {0x07c0, 0x07df}, {0x1000, 0x101f}, {0x10a0, 0x10bf}, {0x1100, 0x115f},
        {0x1180, 0x119f}, {0x11e0, 0x123f}, {0x1260, 0x127f}, {0x12e0, 0x12ff},
        {0x1320, 0x133f}, {0x13a0, 0x13df}, {0x1420, 0x165f}, {0x16a0, 0x16df},
        {0x1780, 0x179f}, {0x1820, 0x185f}, {0x18c0, 0x18df}, {0x1980, 0x199f},
        {0x19e0, 0x19ff}, {0x1a20, 0x1a3f}, {0x1bc0, 0x1bdf}, {0x1c00, 0x1c1f},
        {0x1d00, 0x1d1f}, {0x21e0, 0x21ff}, {0x22c0, 0x22df}, {0x2340, 0x23df},
        {0x2400, 0x241f}, {0x2500, 0x275f}, {0x2780, 0x27bf}, {0x2800, 0x297f},
        {0x29a0, 0x29bf}, {0x2a20, 0x2a5f}, {0x2a80, 0x2abf}, {0x2ae0, 0x2b5f},
        {0x2c00, 0x2c1f}, {0x2c80, 0x2cdf}, {0x2d00, 0x2d1f}, {0x2d40, 0x2d5f},
        {0x2ea0, 0x2edf}, {0x31c0, 0x31df}, {0x3400, 0x4d9f}, {0x4dc0, 0x9fbf},
        {0xa000, 0xa47f}, {0xa4a0, 0xa4bf}, {0xa500, 0xa5ff}, {0xa640, 0xa65f},
        {0xa6a0, 0xa6df}, {0xa700, 0xa75f}, {0xa780, 0xa79f}, {0xa840, 0xa85f}
    };

    private static final VarHandle VH_LONG_BE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    private static final char[] CODES15_CHAR = new char[1 << 15];
    private static final char[] CODES7_CHAR = new char[1 << 7];

    static {
        // Build CODES15_CHAR lookup table from Unicode ranges
        int idx = 0;
        for (int[] range : CODES_15_RANGES) {
            for (int cp = range[0]; cp <= range[1]; cp++) {
                CODES15_CHAR[idx++] = (char) cp;
            }
        }

        // Build CODES7_CHAR lookup table from Unicode ranges
        idx = 0;
        for (int[] range : CODES_7_RANGES) {
            for (int cp = range[0]; cp <= range[1]; cp++) {
                CODES7_CHAR[idx++] = (char) cp;
            }
        }
    }

    /**
     * Encodes all bytes from the specified byte array into a newly-allocated byte array using the {@link Base32768}
     * encoding scheme. The returned byte array is of the length of the resulting bytes.
     *
     * @param src the byte array to encode
     * @return A newly-allocated byte array containing the resulting encoded bytes.
     */
    public byte[] encode(byte[] src) {
        return encodeToString(src).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Encodes all bytes from the specified byte array using the {@link Base32768} encoding scheme,
     * writing the resulting bytes to the given output byte array, starting at offset 0.<br>
     * It is the responsibility of the invoker of this method to make sure the output byte array dst has enough space
     * for encoding all bytes from the input byte array. No bytes will be written to the output byte array if the output
     * byte array is not big enough.
     *
     * @param src the byte array to encode
     * @param dst the output byte array
     * @return The number of bytes written to the output byte array
     * @throws BufferTooSmallException if dst does not have enough space for encoding all input bytes.
     */
    public int encode(byte[] src, byte[] dst) {
        byte[] result = encode(src);
        if (dst.length < result.length) throw new BufferTooSmallException(result.length, dst.length);
        System.arraycopy(result, 0, dst, 0, result.length);
        return result.length;
    }

    /**
     * Encodes all remaining bytes from the specified byte buffer into a newly-allocated ByteBuffer using the
     * {@link Base32768} encoding scheme. Upon return, the source buffer's position will be updated to its limit;
     * its limit will not have been changed. The returned output buffer's position will be zero and its limit will be
     * the number of resulting encoded bytes.
     *
     * @param buffer the source ByteBuffer to encode
     * @return A newly-allocated byte buffer containing the encoded bytes.
     */
    public ByteBuffer encode(ByteBuffer buffer) {
        byte[] src = new byte[buffer.remaining()];
        buffer.get(src);
        return ByteBuffer.wrap(encode(src));
    }

    /**
     * Encodes the specified byte array into a String using the {@link Base32768} encoding scheme.<br>
     *
     * @param src the byte array to encode
     * @return A string containing the resulting Base32768 encoded characters.
     */
    public String encodeToString(byte[] src) {
        if (src.length == 0) return "";

        final char[] lut15 = CODES15_CHAR;
        final char[] lut7 = CODES7_CHAR;

        final int srcLen = src.length;
        final int outLen = (int) (((srcLen * 8L) + 14L) / 15);
        final char[] out = new char[outLen];
        int oi = 0;
        int i = 0;

        // Fast Path: 15バイト -> 8文字
        final int fastLimit = srcLen - 14;
        while (i < fastLimit) {
            long hi = (long) VH_LONG_BE.get(src, i);
            long lo = (long) VH_LONG_BE.get(src, i + 7);

            out[oi] = lut15[(int) (hi >>> 49)];
            out[oi + 1] = lut15[(int) (hi >>> 34) & 0x7FFF];
            out[oi + 2] = lut15[(int) (hi >>> 19) & 0x7FFF];
            out[oi + 3] = lut15[(int) (hi >>> 4) & 0x7FFF];
            out[oi + 4] = lut15[(int) (((hi & 0xFL) << 11) | ((lo >>> 45) & 0x7FFL))];
            out[oi + 5] = lut15[(int) (lo >>> 30) & 0x7FFF];
            out[oi + 6] = lut15[(int) (lo >>> 15) & 0x7FFF];
            out[oi + 7] = lut15[(int) lo & 0x7FFF];

            i += 15;
            oi += 8;
        }

        // 残りバイトの処理
        long acc = 0L;
        int bitCount = 0;

        while (i < srcLen) {
            acc = (acc << 8) | (src[i++] & 0xFFL);
            bitCount += 8;

            if (bitCount >= 15) {
                bitCount -= 15;
                out[oi++] = lut15[(int) ((acc >>> bitCount) & 0x7FFF)];
                acc &= (1L << bitCount) - 1L;
            }
        }

        // 端数処理
        if (bitCount >= 8) {
            int v = (int) (acc << (15 - bitCount));
            v |= 0x7F >>> (bitCount - 8);
            out[oi++] = lut15[v];
        } else if (bitCount > 0) {
            int v = (int) (acc << (7 - bitCount));
            v |= 0x3F >>> (bitCount - 1);
            out[oi++] = lut7[v];
        }

        return new String(out);
    }

    /**
     * Not yet implemented.
     *
     * @param os Not yet implemented.
     * @return Not yet implemented.
     * @throws UnsupportedOperationException Always throw this because not yet implemented.
     */
    public OutputStream wrap(OutputStream os) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
