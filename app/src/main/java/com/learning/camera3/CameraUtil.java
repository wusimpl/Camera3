package com.learning.camera3;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static android.content.Context.CAMERA_SERVICE;

public class CameraUtil {
    private static final String TAG = "CameraUtil";
    private CameraManager cameraManager;
    private TextureView textureView;
    private Surface previewSurface;
    private CameraDevice backFacingDevice;
    private String backFacingDeviceId;
    private int sensorOrientation;
    private CameraCaptureSession previewSession;
    private CameraCaptureSession takePhotoSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest.Builder takePhotoRequestBuilder;
    private CaptureRequest previewRequest;
    private CaptureRequest takePhotoRequest;
    private ImageReader reader;
    private Size outputPhotoSize;
    //private File imageFile;
    private BaseActivity activity;

    public CameraUtil(final BaseActivity activity, final TextureView textureView){
        this.activity = activity;
        this.cameraManager = (CameraManager)activity.getSystemService(CAMERA_SERVICE);
        this.textureView = textureView;
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                previewSurface = new Surface(surfaceTexture);
                preview();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });


    }

    //纠正屏幕方向
    private int getOrientation() {
        SparseIntArray ORIENTATIONS = new SparseIntArray();
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        return (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360;
    }


    public void preview(){
        //获取CameraDevice
        try {
            String[] cameraIdList =  cameraManager.getCameraIdList();
            for(String cameraId : cameraIdList){
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                //获取支持的最大输出尺寸
                StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
                outputPhotoSize = sizes[0];
                for (Size size : sizes){
                    if(size.getHeight() > outputPhotoSize.getHeight()){
                        outputPhotoSize  = size;
                    }
                }

                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK){
                    backFacingDeviceId = cameraId;
                    sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        openBackFacingCamera(backFacingDeviceId);
    }

    public void takePicpure() throws CameraAccessException {
        if(backFacingDevice == null){
            return;
        }

        reader = ImageReader.newInstance(outputPhotoSize.getWidth(), outputPhotoSize.getHeight(), ImageFormat.JPEG, 1);

        reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireLatestImage();//获取源数据
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                File file = new File(Environment.getExternalStorageDirectory(), Math.random()+"pic.jpg");
                buffer.get(bytes);
                try (FileOutputStream output = new FileOutputStream(file))
                {
                    output.write(bytes);
                    Toast.makeText(activity, "保存: " + file, Toast.LENGTH_SHORT).show();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    image.close();
                }
            }

        },null);

        backFacingDevice.createCaptureSession(Arrays.asList(previewSurface,reader.getSurface()), new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                takePhotoSession = session;

                try {
                    takePhotoRequestBuilder = backFacingDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    takePhotoRequestBuilder.addTarget(reader.getSurface());
                    takePhotoRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    takePhotoRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,getOrientation());
                    takePhotoRequest = takePhotoRequestBuilder.build();
                    takePhotoSession.capture(takePhotoRequest,new CaptureSessionCaptureCallbackImpl(),null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {

            }
        },null);


    }

    class CaptureSessionCaptureCallbackImpl extends CameraCaptureSession.CaptureCallback{
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            preview();
        }
    }

    //开启后置摄像机
    public void openBackFacingCamera(String cameraId) throws SecurityException{
        try {
            cameraManager.openCamera(cameraId,new CameraDeviceStateCallbackImpl(),null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //回调嵌套类实现
    class CameraDeviceStateCallbackImpl extends  CameraDevice.StateCallback{

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            backFacingDevice = camera;
            try {
                previewRequestBuilder = backFacingDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                previewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,getOrientation());
                previewRequestBuilder.addTarget(previewSurface);
                previewRequest = previewRequestBuilder.build();
                createCaptureSession();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            backFacingDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

        }
    }

    //创建捕捉会话
    private void createCaptureSession(){
        try {
            backFacingDevice.createCaptureSession(Arrays.asList(previewSurface),new CaptureSessionStateCallbackImpl(),null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //回调嵌套类实现
    class CaptureSessionStateCallbackImpl extends CameraCaptureSession.StateCallback{

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            previewSession = session;
            try {
                previewSession.setRepeatingRequest(previewRequest,null,null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    }

}