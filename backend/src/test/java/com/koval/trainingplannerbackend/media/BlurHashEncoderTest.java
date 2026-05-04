package com.koval.trainingplannerbackend.media;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class BlurHashEncoderTest {

    private static final String CHARACTERS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~";

    private static int[] solidColor(int width, int height, int rgb) {
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) pixels[i] = rgb;
        return pixels;
    }

    private static BufferedImage solidImage(int width, int height, int rgb) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, rgb);
            }
        }
        return img;
    }

    @Nested
    class Encoding {

        @Test
        void solidBlack_producesValidBase83Hash() {
            int[] pixels = solidColor(8, 8, 0x000000);
            String hash = BlurHashEncoder.encode(pixels, 8, 8, 4, 4);

            assertNotNull(hash);
            assertFalse(hash.isEmpty());
            // All characters must be in the BlurHash base-83 alphabet
            for (char c : hash.toCharArray()) {
                assertTrue(CHARACTERS.indexOf(c) >= 0,
                        "Hash contains non-base83 character: " + c);
            }
        }

        @Test
        void solidWhite_producesValidHash() {
            int[] pixels = solidColor(4, 4, 0xFFFFFF);
            String hash = BlurHashEncoder.encode(pixels, 4, 4, 3, 3);
            assertNotNull(hash);
            assertFalse(hash.isEmpty());
        }

        @Test
        void differentColors_produceDifferentHashes() {
            int[] red = solidColor(4, 4, 0xFF0000);
            int[] blue = solidColor(4, 4, 0x0000FF);

            String redHash = BlurHashEncoder.encode(red, 4, 4, 4, 4);
            String blueHash = BlurHashEncoder.encode(blue, 4, 4, 4, 4);

            assertNotEquals(redHash, blueHash);
        }

        @Test
        void deterministic_sameInputProducesSameHash() {
            int[] pixels = solidColor(8, 8, 0x123456);
            String hash1 = BlurHashEncoder.encode(pixels, 8, 8, 4, 3);
            String hash2 = BlurHashEncoder.encode(pixels, 8, 8, 4, 3);
            assertEquals(hash1, hash2);
        }

        @Test
        void bufferedImageOverload_matchesArrayOverload() {
            BufferedImage img = solidImage(6, 6, 0x336699);
            int[] pixels = img.getRGB(0, 0, 6, 6, null, 0, 6);

            String hashFromImage = BlurHashEncoder.encode(img, 4, 4);
            String hashFromArray = BlurHashEncoder.encode(pixels, 6, 6, 4, 4);

            assertEquals(hashFromImage, hashFromArray);
        }
    }

    @Nested
    class HashLength {

        @ParameterizedTest(name = "components {0}x{1} → expected length {2}")
        @CsvSource({
                "1, 1, 6",   // 1+1+4 = 6 (no AC components)
                "3, 3, 22",  // 1+1+4 + 8*2 = 22
                "4, 3, 28",  // 1+1+4 + 11*2 = 28
                "4, 4, 36",  // 1+1+4 + 15*2 = 36
                "9, 9, 166", // 1+1+4 + 80*2 = 166
        })
        void encodedHashHasExpectedLength(int cx, int cy, int expectedLength) {
            int[] pixels = solidColor(8, 8, 0x808080);
            String hash = BlurHashEncoder.encode(pixels, 8, 8, cx, cy);
            assertEquals(expectedLength, hash.length(),
                    "Hash for components %dx%d should be %d chars".formatted(cx, cy, expectedLength));
        }
    }

    @Nested
    class InvalidInputs {

        @Test
        void componentsBelowOne_throws() {
            int[] pixels = solidColor(4, 4, 0);
            assertThrows(IllegalArgumentException.class,
                    () -> BlurHashEncoder.encode(pixels, 4, 4, 0, 4));
            assertThrows(IllegalArgumentException.class,
                    () -> BlurHashEncoder.encode(pixels, 4, 4, 4, 0));
        }

        @Test
        void componentsAboveNine_throws() {
            int[] pixels = solidColor(4, 4, 0);
            assertThrows(IllegalArgumentException.class,
                    () -> BlurHashEncoder.encode(pixels, 4, 4, 10, 4));
            assertThrows(IllegalArgumentException.class,
                    () -> BlurHashEncoder.encode(pixels, 4, 4, 4, 10));
        }

        @Test
        void pixelArrayTooSmall_throws() {
            int[] tooSmall = new int[5];
            assertThrows(IllegalArgumentException.class,
                    () -> BlurHashEncoder.encode(tooSmall, 4, 4, 3, 3));
        }
    }
}
