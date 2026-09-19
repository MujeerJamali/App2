package com.mujeer.floatingblocker;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Opens the camera, continuously decodes whatever barcode/QR code it sees,
 * and returns the decoded text via activity result. Two modes, both using
 * the exact same scan loop:
 *
 * - Registration mode (no EXTRA_ACCEPTED_VALUES passed): the very first
 *   successfully decoded code is returned immediately. Used when adding a
 *   new RegisteredBarcode.
 * - Dismiss mode (EXTRA_ACCEPTED_VALUES passed, one or more values): a
 *   decode only finishes this activity if it matches one of those exact
 *   values - anything else shows "wrong code, keep scanning" and the
 *   camera keeps running. This is what makes barcode-dismiss actually
 *   mean THIS specific code, not just any code.
 */
public class BarcodeScanActivity extends Activity implements SurfaceHolder.Callback {

    public static final String EXTRA_ACCEPTED_VALUES = "accepted_values";
    public static final String EXTRA_DECODED_VALUE = "decoded_value";

    private static final int REQUEST_CAMERA_PERMISSION = 501;

    private SurfaceView surfaceView;
    private TextView txtStatus;
    private Button btnFlashlight;
    private Camera camera;
    private final MultiFormatReader reader = new MultiFormatReader();
    private volatile boolean decodeInFlight = false;
    private boolean torchOn = false;
    private Set<String> acceptedValues;

    // Temporary on-screen diagnostics - this app deliberately avoids relying on
    // logcat (see DiagnosticActivity's original design note: not practically
    // accessible on a non-rooted device), so when something like "camera shows
    // but never decodes" needs debugging, the counters/last-error need to be
    // visible directly on screen instead.
    private final Handler diagnosticHandler = new Handler(Looper.getMainLooper());
    private volatile long framesReceived = 0;
    private volatile long decodeAttempts = 0;
    private volatile String lastDecodeError = "(none)";
    private volatile String previewSizeText = "(camera not open yet)";
    private final Runnable diagnosticTick = new Runnable() {
        @Override
        public void run() {
            txtStatus.setText("Preview size: " + previewSizeText
                    + "\nFrames received: " + framesReceived
                    + "\nDecode attempts: " + decodeAttempts
                    + "\nLast decode error: " + lastDecodeError);
            diagnosticHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String[] acceptedArr = getIntent().getStringArrayExtra(EXTRA_ACCEPTED_VALUES);
        if (acceptedArr != null) {
            acceptedValues = new HashSet<String>();
            for (String v : acceptedArr) acceptedValues.add(v);
        }

        FrameLayout root = new FrameLayout(this);
        surfaceView = new SurfaceView(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        txtStatus = new TextView(this);
        txtStatus.setText(acceptedValues != null
                ? R.string.barcode_scan_dismiss_instructions
                : R.string.barcode_scan_register_instructions);
        txtStatus.setTextColor(Color.WHITE);
        txtStatus.setBackgroundColor(Color.parseColor("#AA000000"));
        txtStatus.setPadding(24, 24, 24, 24);
        txtStatus.setTextSize(16f);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.gravity = android.view.Gravity.BOTTOM;
        root.addView(txtStatus, statusParams);

        btnFlashlight = new Button(this);
        btnFlashlight.setText(R.string.flashlight_on_button);
        btnFlashlight.setVisibility(android.view.View.GONE); // shown only if the device actually supports a torch
        btnFlashlight.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                toggleFlashlight();
            }
        });
        FrameLayout.LayoutParams flashParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flashParams.gravity = Gravity.TOP | Gravity.END;
        flashParams.topMargin = 32;
        flashParams.rightMargin = 32;
        root.addView(btnFlashlight, flashParams);

