package com.anthonykeane.speedsignfinder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup.LayoutParams;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.*;

/**
 * This class is an implementation of the Bridge View between OpenCV and Java Camera.
 * This class relays on the functionality available in base class and only implements
 * required functions:
 * connectCamera - opens Java camera and sets the PreviewCallback to be delivered.
 * disconnectCamera - closes the camera and stops preview.
 * When frame is delivered via callback from Camera - it processed via OpenCV to be
 * converted to RGBA32 and then passed to the external callback for modifications if required.
 */
public class pakJavaCameraView extends CameraBridgeViewBase implements PreviewCallback {

    private static final int MAGIC_TEXTURE_ID = 10;
    private static final String TAG = "pakJavaCameraView";

    private byte mBuffer[];
    private Mat[] mFrameChain;
    private int mChainIdx = 0;
    private Thread mThread;
    private boolean mStopThread;
    private boolean bExperimental = true;
    protected Camera mCamera;
    protected JavaCameraFrame mCameraFrame;
    private SurfaceTexture mSurfaceTexture;



    public static class JavaCameraSizeAccessor implements ListItemAccessor {

        public int getWidth(Object obj) {
            Camera.Size size = (Camera.Size) obj;
            return size.width;
        }

        public int getHeight(Object obj) {
            Camera.Size size = (Camera.Size) obj;
            return size.height;
        }
    }



    public pakJavaCameraView(Context context, int cameraId) {
        super(context, cameraId);
    }

    public pakJavaCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);

        SharedPreferences appSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, ?> xx = appSharedPrefs.getAll();

