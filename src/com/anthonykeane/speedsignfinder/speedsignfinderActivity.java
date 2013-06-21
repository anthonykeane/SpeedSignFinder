package com.anthonykeane.speedsignfinder;


import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static java.util.UUID.randomUUID;
import static org.opencv.imgproc.Imgproc.BORDER_CONSTANT;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2HSV;
import static org.opencv.imgproc.Imgproc.CV_HOUGH_GRADIENT;
import static org.opencv.imgproc.Imgproc.HoughCircles;
import static org.opencv.imgproc.Imgproc.MORPH_RECT;
import static org.opencv.imgproc.Imgproc.copyMakeBorder;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.dilate;
import static org.opencv.imgproc.Imgproc.erode;
import static org.opencv.imgproc.Imgproc.getStructuringElement;

//import java.util.Queue;


public class speedsignfinderActivity extends Activity implements CvCameraViewListener2 {

//	// uncommemt in camera_image_view.xml before uncommenting this
//	Button b1;

	Sensor accelerometer;
	Sensor gyro;
	Sensor lux;

	SensorManager sm;
	float acceleration[];
	float gyration[];
	float ambient_light[];


	//GPS delay stuff
	public static final int delayBetweenGPS_Records = 100;  //every 500mS log Geo date in Queue.
	//Valid size of detected Object
	public static final int lockOutDelay = 3000; // 3 seconds
	public static final int CROPPED_BORDER = 20;
	public static final int maxSizeofDetectableObject = 200;
	public static final int minSizeofDetectableObject = 30;
	public static final double maxAreaofDetectableObject = maxSizeofDetectableObject * maxSizeofDetectableObject;
	public static final double minAreaofDetectableObject = minSizeofDetectableObject * minSizeofDetectableObject;
	//default capture width and height
	//oops too big for SII
	public static final int FRAME_WIDTH = 1280;
	public static final int FRAME_HEIGHT = 720;
	//TODO Need to merge upp and lower RED : Lower is 0-25 Upper is 155-180
	//see ImageFrameToggle
//	public static final int H_MIN =         155;
//	public static final int H_MAX =         180;
	public static final int H_MIN = 0;
	public static final int H_MAX = 15;
	public static final int S_MIN = 100;
	public static final int S_MAX = 256;
	public static final int V_MAX = 256;
	public static final int H_NOR = 15;
	private static final String TAG = "OCVSpeedSignFinder::Activity";
	public static int V_MIN = 100;
	private static boolean hashDefineTrue = false;
	//  public static final int FRAME_WIDTH =   320;
//  public static final int FRAME_HEIGHT =  240;
//	public static final int FRAME_WIDTH =   800;
//	public static final int FRAME_HEIGHT =  600;
	private static final boolean doDebug = true;
	private static final boolean doFancyDisplay = true;
	private static final boolean extraErrode = true;
	private static final boolean extraDilates = true;
	private static boolean alertOnGreenLight = false;
	private static boolean ImageFrameToggle = false;
	private static boolean SensorVisual = false;
	public ArrayList<String> aPAKqueue = new ArrayList<String>();
	public Mat cropped2;
	public boolean foundCircle;                 // Used to trigger writing GPS to file.(indirectly)
	public boolean LockedOut;                   // Caught a sign so take it easy for a while
	public String myInternalFile = "ToBeEmailed";// used to R/W internal file.
	private boolean hasMenuKey;                 // Needed to build correct android menu
	private double lastFullArea = 0;            // used for Green Light detection
	private Handler handler = new Handler();    // used for timers
	private CameraBridgeViewBase mOpenCvCameraView; //used by openCV
	private Mat mCameraFeed;
	private Mat mThreshold;
	private Mat cropped;
	private LocListener gpsListener = new LocListener();    // used by GPS
	private LocationManager locManager;                     // used by GPS
	private pakSensors pakS = new pakSensors();

	private Rect GreenLightRect = null;
	private double xScale;
	private double yScale;


	//	private Size mSize0;
//	private Size mSizeRgba;
//	private Size mSizeRgbaInner;