        setContentView(root);
        surfaceView.getHolder().addCallback(this);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }

        diagnosticHandler.post(diagnosticTick);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.msg_camera_permission_required, Toast.LENGTH_LONG).show();
                setResult(RESULT_CANCELED);
                finish();
            } else if (surfaceView.getHolder().getSurface() != null && surfaceView.getHolder().getSurface().isValid()) {
                startCamera();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopCamera();
    }

    private void startCamera() {
        if (camera != null) {
            return;
        }
        try {
            camera = Camera.open();
            Camera.Parameters params = camera.getParameters();
            Camera.Size bestSize = pickPreviewSize(params.getSupportedPreviewSizes());
            if (bestSize != null) {
                params.setPreviewSize(bestSize.width, bestSize.height);
            }
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            List<String> flashModes = params.getSupportedFlashModes();
            boolean torchSupported = flashModes != null && flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH);
            btnFlashlight.setVisibility(torchSupported ? android.view.View.VISIBLE : android.view.View.GONE);
            torchOn = false;
            btnFlashlight.setText(R.string.flashlight_on_button);

            Camera.Size actualSize = params.getPreviewSize();
            previewSizeText = actualSize.width + "x" + actualSize.height
                    + " (format=" + params.getPreviewFormat() + ", ImageFormat.NV21=" + android.graphics.ImageFormat.NV21 + ")";

            camera.setParameters(params);
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(surfaceView.getHolder());
            camera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera cam) {
                    handlePreviewFrame(data, cam);
                }
            });
            camera.startPreview();
        } catch (Exception e) {
            Log.e("BarcodeScanActivity", "Could not start camera", e);
            // Deliberately NOT finishing here while diagnosing - staying on
            // screen with the error visible in previewSizeText is more
            // useful right now than an instant close the user can't read.
            previewSizeText = "FAILED TO OPEN: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            Toast.makeText(this, R.string.msg_camera_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void stopCamera() {
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            } catch (Exception e) {
                // Best effort - activity is going away regardless.
            }
            camera = null;
        }
        torchOn = false;
    }

    private void toggleFlashlight() {
        if (camera == null) {
            return;
        }
        try {
            Camera.Parameters params = camera.getParameters();
            torchOn = !torchOn;
            params.setFlashMode(torchOn ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(params);
            btnFlashlight.setText(torchOn ? R.string.flashlight_off_button : R.string.flashlight_on_button);
        } catch (Exception e) {
            Log.e("BarcodeScanActivity", "Could not toggle flashlight", e);
        }
    }

    private Camera.Size pickPreviewSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        Camera.Size best = sizes.get(0);
        for (Camera.Size s : sizes) {
            // Prefer something close to 640x480 - plenty of resolution for
            // decoding, without the cost of a needlessly huge frame.
            if (Math.abs(s.width - 640) < Math.abs(best.width - 640)) {
                best = s;
            }
        }
        return best;
    }

    private void handlePreviewFrame(final byte[] data, Camera cam) {
        framesReceived++;
        if (decodeInFlight) {
            return;
        }
        decodeInFlight = true;
        final Camera.Size size = cam.getParameters().getPreviewSize();
        new Thread(new Runnable() {
            @Override
            public void run() {
                decodeAttempts++;
                try {
                    if (data.length < size.width * size.height) {
                        lastDecodeError = "buffer too small: " + data.length + " bytes for " + size.width + "x" + size.height;
                        return;
                    }
                    // setDisplayOrientation(90) only rotates what's shown on
                    // screen - the raw preview buffer camera hands us is
                    // still in the sensor's native (landscape) orientation.
                    // Rotate it to match what's actually on screen, or
                    // decoding is effectively scanning a sideways image -
                    // survivable for a rotation-tolerant QR detector, but
                    // not for a real 1D barcode.
                    byte[] rotated = rotateNV21Clockwise90(data, size.width, size.height);
                    int rotatedWidth = size.height;
                    int rotatedHeight = size.width;
                    PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                            rotated, rotatedWidth, rotatedHeight, 0, 0, rotatedWidth, rotatedHeight, false);
                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                    final Result result = reader.decodeWithState(bitmap);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onDecoded(result.getText());
                        }
                    });
                } catch (NotFoundException e) {
                    lastDecodeError = "NotFoundException (normal - no code in frame)";
                } catch (Exception e) {
                    Log.e("BarcodeScanActivity", "Decode error", e);
                    lastDecodeError = e.getClass().getSimpleName() + ": " + e.getMessage();
                } finally {
                    reader.reset();
                    decodeInFlight = false;
                }
            }
        }).start();
    }

    /** Standard NV21 90-degree-clockwise rotation - matches setDisplayOrientation(90) above. Output is height x width. */
    private static byte[] rotateNV21Clockwise90(byte[] data, int width, int height) {
        byte[] rotated = new byte[data.length];
        int frameSize = width * height;

        int i = 0;
        for (int x = 0; x < width; x++) {
            for (int y = height - 1; y >= 0; y--) {
                rotated[i++] = data[y * width + x];
            }
        }
        i = frameSize;
        for (int x = 0; x < width; x += 2) {
            // The chroma (VU) plane only has height/2 rows, each still
            // `width` bytes wide (interleaved V/U pairs) - unlike the Y
            // plane above, y here must index into that half-height plane,
            // not the full-resolution row range. Using height-1..0 here
            // (matching the Y-plane loop) reads/writes far past the actual
            // chroma plane and throws ArrayIndexOutOfBoundsException on
            // every single frame - which is exactly what was happening.
            for (int y = height / 2 - 1; y >= 0; y--) {
                rotated[i++] = data[frameSize + (y * width) + x];
                rotated[i++] = data[frameSize + (y * width) + (x + 1)];
            }
        }
        return rotated;
    }

    private void onDecoded(String value) {
        if (isFinishing()) {
            return;
        }
        if (acceptedValues == null || acceptedValues.contains(value)) {
            Intent result = new Intent();
            result.putExtra(EXTRA_DECODED_VALUE, value);
            setResult(RESULT_OK, result);
            finish();
        } else {
            txtStatus.setText(R.string.barcode_scan_wrong_code);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCamera();
        diagnosticHandler.removeCallbacks(diagnosticTick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        diagnosticHandler.post(diagnosticTick);
        if (camera == null && surfaceView.getHolder().getSurface() != null
                && surfaceView.getHolder().getSurface().isValid()
                && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }
}
