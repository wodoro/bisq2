/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.webcam.service.capture;

import bisq.webcam.service.converter.FrameToBitmapConverter;
import bisq.webcam.service.processor.QrCodeProcessor;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Compares QR-detection effectiveness between the WinRT capture backend and the OpenCV backend, by running the
 * <em>real</em> decode pipeline ({@link FrameToBitmapConverter} + ZXing via {@link QrCodeProcessor}) on the frames each
 * backend produces.
 *
 * <p>The decode stage is identical for both backends - they both yield a 3-channel BGR {@link Frame} fed to the same
 * ZXing reader - so any difference in decode rate reflects frame <em>quality</em> (resolution, focus, exposure,
 * sharpness, colour fidelity), not the decode algorithm. Point both backends at the same QR code and compare.
 *
 * <p>Run bare on the desktop (both backends work there); inside the MSIX AppContainer only {@code winrt} works. Output
 * is line-oriented {@code key=value}. Exit codes: {@code 0} ran (see {@code decoded_count}), {@code 20} capture open
 * failed, {@code 2} bad arguments.
 */
public final class QrDecodeProbe {
    private QrDecodeProbe() {
    }

    public static void main(String[] args) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.out.println("result=bad_arguments");
            System.out.println("error=" + e.getMessage());
            System.exit(2);
            return;
        }

        System.out.println("backend=" + options.backend);
        System.out.println("device=" + options.device);
        System.out.println("requested=" + options.width + "x" + options.height);
        System.out.println("frames=" + options.frames);

        FrameGrabber grabber = createGrabber(options);
        grabber.setImageWidth(options.width);
        grabber.setImageHeight(options.height);

        try {
            grabber.start();
        } catch (Exception e) {
            System.out.println("open_result=false");
            System.out.println("error=" + e);
            System.out.println("result=capture_open_failed");
            System.exit(20);
            return;
        }
        System.out.println("open_result=true");
        System.out.println("negotiated=" + grabber.getImageWidth() + "x" + grabber.getImageHeight());

        QrCodeProcessor processor = new QrCodeProcessor(new FrameToBitmapConverter());

        int decodedCount = 0;
        long totalDecodeNanos = 0;
        long totalMeanByte = 0;
        int processed = 0;
        String firstPayload = null;

        try {
            for (int frameIndex = 0; frameIndex < options.frames; frameIndex++) {
                Frame frame = grabber.grab();
                if (frame == null) {
                    System.out.println("grab[" + frameIndex + "]=null");
                    continue;
                }
                long meanByte = meanByte(frame);
                long startNanos = System.nanoTime();
                Optional<String> qrCode = processor.process(frame);
                long decodeNanos = System.nanoTime() - startNanos;

                processed++;
                totalDecodeNanos += decodeNanos;
                totalMeanByte += meanByte;
                if (qrCode.isPresent()) {
                    decodedCount++;
                    if (firstPayload == null) {
                        firstPayload = qrCode.get();
                    }
                }
                System.out.println("frame[" + frameIndex + "] meanByte=" + meanByte
                        + " decoded=" + qrCode.isPresent()
                        + " decodeMs=" + (decodeNanos / 1_000_000.0));
            }
        } catch (Exception e) {
            System.out.println("grab_error=" + e);
        } finally {
            try {
                grabber.release();
            } catch (Exception ignore) {
                // Best effort.
            }
        }

        double decodeRate = processed == 0 ? 0.0 : (double) decodedCount / processed;
        double avgDecodeMs = processed == 0 ? 0.0 : totalDecodeNanos / 1_000_000.0 / processed;
        long avgMeanByte = processed == 0 ? 0 : totalMeanByte / processed;

        System.out.println("processed_count=" + processed);
        System.out.println("decoded_count=" + decodedCount);
        System.out.println("decode_rate=" + String.format("%.3f", decodeRate));
        System.out.println("avg_decode_ms=" + String.format("%.2f", avgDecodeMs));
        System.out.println("avg_mean_byte=" + avgMeanByte);
        System.out.println("first_payload=" + (firstPayload == null ? "" : firstPayload));
        System.out.println("result=done");
    }

    private static FrameGrabber createGrabber(Options options) {
        return switch (options.backend) {
            case "winrt" -> new WinRtFrameGrabber(options.device);
            case "opencv" -> new OpenCVFrameGrabber(options.device);
            default -> throw new IllegalArgumentException("Unsupported backend: " + options.backend);
        };
    }

    private static long meanByte(Frame frame) {
        ByteBuffer buffer = (ByteBuffer) frame.image[0];
        buffer.clear();
        int capacity = buffer.capacity();
        if (capacity == 0) {
            return 0;
        }
        int step = Math.max(1, capacity / 4096);
        long sum = 0;
        long count = 0;
        for (int index = 0; index < capacity; index += step) {
            sum += buffer.get(index) & 0xFF;
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private record Options(String backend, int device, int width, int height, int frames) {
        private static Options parse(String[] args) {
            String backend = "winrt";
            int device = 0;
            int width = 640;
            int height = 480;
            int frames = 60;

            for (int index = 0; index < args.length; index++) {
                switch (args[index]) {
                    case "--backend" -> backend = parseString(args, ++index, "--backend");
                    case "--device" -> device = parseInt(args, ++index, "--device");
                    case "--width" -> width = parseInt(args, ++index, "--width");
                    case "--height" -> height = parseInt(args, ++index, "--height");
                    case "--frames" -> frames = parseInt(args, ++index, "--frames");
                    default -> throw new IllegalArgumentException("Unsupported argument: " + args[index]);
                }
            }
            if (!backend.equals("winrt") && !backend.equals("opencv")) {
                throw new IllegalArgumentException("--backend must be winrt or opencv");
            }
            if (frames < 1) {
                throw new IllegalArgumentException("--frames must be at least 1");
            }
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException("--width and --height must be positive");
            }
            return new Options(backend, device, width, height, frames);
        }

        private static int parseInt(String[] args, int index, String name) {
            return Integer.parseInt(parseString(args, index, name));
        }

        private static String parseString(String[] args, int index, String name) {
            if (index >= args.length) {
                throw new IllegalArgumentException(name + " requires a value");
            }
            return args[index];
        }
    }
}