	//	private Scalar mColorsRGB[];
//	private Scalar mColorsHue[];
	/**
	 * Loads OpenCV
	 */
	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {

		@Override
		public void onManagerConnected(int status) {
			switch(status) {
				case LoaderCallbackInterface.SUCCESS: {
					Log.i(TAG, "OpenCV loaded successfully");
					mOpenCvCameraView.enableView();
				}
				break;
				default: {
					super.onManagerConnected(status);
				}
				break;
			}
		}
	};


//   // uncommemt in camera_image_view.xml before uncommenting this/
//	View.OnClickListener myhandler1;
//	{
//		myhandler1 = new View.OnClickListener() {
//			public void onClick(View v) {
//				Toast.makeText(speedsignfinderActivity.this, getString(R.string.thres), Toast.LENGTH_SHORT).show();
//				// it was the 1st button
//			}
//		};
//	}

//	public void onOptionsMenuClosed(Menu iDontUse) {
//
//		ActionBar actionBar = getActionBar();
//		if(actionBar != null) {
//			actionBar.hide();
//		}
//
//	}


	SensorEventListener myAccelerometerSensorEventListener = new SensorEventListener() {
		@Override
		public void onSensorChanged(SensorEvent event) {
			acceleration = event.values;

		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {


		}
	};


	SensorEventListener myGyroSensorEventListener = new SensorEventListener() {
		@Override
		public void onSensorChanged(SensorEvent event) {
			gyration = event.values;

		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {


		}
	};


	SensorEventListener myLuxSensorEventListener = new SensorEventListener() {
		@Override
		public void onSensorChanged(SensorEvent event) {
			ambient_light = event.values;

		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {


		}
	};


	/**
	 * Called when the activity is first created.
	 */
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "called onCreate");
		super.onCreate(savedInstanceState);

		hasMenuKey = ViewConfiguration.get(this).hasPermanentMenuKey();

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		// TODO correct for difference between screensize and displayed size touches don't line up

		Display display = getWindowManager().getDefaultDisplay();
		android.graphics.Point size;
		size = new android.graphics.Point();
		display.getSize(size);
		xScale = (size.x / (double) FRAME_WIDTH);
		yScale = (size.y / (double) FRAME_HEIGHT);


		// Setup GPS Queue.
		// push 2 entries into the queue for a (2 x delayBetweenGPS_Records) delay
		for(int x = 0; x < 2; x++) {
			aPAKqueue.add("Dummy Data for Queue ".concat(String.valueOf(x)));
		}


		handler.postDelayed(timedGPSqueue, delayBetweenGPS_Records);   //Start timer


		if(hasMenuKey) {
			requestWindowFeature(Window.FEATURE_NO_TITLE);
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
			setContentView(R.layout.camera_image_surface_view);
		} else {
			//Make actionBar translucent (1)
			getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
			setContentView(R.layout.camera_image_surface_view);
			// Make actionBar translucent (2)
			try {
				getActionBar().setBackgroundDrawable(new ColorDrawable(Color.argb(128, 0, 0, 0)));
			} catch(Exception e) {
				e.printStackTrace();
			}
		}

//		ActionBar actionBar = getActionBar();
//		if(actionBar != null) {
//			actionBar.hide();
//		}


//		// uncommemt in camera_image_view.xml before uncommenting this
//		b1 = (Button) findViewById(R.id.button);
//		b1.setOnClickListener(myhandler1);
//


		// Write to File (internal)
		//ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());
		//File directory = contextWrapper.getDir(getString(R.string.LatLongFile_txt), Context.MODE_APPEND);
		//myInternalFile = new File(directory, getString(R.string.LayLongStorage));


		// Turn on teh GPS.     set up GPS
		locManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		//Create an instance called gpsListener of the class I added called LocListener which is an implements ( is extra to) android.location.LocationListener
		//Start the GPS listener
		locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, gpsListener);


		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera_image_view);
		mOpenCvCameraView.setCvCameraViewListener(this);
		//confirm size
		mOpenCvCameraView.setMaxFrameSize(FRAME_WIDTH, FRAME_HEIGHT);
		mOpenCvCameraView.enableFpsMeter();
		LockedOut = false;
		//pakS.init();


		//Sensor

		sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
		lux = sm.getDefaultSensor(Sensor.TYPE_LIGHT);


		sm.registerListener(myAccelerometerSensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		sm.registerListener(myGyroSensorEventListener, gyro, SensorManager.SENSOR_DELAY_NORMAL);
		sm.registerListener(myLuxSensorEventListener, lux, SensorManager.SENSOR_DELAY_NORMAL);


	}