//todo can't leave this hardcoded
        bExperimental = appSharedPrefs.getBoolean("bExperimental", false);






    }

    protected boolean initializeCamera(int width, int height) {
        Log.d(TAG, "Initialize java camera");
        boolean result = true;
        synchronized (this) {
            mCamera = null;

            if (mCameraIndex == -1) {
                Log.d(TAG, "Trying to open camera with old open()");
                try {
                    mCamera = Camera.open();
                }
                catch (Exception e){
                    Log.e(TAG, "Camera is not available (in use or does not exist): " + e.getLocalizedMessage());
                }

                if(mCamera == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    boolean connected = false;
                    for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                        Log.d(TAG, "Trying to open camera with new open(" + Integer.valueOf(camIdx) + ")");
                        try {
                            mCamera = Camera.open(camIdx);
                            connected = true;
                        } catch (RuntimeException e) {
                            Log.e(TAG, "Camera #" + camIdx + "failed to open: " + e.getLocalizedMessage());
                        }
                        if (connected) break;
                    }
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    Log.d(TAG, "Trying to open camera with new open(" + Integer.valueOf(mCameraIndex) + ")");
                    try {
                        mCamera = Camera.open(mCameraIndex);
                    } catch (RuntimeException e) {
                        Log.e(TAG, "Camera #" + mCameraIndex + "failed to open: " + e.getLocalizedMessage());
                    }
                }
            }

            if (mCamera == null)
                return false;

            /* Now set camera parameters */
            try {
                Camera.Parameters params = mCamera.getParameters();
                Log.d(TAG, "getSupportedPreviewSizes()");
                List<android.hardware.Camera.Size> sizes = params.getSupportedPreviewSizes();






                //Set Camera properties
                //params.setExposureCompensation(exposure);



                if (sizes != null) {
                    /* Select the size that fits surface considering maximum size allowed */
                    Size frameSize = calculateCameraFrameSize(sizes, new JavaCameraSizeAccessor(), width, height);

                    params.setPreviewFormat(ImageFormat.NV21);
                    Log.d(TAG, "Set preview size to " + (int) frameSize.width + "x" + (int) frameSize.height);
                    params.setPreviewSize((int)frameSize.width, (int)frameSize.height);

                    List<String> ListOfModes = params.getSupportedWhiteBalance();
                    if (ListOfModes != null && ListOfModes.contains(Camera.Parameters.WHITE_BALANCE_AUTO))
                    { params.setFocusMode(Camera.Parameters.WHITE_BALANCE_AUTO); }

                    ListOfModes = params.getSupportedSceneModes();
                    if (ListOfModes != null && ListOfModes.contains(Camera.Parameters.SCENE_MODE_AUTO))
                    { params.setFocusMode(Camera.Parameters.SCENE_MODE_AUTO); }

                    if (bExperimental)
                    {
                        ListOfModes = params.getSupportedFocusModes();
                        if (ListOfModes != null && ListOfModes.contains(Camera.Parameters.FOCUS_MODE_INFINITY))
                        { params.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY); }


                        ListOfModes = split(params.get("iso-values"));
                        if (ListOfModes != null )
                        {
                            Collections.sort(ListOfModes,String.CASE_INSENSITIVE_ORDER);
                            if( ListOfModes.contains("ISO_HJR"))
                            {
                                params.set("iso","ISO_HJR");
                            }
                            else
                            {
                                params.set("iso",ListOfModes.get(ListOfModes.size()-1));
                            }
                        }

                        ListOfModes = split(params.get("preview-frame-rate-values"));
                        if (ListOfModes != null )
                        {
                            Collections.sort(ListOfModes, new Comparator<String>() {
                                @Override
                                public int compare(String o1, String o2) {
                                    return (Integer.parseInt(o1)<Integer.parseInt(o2) ? -1 : (Integer.parseInt(o1)==Integer.parseInt(o2) ? 0 : 1)); }
                            });

                            if( ListOfModes.contains("5"))
                            {
                                params.set("preview-frame-rate","15");
                            }
                            else
                            {
                                params.set("preview-frame-rate",ListOfModes.get(0));
                            }
                        }



                    }
                    else
                    {
                        ListOfModes = params.getSupportedFocusModes();
                        if (ListOfModes != null && ListOfModes.contains(Camera.Parameters.FOCUS_MODE_AUTO))
                        {
                            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                        }


                        ListOfModes = split(params.get("iso-values"));
                        if (ListOfModes != null && ListOfModes.contains("auto"))
                        {
                            params.set("iso","auto");
                        }
                    }






                    mCamera.setParameters(params);
                    params = mCamera.getParameters();

                    mFrameWidth = params.getPreviewSize().width;
                    mFrameHeight = params.getPreviewSize().height;

                    if ((getLayoutParams().width == LayoutParams.MATCH_PARENT) && (getLayoutParams().height == LayoutParams.MATCH_PARENT))
                        mScale = Math.min(((float)height)/mFrameHeight, ((float)width)/mFrameWidth);
                    else
                        mScale = 0;

                    if (mFpsMeter != null) {
                        mFpsMeter.setResolution(mFrameWidth, mFrameHeight);
                    }

                    int size = mFrameWidth * mFrameHeight;
                    size  = size * ImageFormat.getBitsPerPixel(params.getPreviewFormat()) / 8;
                    mBuffer = new byte[size];

                    mCamera.addCallbackBuffer(mBuffer);
                    mCamera.setPreviewCallbackWithBuffer(this);

                    mFrameChain = new Mat[2];
                    mFrameChain[0] = new Mat(mFrameHeight + (mFrameHeight/2), mFrameWidth, CvType.CV_8UC1);
                    mFrameChain[1] = new Mat(mFrameHeight + (mFrameHeight/2), mFrameWidth, CvType.CV_8UC1);

                    AllocateCache();

                    mCameraFrame = new JavaCameraFrame(mFrameChain[mChainIdx], mFrameWidth, mFrameHeight);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        mSurfaceTexture = new SurfaceTexture(MAGIC_TEXTURE_ID);
                        mCamera.setPreviewTexture(mSurfaceTexture);
                    } else
                        mCamera.setPreviewDisplay(null);

                    /* Finally we are ready to start the preview */
                    Log.d(TAG, "startPreview");
                    mCamera.startPreview();
                }
                else
                    result = false;
            } catch (Exception e) {
                result = false;
                e.printStackTrace();
            }
        }

        return result;
    }

    protected void releaseCamera() {
        synchronized (this) {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
            }
            mCamera = null;
            if (mFrameChain != null) {
                mFrameChain[0].release();
                mFrameChain[1].release();
            }
            if (mCameraFrame != null)
                mCameraFrame.release();
        }
    }

    @Override
    protected boolean connectCamera(int width, int height) {

        /* 1. We need to instantiate camera
         * 2. We need to start thread which will be getting frames
         */
        /* First step - initialize camera connection */
        Log.d(TAG, "Connecting to camera");


            if (!initializeCamera(width, height))    return false;

        /* now we can start update thread */
        Log.d(TAG, "Starting processing thread");
        mStopThread = false;
        mThread = new Thread(new CameraWorker());
        mThread.start();

        return true;
    }

    protected void disconnectCamera() {
        /* 1. We need to stop thread which updating the frames
         * 2. Stop camera and release it
         */
        Log.d(TAG, "Disconnecting from camera");
        try {
            mStopThread = true;
            Log.d(TAG, "Notify thread");
            synchronized (this) {
                this.notify();
            }
            Log.d(TAG, "Wating for thread");
            if (mThread != null)
                mThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mThread =  null;
        }

        /* Now release camera */
        releaseCamera();
    }

    public void onPreviewFrame(byte[] frame, Camera arg1) {
        Log.d(TAG, "Preview Frame received. Frame size: " + frame.length);
        synchronized (this) {
            mFrameChain[1 - mChainIdx].put(0, 0, frame);
            this.notify();
        }
        if (mCamera != null)
            mCamera.addCallbackBuffer(mBuffer);
    }

    private class JavaCameraFrame implements CvCameraViewFrame {
        public Mat gray() {
            return mYuvFrameData.submat(0, mHeight, 0, mWidth);
        }

        public Mat rgba() {
            Imgproc.cvtColor(mYuvFrameData, mRgba, Imgproc.COLOR_YUV2BGR_NV12, 4);
            return mRgba;
        }

        public Mat hsv() {
            Imgproc.cvtColor(mYuvFrameData, mRgba, Imgproc.COLOR_YUV2BGR_NV12, 4);
            return mRgba;
        }
        public JavaCameraFrame(Mat Yuv420sp, int width, int height) {
            super();
            mWidth = width;
            mHeight = height;
            mYuvFrameData = Yuv420sp;
            mRgba = new Mat();
        }

        public void release() {
            mRgba.release();
        }

        private JavaCameraFrame(CvCameraViewFrame obj) {
        }

        private Mat mYuvFrameData;
        private Mat mRgba;
        private int mWidth;
        private int mHeight;
    };

    private class CameraWorker implements Runnable {

        public void run() {
            do {
                synchronized (pakJavaCameraView.this) {
                    try {
                        pakJavaCameraView.this.wait();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                if (!mStopThread) {
                    if (!mFrameChain[mChainIdx].empty())
                        deliverAndDrawFrame(mCameraFrame);
                    mChainIdx = 1 - mChainIdx;
                }
            } while (!mStopThread);
            Log.d(TAG, "Finish processing thread");
        }
    }


    // Splits a comma delimited string to an ArrayList of String.
    // Return null if the passing string is null or the size is 0.
    private ArrayList<String> split(String str) {
        if (str == null) return null;

        TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter(',');
        splitter.setString(str);
        ArrayList<String> substrings = new ArrayList<String>();
        for (String s : splitter) {
            substrings.add(s);
        }
        return substrings;
    }








}
//S4
//        [0] = {java.util.HashMap$HashMapEntry@830038207424}"capture-burst-interval-max" -> "10"
//        [1] = {java.util.HashMap$HashMapEntry@830040617288}"zoom" -> "0"
//        [2] = {java.util.HashMap$HashMapEntry@830040612272}"redeye-reduction-values" -> "enable,disable"
//        [3] = {java.util.HashMap$HashMapEntry@830038213592}"max-num-detected-faces-hw" -> "10"
//        [4] = {java.util.HashMap$HashMapEntry@830040612656}"scene-detect-values" -> "off,on"
//        [5] = {java.util.HashMap$HashMapEntry@830040611760}"qc-camera-features" -> "1"
//        [6] = {java.util.HashMap$HashMapEntry@830038209600}"face-detection-values" -> "off,on"
//        [7] = {java.util.HashMap$HashMapEntry@830040617032}"whitebalance" -> "auto"
//        [8] = {java.util.HashMap$HashMapEntry@830038214104}"max-sharpness" -> "30"
//        [9] = {java.util.HashMap$HashMapEntry@830038216408}"preview-format-values" -> "yuv420sp,yuv420sp-adreno,yuv420p,nv12"
//        [10] = {java.util.HashMap$HashMapEntry@830038212568}"jpeg-thumbnail-quality" -> "90"
//        [11] = {java.util.HashMap$HashMapEntry@830038216280}"preview-format" -> "yuv420sp"
//        [12] = {java.util.HashMap$HashMapEntry@830038209472}"face-detection" -> "off"
//        [13] = {java.util.HashMap$HashMapEntry@830038206784}"camera-mode-values" -> "0,1"
//        [14] = {java.util.HashMap$HashMapEntry@830040616904}"video-zoom-support" -> "true"
//        [15] = {java.util.HashMap$HashMapEntry@830038211520}"iso" -> "auto"
//        [16] = {java.util.HashMap$HashMapEntry@830038209856}"fast-fps-mode" -> "-"
//        [17] = {java.util.HashMap$HashMapEntry@830038214488}"mce-values" -> "enable,disable"
//        [18] = {java.util.HashMap$HashMapEntry@830038210112}"flash-mode-values" -> "off,auto,on,torch"
//        [19] = {java.util.HashMap$HashMapEntry@830038216792}"preview-frame-rate" -> "30"
//        [20] = {java.util.HashMap$HashMapEntry@830038206528}"cam_mode" -> "0"
//        [21] = {java.util.HashMap$HashMapEntry@830038212824}"jpeg-thumbnail-width" -> "320"
//        [22] = {java.util.HashMap$HashMapEntry@830040616392}"video-size" -> "1920x1080"
//        [23] = {java.util.HashMap$HashMapEntry@830040613960}"scene-mode-values" -> "auto,asd,action,portrait,landscape,night,night-portrait,theatre,beach,snow,sunset,steadyphoto,fireworks,sports,party,candlelight,back-light,flowers,AR,text,fall-color,dusk-dawn"
//        [24] = {java.util.HashMap$HashMapEntry@830040612144}"redeye-reduction" -> "disable"
//        [25] = {java.util.HashMap$HashMapEntry@830038216664}"preview-fps-range-values" -> "(4000,30000)"
//        [26] = {java.util.HashMap$HashMapEntry@830038211008}"histogram" -> "disable"
//        [27] = {java.util.HashMap$HashMapEntry@830038206656}"camera-mode" -> "1"
//        [28] = {java.util.HashMap$HashMapEntry@830040611632}"preview-size-values" -> "1920x1080,1440x1080,1280x720,1056x864,960x720,720x480,640x480,320x240,176x144"
//        [29] = {java.util.HashMap$HashMapEntry@830040615368}"touch-af-aec" -> "touch-off"
//        [30] = {java.util.HashMap$HashMapEntry@830038216536}"preview-fps-range" -> "4000,30000"
//        [31] = {java.util.HashMap$HashMapEntry@830038206272}"auto-whitebalance-lock" -> "false"
//        [32] = {java.util.HashMap$HashMapEntry@830038214872}"min-exposure-compensation" -> "-4"
//        [33] = {java.util.HashMap$HashMapEntry@830038205760}"antibanding" -> "off"
//        [34] = {java.util.HashMap$HashMapEntry@830038213720}"max-num-focus-areas" -> "1"
//        [35] = {java.util.HashMap$HashMapEntry@830040615880}"vertical-view-angle" -> "44.1"
//        [36] = {java.util.HashMap$HashMapEntry@830038208576}"display_mode" -> "landscape"
//        [37] = {java.util.HashMap$HashMapEntry@830040611888}"qc-max-num-requested-faces" -> "2"
//        [38] = {java.util.HashMap$HashMapEntry@830038207168}"capture-burst-exposures-values" -> "-12,-11,-10,-9,-8,-7,-6,-5,-4,-3,-2,-1,0,1,2,3,4,5,6,7,8,9,10,11,12"
//        [39] = {java.util.HashMap$HashMapEntry@830038213976}"max-saturation" -> "10"
//        [40] = {java.util.HashMap$HashMapEntry@830038207552}"capture-burst-interval-min" -> "1"
//        [41] = {java.util.HashMap$HashMapEntry@830038215000}"no-display-mode" -> "0"
//        [42] = {java.util.HashMap$HashMapEntry@830038213336}"max-contrast" -> "10"
//        [43] = {java.util.HashMap$HashMapEntry@830038215512}"picture-format-values" -> "jpeg,raw"
//        [44] = {java.util.HashMap$HashMapEntry@830040616136}"video-hfr" -> "off"
//        [45] = {java.util.HashMap$HashMapEntry@830038209344}"exposure-compensation-step" -> "1"
//        [46] = {java.util.HashMap$HashMapEntry@830040612528}"scene-detect" -> "off"
//        [47] = {java.util.HashMap$HashMapEntry@830038215640}"picture-size" -> "4128x3096"
//        [48] = {java.util.HashMap$HashMapEntry@830040612400}"saturation" -> "5"
//        [49] = {java.util.HashMap$HashMapEntry@830040617160}"whitebalance-values" -> "auto,incandescent,fluorescent,daylight,cloudy-daylight"
//        [50] = {java.util.HashMap$HashMapEntry@830038215384}"picture-format" -> "jpeg"
//        [51] = {java.util.HashMap$HashMapEntry@830040617672}"zsl" -> "off"
//        [52] = {java.util.HashMap$HashMapEntry@830038213080}"lensshade-values" -> "enable,disable"
//        [53] = {java.util.HashMap$HashMapEntry@830040614088}"selectable-zone-af" -> "auto"
//        [54] = {java.util.HashMap$HashMapEntry@830040616264}"video-hfr-values" -> "off"
//        [55] = {java.util.HashMap$HashMapEntry@830038212184}"iso-values" -> "auto,ISO_HJR,100,200,400,800,1600"
//        [56] = {java.util.HashMap$HashMapEntry@830040614216}"selectable-zone-af-values" -> "auto,spot-metering,center-weighted,frame-average"
//        [57] = {java.util.HashMap$HashMapEntry@830038212952}"lensshade" -> "enable"
//        [58] = {java.util.HashMap$HashMapEntry@830038207040}"capture-burst-exposures" -> ""
//        [59] = {java.util.HashMap$HashMapEntry@830038216152}"preferred-preview-size-for-video" -> "176x144"
//        [60] = {java.util.HashMap$HashMapEntry@830038205632}"anti-shake" -> "0"
//        [61] = {java.util.HashMap$HashMapEntry@830038211392}"intelligent-mode" -> "0"
//        [62] = {java.util.HashMap$HashMapEntry@830038214360}"mce" -> "enable"
//        [63] = {java.util.HashMap$HashMapEntry@830038210880}"hfr-size-values" -> "800x480,640x480"
//        [64] = {java.util.HashMap$HashMapEntry@830040612016}"recording-hint" -> "false"
//        [65] = {java.util.HashMap$HashMapEntry@830040616776}"video-stabilization" -> "false"
//        [66] = {java.util.HashMap$HashMapEntry@830040617544}"zoom-supported" -> "true"
//        [67] = {java.util.HashMap$HashMapEntry@830040615112}"strtextures" -> "OFF"
//        [68] = {java.util.HashMap$HashMapEntry@830038214616}"metering" -> "center"
//        [69] = {java.util.HashMap$HashMapEntry@830038208448}"denoise-values" -> "denoise-off,denoise-on"
//        [70] = {java.util.HashMap$HashMapEntry@830040617800}"zsl-values" -> "off,on"
//        [71] = {java.util.HashMap$HashMapEntry@830040614344}"sharpness" -> "10"
//        [72] = {java.util.HashMap$HashMapEntry@830038208192}"contrast" -> "5"
//        [73] = {java.util.HashMap$HashMapEntry@830040613832}"scene-mode" -> "auto"
//        [74] = {java.util.HashMap$HashMapEntry@830038212312}"jpeg-quality" -> "85"
//        [75] = {java.util.HashMap$HashMapEntry@830038207296}"capture-burst-interval" -> "1"
//        [76] = {java.util.HashMap$HashMapEntry@830038211136}"histogram-values" -> "enable,disable"
//        [77] = {java.util.HashMap$HashMapEntry@830038215256}"overlay-format" -> "265"
//        [78] = {java.util.HashMap$HashMapEntry@830038214744}"metering-areas" -> "(0,0,0,0,0)"
//        [79] = {java.util.HashMap$HashMapEntry@830038206912}"capture-burst-captures-values" -> "2"
//        [80] = {java.util.HashMap$HashMapEntry@830040616520}"video-size-values" -> "1920x1080,1440x1080,1280x720,800x450,720x480,640x480,320x240,176x144"
//        [81] = {java.util.HashMap$HashMapEntry@830040614728}"skinToneEnhancement" -> "0"
//        [82] = {java.util.HashMap$HashMapEntry@830040611504}"preview-size" -> "1440x1080"
//        [83] = {java.util.HashMap$HashMapEntry@830038210240}"focal-length" -> "4.2"
//        [84] = {java.util.HashMap$HashMapEntry@830038205504}"ae-bracket-hdr-values" -> "Off,HDR,AE-Bracket"
//        [85] = {java.util.HashMap$HashMapEntry@830038208320}"denoise" -> "denoise-off"
//        [86] = {java.util.HashMap$HashMapEntry@830038207808}"capture-burst-retroactive" -> "0"
//        [87] = {java.util.HashMap$HashMapEntry@830038216920}"preview-frame-rate-values" -> "15,30"
//        [88] = {java.util.HashMap$HashMapEntry@830038216024}"power-mode-supported" -> "true"
//        [89] = {java.util.HashMap$HashMapEntry@830038208064}"contextualtag-cityid" -> "0"
//        [90] = {java.util.HashMap$HashMapEntry@830038213848}"max-num-metering-areas" -> "0"
//        [91] = {java.util.HashMap$HashMapEntry@830038209728}"face_position" -> "(-1,-1)"
//        [92] = {java.util.HashMap$HashMapEntry@830038210752}"focus-mode-values" -> "auto,infinity,macro,continuous-picture,continuous-video"
//        [93] = {java.util.HashMap$HashMapEntry@830038215896}"power-mode" -> "Normal_Power"
//        [94] = {java.util.HashMap$HashMapEntry@830038212696}"jpeg-thumbnail-size-values" -> "512x288,480x288,432x288,512x384,352x288,320x240,176x144,0x0"
//        [95] = {java.util.HashMap$HashMapEntry@830040617416}"zoom-ratios" -> "100,102,104,107,109,112,114,117,120,123,125,128,131,135,138,141,144,148,151,155,158,162,166,170,174,178,182,186,190,195,200"
//        [96] = {java.util.HashMap$HashMapEntry@830040614600}"single-isp-output-enabled" -> "false"
//        [97] = {java.util.HashMap$HashMapEntry@830038208704}"dual_mode" -> "0"
//        [98] = {java.util.HashMap$HashMapEntry@830040615240}"sw-vdis" -> "off"
//        [99] = {java.util.HashMap$HashMapEntry@830038215768}"picture-size-values" -> "4128x3096,4128x2322,3264x2448,3264x1836,2048x1536,2048x1152,1280x720,640x480"
//        [100] = {java.util.HashMap$HashMapEntry@830038207936}"capture-burst-retroactive-max" -> "2"



//S3

//        [0] = {java.util.HashMap$HashMapEntry@830048175056}"preferred-preview-size-for-video" -> "1280x720"
//        [1] = {java.util.HashMap$HashMapEntry@830048178024}"zoom" -> "0"
//        [2] = {java.util.HashMap$HashMapEntry@830048173904}"max-num-detected-faces-hw" -> "5"
//        [3] = {java.util.HashMap$HashMapEntry@830048178280}"zoom-supported" -> "true"
//        [4] = {java.util.HashMap$HashMapEntry@830048174288}"metering" -> "center"
//        [5] = {java.util.HashMap$HashMapEntry@830048170696}"contrast" -> "2"
//        [6] = {java.util.HashMap$HashMapEntry@830048177768}"whitebalance" -> "auto"
//        [7] = {java.util.HashMap$HashMapEntry@830048176208}"scene-mode" -> "auto"
//        [8] = {java.util.HashMap$HashMapEntry@830048172856}"jpeg-quality" -> "100"
//        [9] = {java.util.HashMap$HashMapEntry@830048175312}"preview-format-values" -> "yuv420sp,yuv420p"
//        [10] = {java.util.HashMap$HashMapEntry@830048173112}"jpeg-thumbnail-quality" -> "100"
//        [11] = {java.util.HashMap$HashMapEntry@830048175184}"preview-format" -> "yuv420sp"
//        [12] = {java.util.HashMap$HashMapEntry@830048177512}"video-size-values" -> "1280x720,1920x1080,960x720,720x480,640x480,352x288,320x240,176x144"
//        [13] = {java.util.HashMap$HashMapEntry@830048175952}"preview-size" -> "960x720"
//        [14] = {java.util.HashMap$HashMapEntry@830048171808}"focal-length" -> "3.700000"
//        [15] = {java.util.HashMap$HashMapEntry@830048172728}"iso" -> "auto"
//        [16] = {java.util.HashMap$HashMapEntry@830048171424}"fast-fps-mode" -> "0"
//        [17] = {java.util.HashMap$HashMapEntry@830048171680}"flash-mode-values" -> "auto,off,on,torch"
//        [18] = {java.util.HashMap$HashMapEntry@830048175824}"preview-frame-rate-values" -> "30,20,15,10"
//        [19] = {java.util.HashMap$HashMapEntry@830048175696}"preview-frame-rate" -> "30"
//        [20] = {java.util.HashMap$HashMapEntry@830048172472}"focus-mode-values" -> "auto,infinity,macro,fixed,continuous-picture,continuous-video"
//        [21] = {java.util.HashMap$HashMapEntry@830048173368}"jpeg-thumbnail-width" -> "160"
//        [22] = {java.util.HashMap$HashMapEntry@830048176848}"video-size" -> "1280x720"
//        [23] = {java.util.HashMap$HashMapEntry@830048176336}"scene-mode-values" -> "auto,portrait,landscape,night,beach,snow,sunset,fireworks,sports,party,candlelight,dusk-dawn,fall-color,text,back-light"
//        [24] = {java.util.HashMap$HashMapEntry@830048175568}"preview-fps-range-values" -> "(10000,10000),(15000,15000),(15000,30000),(30000,30000)"
//        [25] = {java.util.HashMap$HashMapEntry@830048173240}"jpeg-thumbnail-size-values" -> "160x120,160x90,144x96,0x0"
//        [26] = {java.util.HashMap$HashMapEntry@830048178152}"zoom-ratios" -> "100,102,104,109,111,113,119,121,124,131,134,138,146,150,155,159,165,170,182,189,200,213,222,232,243,255,283,300,319,364,400"
//        [27] = {java.util.HashMap$HashMapEntry@830048176080}"preview-size-values" -> "960x720,1280x720,640x480,352x288,320x240"
//        [28] = {java.util.HashMap$HashMapEntry@830048174928}"picture-size-values" -> "640x480,960x720,1024x768,1280x720,1600x1200,2560x1920,3264x2448,2048x1536,3264x1836,2048x1152,3264x2176"
//        [29] = {java.util.HashMap$HashMapEntry@830048175440}"preview-fps-range" -> "15000,30000"
//        [30] = {java.util.HashMap$HashMapEntry@830048174416}"min-exposure-compensation" -> "-4"
//        [31] = {java.util.HashMap$HashMapEntry@830048170128}"antibanding" -> "off"
//        [32] = {java.util.HashMap$HashMapEntry@830048174032}"max-num-focus-areas" -> "1"
//        [33] = {java.util.HashMap$HashMapEntry@830048176592}"vertical-view-angle" -> "49.3"
//        [34] = {java.util.HashMap$HashMapEntry@830048172600}"horizontal-view-angle" -> "63"
//        [35] = {java.util.HashMap$HashMapEntry@830048177640}"video-stabilization-supported" -> "true"
//        [36] = {java.util.HashMap$HashMapEntry@830048172984}"jpeg-thumbnail-height" -> "120"
//        [37] = {java.util.HashMap$HashMapEntry@830048176464}"smooth-zoom-supported" -> "false"
//        [38] = {java.util.HashMap$HashMapEntry@830048172344}"focus-mode" -> "auto"
//        [39] = {java.util.HashMap$HashMapEntry@830048170512}"auto-whitebalance-lock-supported" -> "true"
//        [40] = {java.util.HashMap$HashMapEntry@830048176720}"video-frame-format" -> "yuv420sp"
//        [41] = {java.util.HashMap$HashMapEntry@830048174672}"picture-format-values" -> "jpeg"
//        [42] = {java.util.HashMap$HashMapEntry@830048173496}"max-exposure-compensation" -> "4"
//        [43] = {java.util.HashMap$HashMapEntry@830048172088}"focus-areas" -> "(0,0,0,0,0)"
//        [44] = {java.util.HashMap$HashMapEntry@830048171168}"exposure-compensation" -> "0"
//        [45] = {java.util.HashMap$HashMapEntry@830048171296}"exposure-compensation-step" -> "0.5"
//        [46] = {java.util.HashMap$HashMapEntry@830048171552}"flash-mode" -> "off"
//        [47] = {java.util.HashMap$HashMapEntry@830048170952}"effect-values" -> "none,mono,negative,sepia,solarize,posterize,washed,vintage-warm,vintage-cold,point-blue,point-red-yellow,point-green,cartoonize"
//        [48] = {java.util.HashMap$HashMapEntry@830048174800}"picture-size" -> "640x480"
//        [49] = {java.util.HashMap$HashMapEntry@830048174160}"max-zoom" -> "30"
//        [50] = {java.util.HashMap$HashMapEntry@830048170824}"effect" -> "none"
//        [51] = {java.util.HashMap$HashMapEntry@830048177896}"whitebalance-values" -> "auto,incandescent,fluorescent,daylight,cloudy-daylight"
//        [52] = {java.util.HashMap$HashMapEntry@830048174544}"picture-format" -> "jpeg"
//        [53] = {java.util.HashMap$HashMapEntry@830048172216}"focus-distances" -> "0.15,1.20,Infinity"
//        [54] = {java.util.HashMap$HashMapEntry@830048170384}"auto-exposure-lock-supported" -> "true"
//        [55] = {java.util.HashMap$HashMapEntry@830048170256}"antibanding-values" -> "50hz,off"

// S2
//        [0] = {java.util.HashMap$HashMapEntry@830026631376}"preferred-preview-size-for-video" -> "640x480"
//        [1] = {java.util.HashMap$HashMapEntry@830026633552}"zoom" -> "0"
//        [2] = {java.util.HashMap$HashMapEntry@830026630224}"max-num-detected-faces-hw" -> "0"
//        [3] = {java.util.HashMap$HashMapEntry@830026634344}"zoom-supported" -> "true"
//        [4] = {java.util.HashMap$HashMapEntry@830026633296}"whitebalance" -> "auto"
//        [5] = {java.util.HashMap$HashMapEntry@830026632528}"scene-mode" -> "auto"
//        [6] = {java.util.HashMap$HashMapEntry@830026629456}"jpeg-quality" -> "100"
//        [7] = {java.util.HashMap$HashMapEntry@830026631632}"preview-format-values" -> "yuv420sp,yuv420p,yuv422i-yuyv,yuv422sp,rgb565"
//        [8] = {java.util.HashMap$HashMapEntry@830026629712}"jpeg-thumbnail-quality" -> "100"
//        [9] = {java.util.HashMap$HashMapEntry@830026631504}"preview-format" -> "yuv420sp"
//        [10] = {java.util.HashMap$HashMapEntry@830026632272}"preview-size" -> "800x480"
//        [11] = {java.util.HashMap$HashMapEntry@830026628560}"focal-length" -> "4.030000"
//        [12] = {java.util.HashMap$HashMapEntry@830026628432}"flash-mode-values" -> "off,auto,on,torch"
//        [13] = {java.util.HashMap$HashMapEntry@830026632144}"preview-frame-rate-values" -> "30,25,20,15,10,7"
//        [14] = {java.util.HashMap$HashMapEntry@830026632016}"preview-frame-rate" -> "30"
//        [15] = {java.util.HashMap$HashMapEntry@830026629072}"focus-mode-values" -> "auto,infinity,macro,fixed,facedetect,continuous-video"
//        [16] = {java.util.HashMap$HashMapEntry@830026629968}"jpeg-thumbnail-width" -> "320"
//        [17] = {java.util.HashMap$HashMapEntry@830026633168}"video-size" -> "720x480"
//        [18] = {java.util.HashMap$HashMapEntry@830026632656}"scene-mode-values" -> "auto,portrait,landscape,night,beach,snow,sunset,fireworks,sports,party,candlelight,dusk-dawn,fall-color,back-light,text"
//        [19] = {java.util.HashMap$HashMapEntry@830026631888}"preview-fps-range-values" -> "(7000,30000)"
//        [20] = {java.util.HashMap$HashMapEntry@830026629840}"jpeg-thumbnail-size-values" -> "320x240,400x240,0x0"
//        [21] = {java.util.HashMap$HashMapEntry@830026633680}"zoom-ratios" -> "100,102,104,109,111,113,119,121,124,131,134,138,146,150,155,159,165,170,182,189,200,213,222,232,243,255,283,300,319,364,400"
//        [22] = {java.util.HashMap$HashMapEntry@830026632400}"preview-size-values" -> "640x480,720x480,800x480,800x450,352x288,320x240,176x144"
//        [23] = {java.util.HashMap$HashMapEntry@830026631248}"picture-size-values" -> "3264x2448,3264x1968,2048x1536,2048x1232,800x480,640x480"
//        [24] = {java.util.HashMap$HashMapEntry@830026631760}"preview-fps-range" -> "7000,30000"
//        [25] = {java.util.HashMap$HashMapEntry@830026627664}"auto-whitebalance-lock" -> "false"
//        [26] = {java.util.HashMap$HashMapEntry@830026630736}"min-exposure-compensation" -> "-4"
//        [27] = {java.util.HashMap$HashMapEntry@830026630480}"max-num-focus-areas" -> "1"
//        [28] = {java.util.HashMap$HashMapEntry@830026632912}"vertical-view-angle" -> "47.1"
//        [29] = {java.util.HashMap$HashMapEntry@830026629200}"horizontal-view-angle" -> "60.5"
//        [30] = {java.util.HashMap$HashMapEntry@830026629584}"jpeg-thumbnail-height" -> "240"
//        [31] = {java.util.HashMap$HashMapEntry@830026632784}"smooth-zoom-supported" -> "false"
//        [32] = {java.util.HashMap$HashMapEntry@830026628944}"focus-mode" -> "infinity"
//        [33] = {java.util.HashMap$HashMapEntry@830026633040}"video-frame-format" -> "yuv420sp"
//        [34] = {java.util.HashMap$HashMapEntry@830026630352}"max-num-detected-faces-sw" -> "3"
//        [35] = {java.util.HashMap$HashMapEntry@830026630992}"picture-format-values" -> "jpeg"
//        [36] = {java.util.HashMap$HashMapEntry@830026630096}"max-exposure-compensation" -> "4"
//        [37] = {java.util.HashMap$HashMapEntry@830026628688}"focus-areas" -> "(0,0,0,0,0)"
//        [38] = {java.util.HashMap$HashMapEntry@830026628048}"exposure-compensation" -> "0"
//        [39] = {java.util.HashMap$HashMapEntry@830026628176}"exposure-compensation-step" -> "0.5"
//        [40] = {java.util.HashMap$HashMapEntry@830026628304}"flash-mode" -> "off"
//        [41] = {java.util.HashMap$HashMapEntry@830026627536}"auto-exposure-lock" -> "false"
//        [42] = {java.util.HashMap$HashMapEntry@830026627920}"effect-values" -> "none,mono,negative,sepia,aqua"
//        [43] = {java.util.HashMap$HashMapEntry@830026631120}"picture-size" -> "3264x2448"
//        [44] = {java.util.HashMap$HashMapEntry@830026630608}"max-zoom" -> "30"
//        [45] = {java.util.HashMap$HashMapEntry@830026627792}"effect" -> "none"
//        [46] = {java.util.HashMap$HashMapEntry@830026633424}"whitebalance-values" -> "auto,incandescent,fluorescent,daylight,cloudy-daylight"
//        [47] = {java.util.HashMap$HashMapEntry@830026630864}"picture-format" -> "jpeg"
//        [48] = {java.util.HashMap$HashMapEntry@830026628816}"focus-distances" -> "0.15,1.20,Infinity"
//        [49] = {java.util.HashMap$HashMapEntry@830026629328}"iso-values" -> "auto,ISO50,ISO100,ISO200,ISO400,ISO800"


// TF201
//        [0] = {java.util.HashMap$HashMapEntry@830035710496}"preferred-preview-size-for-video" -> "1280x960"
//        [1] = {java.util.HashMap$HashMapEntry@830035638192}"rotation-values" -> "0,90,180,270"
//        [2] = {java.util.HashMap$HashMapEntry@830035319288}"zoom" -> "0"
//        [3] = {java.util.HashMap$HashMapEntry@830035705368}"recording-hint" -> "false"
//        [4] = {java.util.HashMap$HashMapEntry@830035709696}"max-num-detected-faces-hw" -> "0"
//        [5] = {java.util.HashMap$HashMapEntry@830035328792}"video-stabilization" -> "false"
//        [6] = {java.util.HashMap$HashMapEntry@830035296048}"zoom-supported" -> "true"
//        [7] = {java.util.HashMap$HashMapEntry@830035321768}"whitebalance" -> "auto"
//        [8] = {java.util.HashMap$HashMapEntry@830035325808}"video-stabilization-values" -> "false,true"
//        [9] = {java.util.HashMap$HashMapEntry@830035552632}"scene-mode" -> "auto"
//        [10] = {java.util.HashMap$HashMapEntry@830035708928}"jpeg-quality" -> "95"
//        [11] = {java.util.HashMap$HashMapEntry@830035710464}"nv-flip-preview" -> "off"
//        [12] = {java.util.HashMap$HashMapEntry@830035709472}"preview-format-values" -> "yuv420p,yuv420sp"
//        [13] = {java.util.HashMap$HashMapEntry@830035663000}"rotation" -> "0"
//        [14] = {java.util.HashMap$HashMapEntry@830035709184}"jpeg-thumbnail-quality" -> "90"
//        [15] = {java.util.HashMap$HashMapEntry@830035709984}"preview-format" -> "yuv420sp"
//        [16] = {java.util.HashMap$HashMapEntry@830035710208}"metering-areas" -> "(0,0,0,0,0)"
//        [17] = {java.util.HashMap$HashMapEntry@830035334280}"video-frame-format-values" -> "yuv420p"
//        [18] = {java.util.HashMap$HashMapEntry@830035331568}"video-size-values" -> "40x30,176x144,320x240,352x288,640x480,704x576,720x480,768x432,1280x720,1920x1080"
//        [19] = {java.util.HashMap$HashMapEntry@830035710592}"nv-flip-preview-values" -> "off,vertical,horizontal,both"
//        [20] = {java.util.HashMap$HashMapEntry@830035706632}"preview-size" -> "1280x720"
//        [21] = {java.util.HashMap$HashMapEntry@830035707496}"focal-length" -> "4.390"
//        [22] = {java.util.HashMap$HashMapEntry@830035708672}"iso" -> "auto"
//        [23] = {java.util.HashMap$HashMapEntry@830035707112}"face-detection-mode-values" -> "off,on"
//        [24] = {java.util.HashMap$HashMapEntry@830035710976}"nv-focus-move-msg" -> "false"
//        [25] = {java.util.HashMap$HashMapEntry@830035707368}"flash-mode-values" -> "off,on,auto,torch"
//        [26] = {java.util.HashMap$HashMapEntry@830035707144}"preview-frame-rate-values" -> "5,8,10,15,20,24,25,30"
//        [27] = {java.util.HashMap$HashMapEntry@830035709952}"max-num-metering-areas" -> "0"
//        [28] = {java.util.HashMap$HashMapEntry@830035707656}"preview-frame-rate" -> "30"
//        [29] = {java.util.HashMap$HashMapEntry@830035710720}"nv-flip-still" -> "off"
//        [30] = {java.util.HashMap$HashMapEntry@830035708008}"focus-mode-values" -> "auto,infinity,fixed,continuous-video,continuous-picture"
//        [31] = {java.util.HashMap$HashMapEntry@830035705208}"asus_pp_special" -> "0,0"
//        [32] = {java.util.HashMap$HashMapEntry@830035340280}"shading-values" -> "off,on"
//        [33] = {java.util.HashMap$HashMapEntry@830035709440}"jpeg-thumbnail-width" -> "320"
//        [34] = {java.util.HashMap$HashMapEntry@830035332912}"video-size" -> "1280x720"
//        [35] = {java.util.HashMap$HashMapEntry@830035498304}"scene-mode-values" -> "auto,portrait,landscape,night,snow,sports,sunset,party,backlight,vivid"
//        [36] = {java.util.HashMap$HashMapEntry@830035708448}"preview-fps-range-values" -> "(4000,60000)"
//        [37] = {java.util.HashMap$HashMapEntry@830035709312}"jpeg-thumbnail-size-values" -> "0x0,320x240"
//        [38] = {java.util.HashMap$HashMapEntry@830035317912}"zoom-ratios" -> "100,125,150,175,200,225,250,275,300,325,350,375,400,425,450,475,500,525,550,575,600,625,650,675,700,725,750,775,800"
//        [39] = {java.util.HashMap$HashMapEntry@830035705968}"preview-size-values" -> "176x144,320x240,352x288,640x480,704x576,720x480,768x432,1280x720,1280x960,1360x720,1920x1080"
//        [40] = {java.util.HashMap$HashMapEntry@830035704800}"recording-hint-values" -> "false,true"
//        [41] = {java.util.HashMap$HashMapEntry@830035706984}"face-detection-mode" -> "off"
//        [42] = {java.util.HashMap$HashMapEntry@830035705336}"asus_pp_video" -> "0,0"
//        [43] = {java.util.HashMap$HashMapEntry@830035711008}"picture-size-values" -> "320x240,640x480,704x576,800x600,1024x768,1280x720,1280x960,1600x900,1600x1200,1920x1080,2048x1152,2048x1536,2592x1458,2592x1944,3264x1836,3264x2448"
//        [44] = {java.util.HashMap$HashMapEntry@830035711104}"nv-focus-move-msg-values" -> "false,true"
//        [45] = {java.util.HashMap$HashMapEntry@830035708960}"preview-fps-range" -> "4000,60000"
//        [46] = {java.util.HashMap$HashMapEntry@830035705936}"auto-whitebalance-lock" -> "false"
//        [47] = {java.util.HashMap$HashMapEntry@830035710336}"min-exposure-compensation" -> "-6"
//        [48] = {java.util.HashMap$HashMapEntry@830035704640}"antibanding" -> "off"
//        [49] = {java.util.HashMap$HashMapEntry@830035709824}"max-num-focus-areas" -> "1"
//        [50] = {java.util.HashMap$HashMapEntry@830035337640}"vertical-view-angle" -> "60.000"
//        [51] = {java.util.HashMap$HashMapEntry@830035708544}"horizontal-view-angle" -> "60.000"
//        [52] = {java.util.HashMap$HashMapEntry@830035323160}"wdr-values" -> "off,on"
//        [53] = {java.util.HashMap$HashMapEntry@830035327424}"video-stabilization-supported" -> "false"
//        [54] = {java.util.HashMap$HashMapEntry@830035709056}"jpeg-thumbnail-height" -> "240"
//        [55] = {java.util.HashMap$HashMapEntry@830035339016}"smooth-zoom-supported" -> "true"
//        [56] = {java.util.HashMap$HashMapEntry@830035707880}"focus-mode" -> "infinity"
//        [57] = {java.util.HashMap$HashMapEntry@830035710848}"nv-flip-still-values" -> "off,vertical,horizontal,both"
//        [58] = {java.util.HashMap$HashMapEntry@830035708416}"frame-rate" -> "auto"
//        [59] = {java.util.HashMap$HashMapEntry@830035706064}"auto-whitebalance-lock-supported" -> "true"
//        [60] = {java.util.HashMap$HashMapEntry@830035336224}"video-frame-format" -> "yuv420p"
//        [61] = {java.util.HashMap$HashMapEntry@830035711360}"picture-format-values" -> "jpeg,jfif,exif,yuv420p,yuv420sp"
//        [62] = {java.util.HashMap$HashMapEntry@830035709568}"max-exposure-compensation" -> "6"
//        [63] = {java.util.HashMap$HashMapEntry@830035707624}"focus-areas" -> "(0,0,0,0,0)"
//        [64] = {java.util.HashMap$HashMapEntry@830035330176}"video-snapshot-supported" -> "true"
//        [65] = {java.util.HashMap$HashMapEntry@830035706728}"exposure-compensation" -> "0"
//        [66] = {java.util.HashMap$HashMapEntry@830035456224}"shading" -> "on"
//        [67] = {java.util.HashMap$HashMapEntry@830035706856}"exposure-compensation-step" -> "0.33"
//        [68] = {java.util.HashMap$HashMapEntry@830035707240}"flash-mode" -> "off"
//        [69] = {java.util.HashMap$HashMapEntry@830035705464}"auto-exposure-lock" -> "false"
//        [70] = {java.util.HashMap$HashMapEntry@830035706600}"effect-values" -> "mono,negative,none,posterize,sepia,aqua,solarize,nv-vivid,nv-emboss"
//        [71] = {java.util.HashMap$HashMapEntry@830035712024}"picture-size" -> "3264x2448"
//        [72] = {java.util.HashMap$HashMapEntry@830035710080}"max-zoom" -> "28"
//        [73] = {java.util.HashMap$HashMapEntry@830035706320}"effect" -> "none"
//        [74] = {java.util.HashMap$HashMapEntry@830035320552}"whitebalance-values" -> "auto,incandescent,fluorescent,warm-fluorescent,daylight,cloudy-daylight,shade"
//        [75] = {java.util.HashMap$HashMapEntry@830035711232}"picture-format" -> "jpeg"
//        [76] = {java.util.HashMap$HashMapEntry@830035707752}"focus-distances" -> "0.95,1.9,Infinity"
//        [77] = {java.util.HashMap$HashMapEntry@830035705024}"asus_pp_snapshot" -> "0,0"
//        [78] = {java.util.HashMap$HashMapEntry@830035705808}"auto-exposure-lock-values" -> "false,true"
//        [79] = {java.util.HashMap$HashMapEntry@830035324392}"wdr" -> "off"
//        [80] = {java.util.HashMap$HashMapEntry@830035704896}"asus_pp_preview" -> "0,0"
//        [81] = {java.util.HashMap$HashMapEntry@830035705680}"auto-exposure-lock-supported" -> "true"
//        [82] = {java.util.HashMap$HashMapEntry@830035708800}"iso-values" -> "auto,50,100,200,400,800,1600"
//        [83] = {java.util.HashMap$HashMapEntry@830035706192}"auto-whitebalance-lock-values" -> "false,true"
//        [84] = {java.util.HashMap$HashMapEntry@830035704768}"antibanding-values" -> "off,auto,50hz,60hz"


