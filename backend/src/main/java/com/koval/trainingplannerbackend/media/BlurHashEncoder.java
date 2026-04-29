package com.koval.trainingplannerbackend.media;

import java.awt.image.BufferedImage;

/**
 * Minimal pure-Java BlurHash encoder. Computes a compact (~30 char) string
 * that represents a blurred preview of an image, suitable for use as a CSS
 * background while the real image is loading.
 *
 * Algorithm reference and original C/JS implementations:
 *   https://github.com/woltapp/blurhash (MIT licensed).
 *
 * This implementation only encodes; the frontend handles decoding via the
 * `blurhash` npm package.
 */
public final class BlurHashEncoder {

    private static final String CHARACTERS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~";

    private BlurHashEncoder() {}

    /**
     * Encode a {@link BufferedImage} into a BlurHash string. For best results
     * pass a small (≤ 100px on a side) downsampled version — the algorithm is
     * O(width × height × componentsX × componentsY).
     */
    public static String encode(BufferedImage image, int componentsX, int componentsY) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
        return encode(argb, width, height, componentsX, componentsY);
    }

    public static String encode(int[] argb, int width, int height, int componentsX, int componentsY) {
        if (componentsX < 1 || componentsX > 9 || componentsY < 1 || componentsY > 9) {
            throw new IllegalArgumentException("Components must be in [1, 9]");
        }
        if (argb.length < width * height) {
            throw new IllegalArgumentException("Pixel array too small for given dimensions");
        }

        double[][] factors = new double[componentsX * componentsY][3];
        for (int j = 0; j < componentsY; j++) {
            for (int i = 0; i < componentsX; i++) {
                double normalisation = (i == 0 && j == 0) ? 1.0 : 2.0;
                double[] factor = new double[3];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        double basis = normalisation
                                * Math.cos(Math.PI * i * x / width)
                                * Math.cos(Math.PI * j * y / height);
                        int pixel = argb[y * width + x];
                        int r = (pixel >> 16) & 0xFF;
                        int g = (pixel >> 8) & 0xFF;
                        int b = pixel & 0xFF;
                        factor[0] += basis * sRGBToLinear(r);
                        factor[1] += basis * sRGBToLinear(g);
                        factor[2] += basis * sRGBToLinear(b);
                    }
                }
                double scale = 1.0 / (width * height);
                factor[0] *= scale;
                factor[1] *= scale;
                factor[2] *= scale;
                factors[j * componentsX + i] = factor;
            }
        }

        StringBuilder hash = new StringBuilder();
        int sizeFlag = (componentsX - 1) + (componentsY - 1) * 9;
        appendBase83(hash, sizeFlag, 1);

        double maximumValue;
        if (factors.length > 1) {
            double actualMax = 0;
            for (int i = 1; i < factors.length; i++) {
                actualMax = Math.max(actualMax, Math.max(
                        Math.abs(factors[i][0]),
                        Math.max(Math.abs(factors[i][1]), Math.abs(factors[i][2]))));
            }
            int quantisedMax = (int) Math.max(0, Math.min(82, Math.floor(actualMax * 166 - 0.5)));
            maximumValue = (quantisedMax + 1) / 166.0;
            appendBase83(hash, quantisedMax, 1);
        } else {
            maximumValue = 1;
            appendBase83(hash, 0, 1);
        }

        appendBase83(hash, encodeDC(factors[0]), 4);
        for (int i = 1; i < factors.length; i++) {
            appendBase83(hash, encodeAC(factors[i], maximumValue), 2);
        }
        return hash.toString();
    }

    private static int encodeDC(double[] value) {
        int r = linearToSRGB(value[0]);
        int g = linearToSRGB(value[1]);
        int b = linearToSRGB(value[2]);
        return (r << 16) | (g << 8) | b;
    }

    private static int encodeAC(double[] value, double maximumValue) {
        long quantR = clamp(Math.floor(signPow(value[0] / maximumValue, 0.5) * 9 + 9.5));
        long quantG = clamp(Math.floor(signPow(value[1] / maximumValue, 0.5) * 9 + 9.5));
        long quantB = clamp(Math.floor(signPow(value[2] / maximumValue, 0.5) * 9 + 9.5));
        return (int) (quantR * 19 * 19 + quantG * 19 + quantB);
    }

    private static long clamp(double v) {
        return (long) Math.max(0, Math.min(18, v));
    }

    private static double signPow(double base, double exp) {
        return Math.copySign(Math.pow(Math.abs(base), exp), base);
    }

    private static double sRGBToLinear(int value) {
        double v = value / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static int linearToSRGB(double value) {
        double v = Math.max(0, Math.min(1, value));
        return v <= 0.0031308
                ? (int) (v * 12.92 * 255 + 0.5)
                : (int) ((1.055 * Math.pow(v, 1.0 / 2.4) - 0.055) * 255 + 0.5);
    }

    private static void appendBase83(StringBuilder out, int value, int length) {
        for (int i = 1; i <= length; i++) {
            int digit = (value / (int) Math.pow(83, length - i)) % 83;
            out.append(CHARACTERS.charAt(digit));
        }
    }
}