	@Override
	public void onPause() {
		super.onPause();
		if(mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
		//Create an instance called gpsListener of the class I added called LocListener which is an implements ( is extra to) android.location.LocationListener
		//Stop the GPS listener
		locManager.removeUpdates(gpsListener);
//		ActionBar actionBar = getActionBar();
//		if(actionBar != null) {actionBar.hide();}

	}

	@Override
	public void onResume() {
		super.onResume();

		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
		//Start the GPS listener
		locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, gpsListener);
	}

	public void onDestroy() {
		super.onDestroy();
		if(mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
		//Stop the GPS listener
		locManager.removeUpdates(gpsListener);
		// Turn Off the GPS
		locManager = null;
	}


	//TODO Use this to select a blob in the traffic light code it catches an anywhere touch
	// this is to open the menu for buttonless devices
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// MotionEvent object holds X-Y values
		if(event.getAction() == MotionEvent.ACTION_DOWN) {
			String text = "You click at x = " + event.getX() + " and y = " + event.getY();
			Toast.makeText(speedsignfinderActivity.this, text, Toast.LENGTH_SHORT).show();
			alertOnGreenLight = true;
			GreenLightRect = new Rect(new Point(event.getX() - 50, event.getY() - 50), new Point(event.getX() + 50, event.getY() + 50));
			//Core.rectangle(mCameraFeed,GreenLightRect.tl(),GreenLightRect.br(), new Scalar(0, 255, 0), -1);
			Core.circle(mCameraFeed, new Point(event.getX() / xScale, event.getY() / yScale), 30, new Scalar(255, 255, 0), -1);

//			ActionBar actionBar = getActionBar();
//			if(actionBar != null) {actionBar.show();}
			//openOptionsMenu();

		}

		return super.onTouchEvent(event);
	}


	//MENU CODE START
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Log.i(TAG, "called onCreateOptionsMenu");
		getMenuInflater().inflate(R.menu.menu, menu);  //gets the menu entries from menu.xml
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
		PackageInfo pinfo = null;
		try {
			pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
		} catch(PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}
		//int versionNumber = pinfo.versionCode;
		//String versionName = pinfo.versionName;


		switch(item.getItemId()) {
//			case R.id.fancy:
//				doFancyDisplay = !doFancyDisplay;
//				Toast.makeText(speedsignfinderActivity.this, getString(R.string.fancy), Toast.LENGTH_SHORT).show();
//				item.setTitle(getString(R.string.fancy).concat(" ").concat(String.valueOf(doFancyDisplay)));
//				return true;

			case R.id.thres:
				hashDefineTrue = !hashDefineTrue;
				//Toast.makeText(speedsignfinderActivity.this, getString(R.string.thres), Toast.LENGTH_SHORT).show();
				item.setTitle(getString(R.string.thres).concat(" ").concat(String.valueOf(hashDefineTrue)));
				return true;


			case R.id.popup:
				hashDefineTrue = true;


				final Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
				dialog.setContentView(R.layout.threshold_dialog);

				dialog.setCancelable(true);
				//there are a lot of settings, for dialog, check them all out!
				dialog.show();


				SeekBar seekbar = (SeekBar) dialog.findViewById(R.id.size_seekbar);
				seekbar.setProgress(V_MIN);
				//	Toast.makeText(speedsignfinderActivity.this,"hellp PB1", Toast.LENGTH_SHORT).show();
				//final TextView tv_dialog_size = (TextView) dialog.findViewById(R.id.set_size_help_text);

				seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

					@Override
					public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
						V_MIN = progress;
					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {

					}

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
						timerDelayRemoveDialog(400, dialog); //closes the dialog after 2000mS
					}
				});

				//
				//			SeekBar seekbar2 = (SeekBar) dialog.findViewById(R.id.size_seekbar2);
				//			//	Toast.makeText(speedsignfinderActivity.this,"hellp PB2", Toast.LENGTH_SHORT).show();
				//			//	final TextView tv_dialog_size2 = (TextView) dialog.findViewById(R.id.set_size_help_text);
				//
				//				seekbar2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				//					@Override
				//					public void onProgressChanged(SeekBar seekBar2, int progress, boolean fromUser) {
				//					}
				//
				//					@Override
				//					public void onStartTrackingTouch(SeekBar seekBar2) {
				//					}
				//
				//					@Override
				//					public void onStopTrackingTouch(SeekBar seekBar2) {
				//						//Toast.makeText(speedsignfinderActivity.this,String.valueOf(  seekBar2).concat("Stop 2"), Toast.LENGTH_SHORT).show();
				//
				//					}
				//				});

				hashDefineTrue = true;

				return true;


			case R.id.temp:
				SensorVisual = !SensorVisual;
				//Toast.makeText(speedsignfinderActivity.this, String.valueOf(pakS.getLastX()), Toast.LENGTH_SHORT).show();
				return true;


