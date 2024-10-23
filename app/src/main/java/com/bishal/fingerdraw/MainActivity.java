package com.bishal.fingerdraw;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.DragEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private TextureView textureView;
    private HandOverlayView handOverlayView;
    private Hands hands;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private ImageReader imageReader;

    //recording work
    private MediaRecorder mediaRecorder;
    private String videoFilePath;
    private boolean isRecording = false;


    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService imageProcessingExecutor = Executors.newSingleThreadExecutor(); // For image processing

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d("MainActivity", "Permission Granted");
                    if (textureView.isAvailable()) {
                        startCamera();
                    }
                } else {
                    Log.d("MainActivity", "Permission Denied");
                    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        showMessageOKCancel();
                    } else {
                        Log.d("MainActivity", "Permission permanently denied");
                    }
                }
            });

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            startCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            stopCamera();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        textureView = findViewById(R.id.textureView);
        handOverlayView = findViewById(R.id.handOverlayView);
        Button clearButton = findViewById(R.id.clearButton);
        ImageButton pensil, color, erase;
        SeekBar pathSizeWidth;
        pensil = findViewById(R.id.pensil);
        color = findViewById(R.id.color);

        pathSizeWidth = findViewById(R.id.burshsize);

        pathSizeWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Map progress (0-100) to a suitable brush size range, e.g., 1 to 50
                float brushSize = 1 + (progress / 100.0f) * 49; // Scale to 1-50
                handOverlayView.changeBrushSize(brushSize); // Update the brush size
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Optional: Add behavior when touch starts
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Optional: Add behavior when touch stops
            }
        });


        color.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ColorPickerDialogFragment dialogFragment = new ColorPickerDialogFragment(color -> {
                    handOverlayView.changeColor(color);
                });
                dialogFragment.show(getSupportFragmentManager(), "colorPicker");
            }
        });


        clearButton.setOnClickListener(v -> handOverlayView.clearPaths());

        HandsOptions handsOptions = HandsOptions.builder()
                .setStaticImageMode(false)
                .setMaxNumHands(1)
                .setRunOnGpu(true)
                .build();
        hands = new Hands(this, handsOptions);
        hands.setErrorListener((message, e) -> Log.e("MediaPipe", "Error: " + message, e));
        hands.setResultListener(handsResult -> {
            if (!handsResult.multiHandLandmarks().isEmpty()) {
                runOnUiThread(() -> handOverlayView.updateLandmarks(handsResult));
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (textureView.isAvailable()) {
                startCamera();
            } else {
                textureView.setSurfaceTextureListener(surfaceTextureListener);
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }


        Button recordButton = findViewById(R.id.recordButton);

        // Set up click listener to start/stop recording
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isRecording) {
                    startRecording();
                    recordButton.setText("Stop Recording");
                } else {
                    stopRecording();
                    recordButton.setText("Start Recording");
                }
            }
        });
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e("MainActivity", "Interrupted while stopping background thread", e);
            }
        }
    }

    private void stopCamera() {
        Log.d("MainActivity", "Stopping camera");
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        stopBackgroundThread();
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    private void startCamera() {
        Log.d("MainActivity", "Starting camera");

        startBackgroundThread();

        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = cameraManager.getCameraIdList()[1];
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            Size imageDimension = new Size(480, 640);
            imageReader = ImageReader.newInstance(imageDimension.getWidth(), imageDimension.getHeight(), ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCameraCaptureSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(480, 640);

            Surface previewSurface = new Surface(texture);
            Surface imageReaderSurface = imageReader.getSurface();

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);
            captureRequestBuilder.addTarget(imageReaderSurface);

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int rotationDegrees = getJpegOrientation(rotation);

            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, rotationDegrees);

            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, imageReaderSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            cameraCaptureSession = session;
                            try {
                                captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e("MainActivity", "Camera Capture Session configuration failed");
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        matrix.postScale(1, -1, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private int getJpegOrientation(int deviceRotation) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = cameraManager.getCameraIdList()[1];
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            int surfaceRotationDegrees = 0;
            switch (deviceRotation) {
                case Surface.ROTATION_0: surfaceRotationDegrees = 0; break;
                case Surface.ROTATION_90: surfaceRotationDegrees = 90; break;
                case Surface.ROTATION_180: surfaceRotationDegrees = 180; break;
                case Surface.ROTATION_270: surfaceRotationDegrees = 270; break;
            }

            int jpegOrientation;
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                jpegOrientation = (sensorOrientation + surfaceRotationDegrees) % 360;
                jpegOrientation = (360 - jpegOrientation) % 360;
            } else {
                jpegOrientation = (sensorOrientation - surfaceRotationDegrees + 360) % 360;
            }
            return jpegOrientation;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            imageProcessingExecutor.execute(() -> { // Offload image processing
                Image image = reader.acquireLatestImage();
                if (image == null) {
                    return;
                }

                try {
                    Bitmap bitmap = convertYUV420888ToBitmap(image);
                    if (bitmap != null) {
                        int rotationDegrees = getJpegOrientation(getWindowManager().getDefaultDisplay().getRotation());
                        bitmap = rotateBitmap(bitmap, rotationDegrees);
                        long timestamp = System.currentTimeMillis();
                        processImage(bitmap, timestamp);
                    }
                } finally {
                    image.close();
                }
            });
        }
    };

    private void processImage(Bitmap bitmap, long timestamp) {
        if (bitmap != null) {
            hands.send(bitmap, timestamp);
            hands.setResultListener(handsResult -> {
                if (handsResult != null && !handsResult.multiHandLandmarks().isEmpty()) {
                    runOnUiThread(() -> handOverlayView.updateLandmarks(handsResult));
                }
            });
        }
    }

    private Bitmap convertYUV420888ToBitmap(Image image) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            startCamera();
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        stopCamera();
        super.onPause();
    }

    private void showMessageOKCancel() {
        new AlertDialog.Builder(this)
                .setMessage("You need to allow access to the camera")
                .setPositiveButton("OK", (dialog, which) -> requestPermissionLauncher.launch(Manifest.permission.CAMERA))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void startRecording() {
        // Prepare the MediaRecorder
        mediaRecorder = new MediaRecorder();
//        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        // Set output format and file path
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        videoFilePath = getFilePath();
        mediaRecorder.setOutputFile(videoFilePath);

        // Set additional parameters (resolution, bitrate, etc.)
        mediaRecorder.setVideoSize(1280, 720); // Set the resolution
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024); // 5Mbps
        mediaRecorder.setVideoFrameRate(30); // 30 fps

        try {
            mediaRecorder.prepare();
            mediaRecorder.start(); // Start recording
            isRecording = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        mediaRecorder.stop();
        mediaRecorder.reset();
        mediaRecorder.release();
        mediaRecorder = null;
        isRecording = false;

        // Notify user
        Toast.makeText(this, "Recording saved to: " + videoFilePath, Toast.LENGTH_LONG).show();
    }

    private String getFilePath() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadsDir, "screen_record_" + System.currentTimeMillis() + ".mp4").getAbsolutePath();
    }

}
