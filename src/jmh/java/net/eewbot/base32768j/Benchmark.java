package net.eewbot.base32768j;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class Benchmark {
    private static final Base32768Encoder encoder = Base32768.getEncoder();
//    private static final Base32768Decoder decoder = Base32768.getDecoder();
//    private static byte[] oneByteArray;
    private static byte[] tenKilobytesArray;
    private static byte[] oneMegabytesArray;
//    private static String oneByteString;
//    private static String tenKilobytesString;
//    private static String oneMegabytesString;

    @Param({"32768"})
    public long seed;

    @Setup(Level.Trial)
    public void setup() {
//        oneByteArray = new byte[]{127};
        tenKilobytesArray = new byte[10_000];
        oneMegabytesArray = new byte[1_000_000];
        fillRandom(tenKilobytesArray, seed ^ 0x9E3779B97F4A7C15L);
        fillRandom(oneMegabytesArray, seed);
//        oneByteString = encoder.encodeToString(oneByteArray);
//        tenKilobytesString = encoder.encodeToString(tenKilobytesArray);
//        oneMegabytesString = encoder.encodeToString(oneMegabytesArray);
    }

    private static void fillRandom(byte[] dst, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        int i = 0;
        int n = dst.length;

        while (i + 4 <= n) {
            int x = r.nextInt();
            dst[i]     = (byte) x;
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

//    @org.openjdk.jmh.annotations.Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @OutputTimeUnit(TimeUnit.SECONDS)
//    public void encoderOneByte(Blackhole blackhole) {
//        blackhole.consume(encoder.encodeToString(oneByteArray));
//    }

    @org.openjdk.jmh.annotations.Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void encoderTenKilobytes(Blackhole blackhole) {
        blackhole.consume(encoder.encodeToString(tenKilobytesArray));
    }

    @org.openjdk.jmh.annotations.Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void encoderOneMegabytes(Blackhole blackhole) {
        blackhole.consume(encoder.encodeToString(oneMegabytesArray));
    }

//    @org.openjdk.jmh.annotations.Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @OutputTimeUnit(TimeUnit.SECONDS)
//    public void decoderOneByte(Blackhole blackhole) {
//        blackhole.consume(decoder.decode(oneByteString));
//    }
//
//    @org.openjdk.jmh.annotations.Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @OutputTimeUnit(TimeUnit.SECONDS)
//    public void decoderTenKilobytes(Blackhole blackhole) {
//        blackhole.consume(decoder.decode(tenKilobytesString));
//    }
//
//    @org.openjdk.jmh.annotations.Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @OutputTimeUnit(TimeUnit.SECONDS)
//    public void decoderOneMegabytes(Blackhole blackhole) {
//        blackhole.consume(decoder.decode(oneMegabytesString));
//    }
}