//			case R.id.debug:
//				doDebug = !doDebug;
//				Toast.makeText(speedsignfinderActivity.this, getString(R.string.debug), Toast.LENGTH_SHORT).show();
//				item.setTitle(getString(R.string.debug).concat(" ").concat(String.valueOf(doDebug)));
//				return true;
//
//			case R.id.erode:
//				extraErrode = !extraErrode;
//				Toast.makeText(speedsignfinderActivity.this, getString(R.string.erode), Toast.LENGTH_SHORT).show();
//				item.setTitle(getString(R.string.erode).concat(" ").concat(String.valueOf(extraErrode)));
//				return true;
//
//			case R.id.dilate:
//				extraDilates = !extraDilates;
//				Toast.makeText(speedsignfinderActivity.this, getString(R.string.dilate), Toast.LENGTH_SHORT).show();
//				item.setTitle(getString(R.string.dilate).concat(" ").concat(String.valueOf(extraDilates)));
//				return true;

			case R.id.showversion:
				Toast.makeText(speedsignfinderActivity.this, (pinfo != null ? pinfo.versionName : null), Toast.LENGTH_SHORT).show();
				return true;

			case R.id.lightgreen:
				alertOnGreenLight = true;
				Toast.makeText(speedsignfinderActivity.this, getString(R.string.lightGreen), Toast.LENGTH_SHORT).show();
				GreenLightRect = new Rect(new Point(100, 100), new Point(200, 200));
				Core.rectangle(mCameraFeed, GreenLightRect.tl(), GreenLightRect.br(), new Scalar(0, 255, 0), -1);
				return true;

			case R.id.email:
				//intent to send email
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/html");
				intent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.emailAddress)});
				intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.emailSubject).concat(" ").concat(pinfo != null ? pinfo.versionName : null));
				intent.putExtra(Intent.EXTRA_TEXT, pakReadInternal());
				startActivity(Intent.createChooser(intent, "Send Email"));


				//There is no way to see if the email was sent?
				// Clear the content of the internal file by not appending
				pakStartInternalFile();

				return true;


/*	TIP			regex find expression  = ^(.*)"@string/(.*)"(,)(.*$)
				Toast.makeText(speedsignfinderActivity.this, "@string/dilate", Toast.LENGTH_SHORT).show();
				---------------------------------------------         ++++++ -----------------------------
				regex replace expression  =                $1getString(R.string.$2)$3$4
				Toast.makeText(speedsignfinderActivity.this, getString(R.string.dilate), Toast.LENGTH_SHORT).show();
				---------------------------------------------                   ++++++ -----------------------------


				Find        ^(.*)tion=(.*)&heading=(.*)></a>

				Replace     <iframe width="425" height="350" frameborder="0" scrolling="no" marginheight="0" marginwidth="0" src="https://maps.google.com/maps?f=q&amp;source=embed&amp;hl=en&amp;geocode=&amp;sll=41.866699,-103.650731&amp;sspn=0.000978,0.002064&amp;t=h&amp;ie=UTF8&amp;hq=&amp;spn=0.139225,0.264187&amp;z=13&amp;layer=c&amp;cbll=$2&amp;cbp=12,$3,,0,0&amp;output=svembed"></iframe>








*/
			default:
				return super.onOptionsItemSelected(item);
		}
	}    //MENU CODE END


	public void onCameraViewStarted(int width, int height) {
		mThreshold = new Mat();
		mCameraFeed = new Mat();
		cropped = new Mat();
		cropped2 = new Mat();
	}

	public void onCameraViewStopped() {
		// Explicitly deallocate Mats
		if(mCameraFeed != null)
			mCameraFeed.release();
		if(mThreshold != null)
			mThreshold.release();
		if(cropped != null)
			cropped.release();
		mCameraFeed = null;
		mThreshold = null;
		cropped = null;
	}

	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

