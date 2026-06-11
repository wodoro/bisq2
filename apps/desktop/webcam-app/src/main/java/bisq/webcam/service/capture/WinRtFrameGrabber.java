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

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.nio.ByteBuffer;

/**
 * A JavaCV {@link FrameGrabber} backed by the Windows WinRT camera shim ({@link WinRtCamera}) instead of OpenCV's
 * {@code VideoCapture}/MSMF, which cannot open the camera inside the Windows AppContainer sandbox.
 *
 * <p>It produces the same 3-channel BGR {@link Frame} the OpenCV grabber produces on the other platforms, so the
 * downstream pipeline (preview rendering and ZXing QR decoding) is unchanged. The native shim writes each frame
 * directly into the {@link Frame}'s native image buffer, avoiding a copy across the JNI boundary.
 */
public class WinRtFrameGrabber extends FrameGrabber {
    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;
    // Bounds a single grab against a stalled/disconnected camera so the capture loop fails instead of hanging.
    private static final int GRAB_TIMEOUT_MILLIS = 5_000;

    private final int deviceIndex;
    private WinRtCamera camera;
    private Frame frame;

    public WinRtFrameGrabber(int deviceIndex) {
        this.deviceIndex = deviceIndex;
    }

    public static int countDevices() {
        return WinRtCamera.countDevices();
    }

    @Override
    public void start() throws Exception {
        if (camera != null) {
            return;
        }
        try {
            int requestedWidth = imageWidth > 0 ? imageWidth : DEFAULT_WIDTH;
            int requestedHeight = imageHeight > 0 ? imageHeight : DEFAULT_HEIGHT;
            camera = WinRtCamera.open(deviceIndex, requestedWidth, requestedHeight);
            // The camera may negotiate a different resolution than requested; adopt the actual one.
            imageWidth = camera.getWidth();
            imageHeight = camera.getHeight();
            frame = new Frame(imageWidth, imageHeight, Frame.DEPTH_UBYTE, 3);
        } catch (RuntimeException e) {
            close();
            throw new Exception("Starting WinRT frame grabber for device index " + deviceIndex + " failed", e);
        }
    }

    @Override
    public Frame grab() throws Exception {
        if (camera == null || frame == null) {
            throw new Exception("WinRT frame grabber is not started");
        }
        ByteBuffer buffer = (ByteBuffer) frame.image[0];
        buffer.clear();
        int result = camera.grab(buffer, GRAB_TIMEOUT_MILLIS);
        if (result != 0) {
            throw new Exception("WinRT frame grab failed with native error code " + result);
        }
        return frame;
    }

    @Override
    public void trigger() {
    }

    @Override
    public void stop() throws Exception {
        release();
    }

    @Override
    public void release() {
        if (camera != null) {
            camera.close();
            camera = null;
        }
        frame = null;
    }
}
