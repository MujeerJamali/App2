package com.mujeer.floatingblocker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Opens the camera, continuously decodes whatever barcode/QR code it sees,
 * and returns the decoded text via activity result. Two modes, both using
 * the exact same scan loop:
 *
 * - Registration mode (no pair extras passed): a scan must be confirmed by
 *   scanning the SAME code again - each of the two scans shows the decoded
 *   number and asks for confirmation before moving on. Used when adding a
 *   new RegisteredBarcode. Guards against a misread getting silently
 *   registered as a Barcode's permanent value.
 * - Pair-dismiss mode (EXTRA_PAIR_VALUES_A/EXTRA_PAIR_VALUES_B passed, two
 *   parallel arrays - index i is one pair): the two barcodes of any one
 *   pair must both be scanned, in either order, within
 *   PAIR_SCAN_WINDOW_MILLIS of each other - scanning only one, scanning an
 *   unrelated code, or letting the window lapse resets and requires
 *   starting that pair over.
 */
public class BarcodeScanActivity extends Activity implements SurfaceHolder.Callback {

    public static final String EXTRA_PAIR_VALUES_A = "pair_values_a";
    public static final String EXTRA_PAIR_VALUES_B = "pair_values_b";
    public static final String EXTRA_DECODED_VALUE = "decoded_value";

    private static final int REQUEST_CAMERA_PERMISSION = 501;
    private static final long PAIR_SCAN_WINDOW_MILLIS = 3000L;

    private SurfaceView surfaceView;
    private TextView txtStatus;
    private Button btnFlashlight;
    private Camera camera;
    private final MultiFormatReader reader = new MultiFormatReader();
    private volatile boolean decodeInFlight = false;
    private boolean torchOn = false;

    private String[] pairValuesA;
    private String[] pairValuesB;

    // Registration mode: the first scan, once the user has confirmed it -
    // null until then. A second scan is only accepted as a confirming
    // rescan once this is set.
    private String firstConfirmedValue;
    // True while a confirmation AlertDialog is up, in registration mode -
    // new decodes are ignored until it's answered, so a frame decoded
    // while the dialog is showing can't silently double-fire it.
    private volatile boolean dialogShowing = false;