//		int top, bottom, left, right;
		Mat mTemp = new Mat();
		Mat mHierarchy = new Mat();
		Rect boundRect;
		Mat erodeElement = getStructuringElement(MORPH_RECT, new Size(2, 2));
		Mat dilateElement = getStructuringElement(MORPH_RECT, new Size(3, 3));

		//ImageFrameToggle = !ImageFrameToggle;

		foundCircle = false;
//
//
//		Mat m;
//		mCameraFeed = Highgui.imread("/storage/sdcard0/Download/color-chart.png");
		mCameraFeed = inputFrame.rgba();
//
//		Cut the screen in half cos too much data to process
//
//		mThreshold = new Mat(mCameraFeed, new Rect(0,0,mCameraFeed.width()/2,mCameraFeed.height())).clone();
//
//		asdadImgproc.pyrDown(mCameraFeed, mCameraFeed, new Size(400,400));
//		Imgproc.pyrUp(mCameraFeed, mCameraFeed);
//


		cvtColor(mCameraFeed, mThreshold, COLOR_RGB2HSV);

		//every second pass does
		if(ImageFrameToggle) {
			Core.inRange(mThreshold, new Scalar((180 - H_MAX), S_MIN, V_MIN), new Scalar(180, S_MAX, V_MAX), mThreshold);
		} else {
			Core.inRange(mThreshold, new Scalar(H_MIN, S_MIN, V_MIN), new Scalar(H_MAX, S_MAX, V_MAX), mThreshold);
		}

