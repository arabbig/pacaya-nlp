package edu.jhu.util;

import org.junit.Test;

public class PrngTest {

    @Test
    public void testSpeed() {
        final int NUM_DOUBLES = 10000000;

        Timer timer;

        timer = new Timer();
        timer.start();
        for (int i = 0; i < NUM_DOUBLES; i++) {
            Prng.javaRandom.nextDouble();
        }
        timer.stop();
        System.out.println(timer.totMs());
        
        timer = new Timer();
        timer.start();
        for (int i = 0; i < NUM_DOUBLES; i++) {
            Prng.mt.nextDouble();
        }
        timer.stop();
        System.out.println(timer.totMs());
        
        timer = new Timer();
        timer.start();
        for (int i = 0; i < NUM_DOUBLES; i++) {
            Prng.mtf.nextDouble();
        }
        timer.stop();
        System.out.println(timer.totMs());

        timer = new Timer();
        timer.start();
        for (int i = 0; i < NUM_DOUBLES; i++) {
            Prng.mtColt.nextDouble();
        }
        timer.stop();
        System.out.println(timer.totMs());

        timer = new Timer();
        timer.start();
        for (int i = 0; i < NUM_DOUBLES; i++) {
            // Current fastest
            Prng.xorShift.nextDouble();
        }
        timer.stop();
        System.out.println(timer.totMs());
        
        timer = new Timer();
        timer.start();
        for (int i = 0; i < NUM_DOUBLES; i++) {
            Prng.mwc4096.nextDouble();
        }
        timer.stop();
        System.out.println(timer.totMs());
    }

}