    // Pair-dismiss mode: the first-of-a-pair scan awaiting its partner.
    private String pendingFirstValue;
    private long pendingFirstScanTime;
    private final Handler pairWindowHandler = new Handler(Looper.getMainLooper());
    private final Runnable pairWindowTick = new Runnable() {
        @Override
        public void run() {
            if (pendingFirstValue == null) {
                return;
            }
            long remaining = PAIR_SCAN_WINDOW_MILLIS - (System.currentTimeMillis() - pendingFirstScanTime);
            if (remaining <= 0) {
                pendingFirstValue = null;
                txtStatus.setText(R.string.barcode_pair_scan_expired);
                return;
            }
            long secondsLeft = (remaining + 999) / 1000;
            txtStatus.setText(getString(R.string.barcode_pair_scan_waiting, secondsLeft));
            pairWindowHandler.postDelayed(this, 200);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TRY_HARDER makes ZXing's readers do a more thorough scan instead
        // of the fast/light default pass - real phone-camera shots of a
        // barcode held at even a modest angle or slightly blurred tend to
        // fail the fast path. Worth the extra per-frame CPU time here since
        // only one decode runs at a time, on a background thread.
        Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);

        pairValuesA = getIntent().getStringArrayExtra(EXTRA_PAIR_VALUES_A);
        pairValuesB = getIntent().getStringArrayExtra(EXTRA_PAIR_VALUES_B);

        FrameLayout root = new FrameLayout(this);
        surfaceView = new SurfaceView(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        txtStatus = new TextView(this);
        txtStatus.setText(isPairMode()
                ? R.string.barcode_scan_pair_instructions
                : R.string.barcode_scan_register_instructions);
        txtStatus.setTextColor(Color.WHITE);
        txtStatus.setBackgroundColor(Color.parseColor("#AA000000"));
        txtStatus.setPadding(24, 24, 24, 24);
        txtStatus.setTextSize(16f);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.gravity = Gravity.BOTTOM;
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
    }

    private boolean isPairMode() {
        return pairValuesA != null && pairValuesB != null;
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
            Toast.makeText(this, R.string.msg_camera_unavailable, Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
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
        if (decodeInFlight) {
            return;
        }
        decodeInFlight = true;
        final Camera.Size size = cam.getParameters().getPreviewSize();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (data.length < size.width * size.height) {
                        return;
                    }
                    // setDisplayOrientation(90) only rotates what's shown on
                    // screen - the raw preview buffer camera hands us is
                    // still in the sensor's native (landscape) orientation.
                    // Rotate it to match what's actually on screen, or
                    // decoding is effectively scanning a sideways image.
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
                    // Normal - no code in this frame.
                } catch (Exception e) {
                    Log.e("BarcodeScanActivity", "Decode error", e);
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
            // not the full-resolution row range.
            for (int y = height / 2 - 1; y >= 0; y--) {
                rotated[i++] = data[frameSize + (y * width) + x];
                rotated[i++] = data[frameSize + (y * width) + (x + 1)];
            }
        }
        return rotated;
    }

    private void onDecoded(String value) {
        if (isFinishing() || dialogShowing) {
            return;
        }
        if (isPairMode()) {
            handlePairDecode(value);
        } else {
            handleRegistrationDecode(value);
        }
    }

    private void handleRegistrationDecode(final String value) {
        if (firstConfirmedValue == null) {
            dialogShowing = true;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.barcode_confirm_title)
                    .setMessage(getString(R.string.barcode_confirm_first_message, value))
                    .setPositiveButton(R.string.barcode_confirm_scan_again_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            firstConfirmedValue = value;
                            dialogShowing = false;
                            txtStatus.setText(getString(R.string.barcode_scan_confirm_instructions, value));
                        }
                    })
                    .setNegativeButton(R.string.cancel_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialogShowing = false;
                        }
                    })
                    .setCancelable(false)
                    .show();
            return;
        }

        if (!value.equals(firstConfirmedValue)) {
            Toast.makeText(this, getString(R.string.msg_barcode_confirm_mismatch, firstConfirmedValue, value), Toast.LENGTH_LONG).show();
            firstConfirmedValue = null;
            txtStatus.setText(R.string.barcode_scan_register_instructions);
            return;
        }

        dialogShowing = true;
        new AlertDialog.Builder(this)
                .setTitle(R.string.barcode_confirm_title)
                .setMessage(getString(R.string.barcode_confirm_second_message, value))
                .setPositiveButton(R.string.confirm_block_website_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent result = new Intent();
                        result.putExtra(EXTRA_DECODED_VALUE, value);
                        setResult(RESULT_OK, result);
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialogShowing = false;
                        firstConfirmedValue = null;
                        txtStatus.setText(R.string.barcode_scan_register_instructions);
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void handlePairDecode(String value) {
        long now = System.currentTimeMillis();
        if (pendingFirstValue != null && (now - pendingFirstScanTime) > PAIR_SCAN_WINDOW_MILLIS) {
            pendingFirstValue = null;
        }

        if (pendingFirstValue == null) {
            if (!isPairMember(value)) {
                txtStatus.setText(R.string.barcode_scan_wrong_code);
                return;
            }
            startPendingPairScan(value, now);
            return;
        }

        if (value.equals(pendingFirstValue)) {
            // Same code seen again while still holding it up to the camera - ignore, keep waiting.
            return;
        }

        if (pairMatches(pendingFirstValue, value)) {
            pendingFirstValue = null;
            pairWindowHandler.removeCallbacks(pairWindowTick);
            Intent result = new Intent();
            result.putExtra(EXTRA_DECODED_VALUE, value);
            setResult(RESULT_OK, result);
            finish();
            return;
        }

        // Didn't complete the pending pair - reset, then let this scan
        // start a fresh pending window if it's itself a valid pair member.
        pendingFirstValue = null;
        if (isPairMember(value)) {
            startPendingPairScan(value, now);
        } else {
            txtStatus.setText(R.string.barcode_scan_wrong_code);
        }
    }

    private void startPendingPairScan(String value, long now) {
        pendingFirstValue = value;
        pendingFirstScanTime = now;
        pairWindowHandler.removeCallbacks(pairWindowTick);
        pairWindowHandler.post(pairWindowTick);
    }

    private boolean isPairMember(String value) {
        for (int i = 0; i < pairValuesA.length; i++) {
            if (pairValuesA[i].equals(value) || pairValuesB[i].equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean pairMatches(String v1, String v2) {
        for (int i = 0; i < pairValuesA.length; i++) {
            if ((pairValuesA[i].equals(v1) && pairValuesB[i].equals(v2))
                    || (pairValuesB[i].equals(v1) && pairValuesA[i].equals(v2))) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCamera();
        pairWindowHandler.removeCallbacks(pairWindowTick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camera == null && surfaceView.getHolder().getSurface() != null
                && surfaceView.getHolder().getSurface().isValid()
                && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }
}