//		    TODO was dropping erode/dilate a bad idea (it saved LOTS of cpu time)
		erode(mThreshold, mThreshold, erodeElement);


		if(extraErrode) erode(mThreshold, mThreshold, erodeElement);


		dilate(mThreshold, mThreshold, dilateElement);


		if(extraDilates) {
			dilate(mThreshold, mThreshold, dilateElement);
			dilate(mThreshold, mThreshold, dilateElement);
		}


		List<MatOfPoint> contours = new ArrayList<MatOfPoint>();


		mThreshold.copyTo(mTemp);
		//TODO  findcontours seems to corrupt mThreshold using mTemp DO I NEED THIS?
		Imgproc.findContours(mTemp, contours, mHierarchy, Imgproc.CV_RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);


		Iterator<MatOfPoint> each = contours.iterator();
		int k = -1;
		double fullArea = 0;
		// Cycle through each found object
		while(each.hasNext()) {
			k++;
			MatOfPoint wrapper = each.next();
			double area = Imgproc.contourArea(wrapper);
			///fullArea = fullArea + area;
			// Check the object size
			//if (boundRect.width > 30 && boundRect.width < 200 && boundRect.height > 30 && boundRect.height < 200)
			//if((area > 900) && (area < 40000))
			if((area > minAreaofDetectableObject) && (area < maxAreaofDetectableObject)) {
				//Cut out the image segment where the object (possible circle) is into its own Mat (smaller image)
				boundRect = setContourRect(contours, k);

				//If the area of the blob is roughly equal to pi/4 of the area of the bounding rectangle then it then it is a circle
				//todo  test for Hollow, if not hollow it is not a sign as they are hollow.
				if((int) (area / 100) == (int) (boundRect.area() * 3.1416927 / 400)) {
					double aSquare;

					if(boundRect.height > boundRect.width) {
						aSquare = ((double) boundRect.height / (double) boundRect.width);
					} else {
						aSquare = ((double) boundRect.width / (double) boundRect.height);
					}

					if(aSquare < 1.5) {
						if(doDebug) {
							Core.rectangle(mThreshold, boundRect.tl(), boundRect.br(), new Scalar(0, 255, 0), 5, 8, 0);
							Core.rectangle(mCameraFeed, boundRect.tl(), boundRect.br(), new Scalar(0, 255, 0), 5, 8, 0);                        //cropped.copyTo(roi);
						}
						cropped = new Mat(mCameraFeed, boundRect).clone();
						Mat croppedT = new Mat(mThreshold, boundRect).clone();
						if(doFancyDisplay)
							copyMakeBorder(cropped, cropped, CROPPED_BORDER, CROPPED_BORDER, CROPPED_BORDER, CROPPED_BORDER, BORDER_CONSTANT, new Scalar(0, 0, 0));
						copyMakeBorder(croppedT, croppedT, CROPPED_BORDER, CROPPED_BORDER, CROPPED_BORDER, CROPPED_BORDER, BORDER_CONSTANT, new Scalar(0, 0, 0));

						//put HOUGH in here so that is only gets called if rectangle is the correct size range
						Mat circles = new Mat();
						HoughCircles(croppedT, circles, CV_HOUGH_GRADIENT, 1, cropped.rows() / 8, 100, H_NOR, cropped.rows() / 4, cropped.rows() / 2);

						if(circles.cols() > 0) {
							//Don't need to loop through ALL circles as the 1st circle will set foundCircle
							int x = 0;
							//for ( ; x < circles.cols(); x++)
							{
								double vCircle[] = circles.get(0, x);

								if(vCircle == null)
									break;

								if(doDebug) {
									Point pt = new Point(Math.round(vCircle[0]), Math.round(vCircle[1]));
									int radius = (int) Math.round(vCircle[2]);
									// draw the found circle
									Core.circle(cropped, pt, radius, new Scalar(0, 255, 0), 5);
									//    Core.circle(cropped, pt, 3, new Scalar(0,0,255), 2);
								}

								if(doFancyDisplay) cropped.copyTo(cropped2);

								if(!LockedOut) //if the sound has been played in the last 2000mS don't do it again.
								{
									LockedOut = true;
									// This code plays the default beep
									try {
										Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
										Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
										r.play();
									} catch(Exception e) {
										e.printStackTrace();
									}
									// write Lat/Long to file


									foundCircle = true; // this tells GPS timer to write to file.


									// see private Runnable timedTask = new Runnable() above
									handler.postDelayed(timedTask, lockOutDelay);

								}
							}
						}
					} else {
						if(doDebug) {
							Core.rectangle(mThreshold, boundRect.tl(), boundRect.br(), new Scalar(0, 0, 255), 5, 8, 0);
							Core.rectangle(mCameraFeed, boundRect.tl(), boundRect.br(), new Scalar(0, 0, 255), 5, 8, 0);                        //cropped.copyTo(roi);
						}
					}
				}
			}
		}

		// Sensor Visual
		if(SensorVisual) {
			//Core.putText(mCameraFeed, String.valueOf(acceleration[0]), new Point(300, 200), 1, 2, new Scalar(128, 0, 0), 1);
			Core.circle(mCameraFeed, new Point(300, 300), (int) acceleration[0] * (int) acceleration[0], new Scalar(0, 255, 0), -1);
			Core.circle(mCameraFeed, new Point(400, 300), (int) acceleration[1] * (int) acceleration[1], new Scalar(255, 0, 0), -1);
			Core.circle(mCameraFeed, new Point(350, 200), (int) acceleration[2] * (int) acceleration[2], new Scalar(0, 0, 255), -1);

			Core.circle(mCameraFeed, new Point(600, 300), (int) gyration[0] * (int) gyration[0] * 10, new Scalar(0, 255, 0), -1);
			Core.circle(mCameraFeed, new Point(700, 300), (int) gyration[1] * (int) gyration[1] * 10, new Scalar(255, 0, 0), -1);
			Core.circle(mCameraFeed, new Point(650, 200), (int) gyration[2] * (int) gyration[2] * 10, new Scalar(0, 0, 255), -1);

			Core.circle(mCameraFeed, new Point(650, 200), (int) ambient_light[0], new Scalar(0, 0, 255), -1);
		}

		if(doFancyDisplay) {
			if(cropped2.cols() > 0)
			//if (foundCircle)
			{
				cropped2.copyTo(mCameraFeed.submat(new Rect(0, 0, cropped2.width(), cropped2.height())));
				//        String latitude,longitude,
				//        latitude = String.valueOf( gpsListener.getLat());
				//        longitude = String.valueOf(gpsListener.getLon() );
//				String myConcatedString;
//				myConcatedString = String.valueOf(gpsListener.getLat()).concat(",").concat(String.valueOf(gpsListener.getLon()));
//				Core.putText(mCameraFeed, myConcatedString, new Point(50, 250), 1, 2, new Scalar(0, 0, 255), 2);
			}
		}
		//reference square

