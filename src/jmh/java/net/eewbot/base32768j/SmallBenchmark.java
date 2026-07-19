package net.eewbot.base32768j;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

/**
 * 小入力に特化したベンチマーク。scalar (アキュムレータのみ) / bulk (固定ブロック) / dispatched (公開API)
 * を個別に測り、経路切り替え閾値の逆転点を直接観測する。
 * エンコードの15バイト境界 (14/15/16, 29/30/31, ...) と、そこから誘導されるデコードの
 * 文字数境界 (8/9, 16/17, 24/25, 32/33文字) をカバーする。
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SmallBenchmark {
    private static final Base32768Encoder encoder = Base32768.getEncoder();
    private static final Base32768Decoder decoder = Base32768.getDecoder();

    // size=0 は公開APIの早期リターンで完結し、scalar/bulk 直呼びは空文字列を処理できないため対象外
    @Param({
        "1", "2", "3",
        "7", "8", "9",
        "14", "15", "16",
        "29", "30", "31",
        "44", "45", "46",
        "60", "61", "62",
        "64", "128", "256"
    })
    public int size;

    @Param({"789"})
    public long seed;

    private byte[] inputArrayEnc;
    private String encodedStringDec;

    @Setup(Level.Trial)
    public void setup() {
        inputArrayEnc = new byte[size];
        fillRandom(inputArrayEnc, seed ^ 0x9E3779B97F4A7C15L);

        byte[] inputArrayDec = new byte[size];
        fillRandom(inputArrayDec, seed);
        encodedStringDec = encoder.encodeToString(inputArrayDec);
    }

    private static void fillRandom(byte[] dst, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        int i = 0;
        int n = dst.length;

        while (i + 4 <= n) {
            int x = r.nextInt();
            dst[i] = (byte) x;
            dst[i + 1] = (byte) (x >>> 8);
            dst[i + 2] = (byte) (x >>> 16);
            dst[i + 3] = (byte) (x >>> 24);
            i += 4;
        }
        if (i < n) {
            int x = r.nextInt();
            for (; i < n; i++) {
                dst[i] = (byte) x;
                x >>>= 8;
            }
        }
    }

    @org.openjdk.jmh.annotations.Benchmark
    public void encodeScalar(Blackhole bh) {
        bh.consume(encoder.encodeScalar(inputArrayEnc));
    }

    @org.openjdk.jmh.annotations.Benchmark
    public void encodeBulk(Blackhole bh) {
        bh.consume(encoder.encodeBulk(inputArrayEnc));
    }

    @org.openjdk.jmh.annotations.Benchmark
    public void encodeDispatched(Blackhole bh) {
        bh.consume(encoder.encodeToString(inputArrayEnc));
    }

    @org.openjdk.jmh.annotations.Benchmark
    public void decodeScalar(Blackhole bh) {
        bh.consume(decoder.decodeScalar(encodedStringDec));
    }

    @org.openjdk.jmh.annotations.Benchmark
    public void decodeBulk(Blackhole bh) {
        bh.consume(decoder.decodeBulk(encodedStringDec));
    }

    @org.openjdk.jmh.annotations.Benchmark
    public void decodeDispatched(Blackhole bh) {
        bh.consume(decoder.decode(encodedStringDec));
    }
}
