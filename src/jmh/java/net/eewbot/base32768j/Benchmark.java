package net.eewbot.base32768j;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class Benchmark {
    private static final Base32768Encoder encoder = Base32768.getEncoder();
    private static final Base32768Decoder decoder = Base32768.getDecoder();

    @Param({
            "32", "64", "128", "256", "512",
            "1024", "2048", "4096", "8192",
            "16384", "32768", "65536", "131072",
            "262144",
            "524288",
//            "1000000"
            "1048576", "2097152",
            "4194304", "8388608"
    })
    public int size;

    @Param({"NONE", /* "FORCE_LAST7" */})
    public String tailMode;


    @Param({"789"})
    public long seed;

    private byte[] inputArrayEnc;
    private byte[] inputArrayDec;
    private String encodedStringDec;

    @Setup(Level.Trial)
    public void setup() {
        int n = size;
        if ("FORCE_LAST7".equals(tailMode)) {
            int r = n % 15;
            int delta = (1 - r + 15) % 15;
            n += delta;
        }

        inputArrayEnc = new byte[n];
        fillRandom(inputArrayEnc, seed ^ 0x9E3779B97F4A7C15L);

        inputArrayDec = new byte[n];
        fillRandom(inputArrayDec, seed);

        encodedStringDec = encoder.encodeToString(inputArrayDec);
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

    @org.openjdk.jmh.annotations.Benchmark
    public void encode(Blackhole bh) {
        bh.consume(encoder.encodeToString(inputArrayEnc));
    }

    @org.openjdk.jmh.annotations.Benchmark
    public void decode(Blackhole bh) {
        bh.consume(decoder.decode(encodedStringDec));
    }
}