//		if(doDebug) {
//			Core.rectangle(mCameraFeed, new Point(400, 400), new Point(400 + minSizeofDetectableObject, 400 + minSizeofDetectableObject), new Scalar(255, 255, 255), 2, 8, 0);
//			Core.rectangle(mCameraFeed, new Point(300, 300), new Point(300 + maxSizeofDetectableObject, 300 + maxSizeofDetectableObject), new Scalar(255, 255, 255), 2, 8, 0);
//		}


		// pop the threshold image back in the unused right side of the screen image
		//TODO this is crashing the app, why
		//mThreshold.copyTo(mCameraFeed.submat(new Rect (mCameraFeed.width()/2-1,0,mCameraFeed.width()-1,mCameraFeed.height()-1)));


		//todo Green Light : this finds the number of white pixels in the selected roi square
		// no point reading area from toggling frames so only read one
		if(!ImageFrameToggle && alertOnGreenLight) {
			if(GreenLightRect != null) {

				int roiCount = 0;
				try {
					roiCount = (Core.countNonZero(new Mat(mThreshold, GreenLightRect)));
				} catch(Exception e) {
					//Log.i(TAG, "GreenLightRect outside area  failed");
					//Toast.makeText(speedsignfinderActivity.this,"Area Outside Sensing Range, selecting nearer the center of the screen", Toast.LENGTH_LONG).show();
					e.printStackTrace();
				}

				Core.circle(mCameraFeed, new Point(0, (FRAME_HEIGHT / 2)), 50, new Scalar(128, 0, 255), 2);
				Core.circle(mCameraFeed, new Point(0, FRAME_HEIGHT / 2), (int) ((lastFullArea / roiCount) * 50), new Scalar(128, 0, 255), -1);

				//Core.putText(mCameraFeed, String.valueOf(lastFullArea), new Point(300, 200), 1, 4, new Scalar(128, 0, 0), 1);
				//Core.putText(mCameraFeed, String.valueOf(roiCount), new Point(300, 100), 1, 4, new Scalar(128, 0, 0), 1);
				if(lastFullArea >= roiCount) {
					try {
						Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
						Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
						r.play();
					} catch(Exception e) {
						e.printStackTrace();
					}
					alertOnGreenLight = false;

				}
				lastFullArea = (roiCount * 0.8);
			}
		}


		//Added a menu to switch between Threshold view and Camera view
		if(hashDefineTrue) {
			//return null;

			return mThreshold;
			//return cropped;
		} else
			return mCameraFeed;
	}

	/**
	 * This code is to create a delay after playing a foundCircle sound so that is doesn't play 10's at once.
	 * This is what is called once the delay expires after the INTENT called in the Hough block
	 */
	private Runnable timedTask = new Runnable() {

		@Override
		public void run() {
			LockedOut = false;
//			handler.postDelayed(timedTask, 2000);   //not doing repeating so not needed
		}
	};
	/**
	 * This timer is to push the Gro data (lat,long etc) into a Queue every x milliseconds.
	 * such that the geo data can be read back, delayed, when a sign is found,
	 */
	private Runnable timedGPSqueue;

	{
		timedGPSqueue = new Runnable() {
			@Override
			public void run() {
				//http://maps.googleapis.com/maps/api/streetview?size=480x320&fov=90&heading=%2090&pitch=0&sensor=false&location=-33.7165435,150.961225
				//toWR.generateNoteOnSD(String.valueOf(String.valueOf(gpsListener.getLat()).concat(",").concat(String.valueOf(gpsListener.getLon()))));
				String whatToWrite;
				/*
				<a href="http://maps.googleapis.com/maps/api/streetview?size=480x320&fov=90&heading=%20200&pitch=0&sensor=false&location=-33.69816467,150.9637255125">IMAGE CLICK</a>
				http://maps.googleapis.com/maps/api/streetview?size=480x320&fov=90&pitch=0&sensor=false&location=-33.69816467,150.9637255125&heading=50
				Write new entry
				*/


				whatToWrite = String.valueOf(LocListener.getLat());
				whatToWrite = whatToWrite.concat(",");
				whatToWrite = whatToWrite.concat(String.valueOf(LocListener.getLon()));
				whatToWrite = whatToWrite.concat(getString(R.string.wwwMiddle2));
				whatToWrite = whatToWrite.concat(String.valueOf(LocListener.getBearing_45()));
				boolean GPSqueueIO = aPAKqueue.add(whatToWrite);
				if(!GPSqueueIO) Log.i(TAG, "timedGPSqueue  aPAKqueue.add(whatToWrite) failed");
				/*
				Don't get lost here there is multiple entries in the queue at this point.
				above writes the lat,long of write now. Below reads the same data but from several
				milliseconds ago, how long ago is determined my the numbers of entries in the queue
				times delayBetweenGPS_Record.

				So if delayBetweenGPS_Records = 200mS
				and we write 6x aPAKqueue.add("dummy") in onCreate()
				Then the delay is 6x200mS = 1.2 seconds back in time
				*/
				whatToWrite = getString(R.string.CrLf);
				whatToWrite = whatToWrite.concat(getString(R.string.wwwMiddle1));

				// the get(0) is in here
				whatToWrite = whatToWrite.concat(aPAKqueue.get(0)); // remember this is the lat,long, wwwMiddle2 and  bearing. see lines just above
				whatToWrite = whatToWrite.concat(getString(R.string.wwwMiddle3));
				whatToWrite = whatToWrite.concat(randomUUID().toString());
				whatToWrite = whatToWrite.concat(",V_min,");
				whatToWrite = whatToWrite.concat(String.valueOf(V_MIN));
				whatToWrite = whatToWrite.concat(",Speed,");
				whatToWrite = whatToWrite.concat(String.valueOf(LocListener.getSpeed()));
				whatToWrite = whatToWrite.concat(",Lat,");
				whatToWrite = whatToWrite.concat(String.valueOf(LocListener.getLat()));
				whatToWrite = whatToWrite.concat(",Lon");
				whatToWrite = whatToWrite.concat(String.valueOf(LocListener.getLon()));
				whatToWrite = whatToWrite.concat(",Alt,");
				whatToWrite = whatToWrite.concat(String.valueOf(LocListener.getAlt()));
				whatToWrite = whatToWrite.concat(getString(R.string.wwwMiddle4));

				aPAKqueue.remove(0);


				// read oldest object value and remove that object from queue
				if(foundCircle) {
					foundCircle = false;
					pakWritetoInternal(whatToWrite);
				}


				handler.postDelayed(timedGPSqueue, delayBetweenGPS_Records);   //repeating so needed
			}
		};
	}

	public static Rect setContourRect(List<MatOfPoint> contours, int k) {
		Rect boundRect = new Rect();
		Iterator<MatOfPoint> each = contours.iterator();
		int j = 0;
		while(each.hasNext()) {
			MatOfPoint wrapper = each.next();
			if(j == k) {
				return Imgproc.boundingRect(wrapper);
			}
			j++;
		}
		return boundRect;
	}


	private void pakWritetoInternal(String whatToWrite) {
		FileOutputStream fos;
		try {
			// Note APPEND  true                       ----
			fos = openFileOutput(myInternalFile, MODE_APPEND);
			fos.write(whatToWrite.getBytes());
			fos.close();
		} catch(IOException e) {
			e.printStackTrace();
		}

	}

	private void pakStartInternalFile() {
		FileOutputStream fos;
		try {
			// Note OVER WRIGTH                         ----
			fos = openFileOutput(myInternalFile, MODE_PRIVATE);
//			fos.write("\n\r".getBytes());
			fos.close();
		} catch(IOException e) {
			e.printStackTrace();
		}

	}

	private String pakReadInternal() {
		FileInputStream fis;

		String myData = getString(R.string.wwwHead);

		try {
			fis = openFileInput(myInternalFile);
		} catch(FileNotFoundException e) {
			e.printStackTrace();
			return "No Data";
		}
		//DataInputStream in = new DataInputStream(fis);
		//BufferedReader br = new BufferedReader(new InputStreamReader(in));
		String strLine;
		try {
			StringBuffer fileContent = new StringBuffer("");
			byte[] buffer = new byte[1024];
			while(fis.read(buffer) != -1) {
				fileContent.append(new String(buffer));
			}
			myData = myData + fileContent;
			fis.close();
		} catch(IOException e) {
			e.printStackTrace();
			return "No Data";
		}

		myData = myData.concat(getString(R.string.wwwTail));
//		myData = myData + '\n';
//		myData = myData + '\r';
		return myData;
	}

	public void timerDelayRemoveDialog(long time, final Dialog d) {
		new Handler().postDelayed(new Runnable() {
			public void run() {
				hashDefineTrue = false;
				d.dismiss();
			}
		}, time);
	}


//
//	/** This code is to create a delay after playing a foundCircle sound so that is doesn't play 10's at once.
//	 This is what is called once the delay expires after the INTENT called in the Hough block */
//	private Runnable timedTaskTresholdDialog = new Runnable() {
//
//		@Override
//		public void run() {
//			LockedOut = false;
////			handler.postDelayed(timedTask, 2000);   //not doing repeating so not needed
//		}
//	};
//


}

