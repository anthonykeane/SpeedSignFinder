package com.anthonykeane.speedsignfinder;


import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
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

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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


public class speedsignfinderActivity extends Activity implements CvCameraViewListener2 {

	//Internal Storage
	File myInternalFile;


	//Valid size of detected Object
	public static final int CROPPED_BORDER = 20;
	public static final int maxSizeofDetectableObject = 200;
	public static final int minSizeofDetectableObject = 30;
	public static final double maxAreaofDetectableObject = maxSizeofDetectableObject * maxSizeofDetectableObject;
	public static final double minAreaofDetectableObject = minSizeofDetectableObject * minSizeofDetectableObject;


	//default capture width and height
	//oops too big for SII
	public static final int FRAME_WIDTH = 1280;
	public static final int FRAME_HEIGHT = 720;
//  public static final int FRAME_WIDTH =   320;
//  public static final int FRAME_HEIGHT =  240;
//	public static final int FRAME_WIDTH =   800;
//	public static final int FRAME_HEIGHT =  600;


	//TODO Need to merge upp and lower RED : Lower is 0-25 Upper is 155-180
//	public static final int H_MIN =         155;
//	public static final int H_MAX =         180;
	public static final int H_MIN = 0;
	public static final int H_MAX = 15;
	public static final int S_MIN = 100;
	public static final int S_MAX = 256;
	public static final int V_MIN = 100;
	public static final int V_MAX = 256;
	public static final int H_NOR = 15;


	private static final String TAG = "OCVSpeedSignFinder::Activity";


	//MENU

	private static boolean hashDefineTrue = false;
	private static boolean doDebug = true;
	private static boolean doFancyDisplay = true;
	private static boolean extraErrode = true;
	private static boolean extraDilates = true;

	// end MENU

	private static boolean alertOnGreenLight = false;
	private double lastFullArea = 0;


	public Mat cropped2;
	public boolean foundCircle;
	public boolean LockedOut;

	private Handler handler = new Handler();
	private CameraBridgeViewBase mOpenCvCameraView;
	//gps
	private LocListener gpsListener = new LocListener();
	//	private Size mSize0;
//	private Size mSizeRgba;
//	private Size mSizeRgbaInner;
	private Mat mCameraFeed;
	private Mat mThreshold;
	private Mat cropped;
	//	private Scalar mColorsRGB[];
//	private Scalar mColorsHue[];
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

	//This code is to create a delay after playing a foundCircle sound so that is doesn't play 10's at once.
	//This is what is called once the delay expires after the INTENT called in the Hough block
	private Runnable timedTask = new Runnable() {

		@Override
		public void run() {
			LockedOut = false;
//			handler.postDelayed(timedTask, 2000);   //not doing repeating so not needed
		}
	};


	public speedsignfinderActivity() {
		Log.i(TAG, "Instantiated new " + this.getClass());
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

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "called onCreate");
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.image_manipulations_surface_view);


		// Write to File (internal)
		ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());
		File directory = contextWrapper.getDir(getString(R.string.LatLongFile_txt), Context.MODE_PRIVATE);
		myInternalFile = new File(directory, getString(R.string.LayLongStorage));


		// set up GPS
		LocationManager locManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		//Create an instance called gpsListener of the class I added called LocListener which is an implements ( is extra to) android.location.LocationListener
		locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, gpsListener);


		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.image_manipulations_activity_surface_view);
		mOpenCvCameraView.setCvCameraViewListener(this);
		//confirm size
		mOpenCvCameraView.setMaxFrameSize(FRAME_WIDTH, FRAME_HEIGHT);
		mOpenCvCameraView.enableFpsMeter();
		LockedOut = false;
	}

	@Override
	public void onPause() {
		super.onPause();
		if(mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
		//locationManager.removeUpdates(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
		//locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, this);
	}

	public void onDestroy() {
		super.onDestroy();
		if(mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
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
		switch(item.getItemId()) {
//			case R.id.fancy:
//				doFancyDisplay = !doFancyDisplay;
//				Toast.makeText(speedsignfinderActivity.this, getString(R.string.fancy), Toast.LENGTH_SHORT).show();
//				item.setTitle(getString(R.string.fancy).concat(" ").concat(String.valueOf(doFancyDisplay)));
//				return true;

			case R.id.thres:
				hashDefineTrue = !hashDefineTrue;
				Toast.makeText(speedsignfinderActivity.this, getString(R.string.thres), Toast.LENGTH_SHORT).show();
				item.setTitle(getString(R.string.thres).concat(" ").concat(String.valueOf(hashDefineTrue)));
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

			case R.id.lightgreen:
				alertOnGreenLight = true;
				Toast.makeText(speedsignfinderActivity.this, getString(R.string.lightGreen), Toast.LENGTH_SHORT).show();
				return true;

			case R.id.email:
				//TODO add intent to send email
				// This code plays the default beep

				PackageInfo pinfo = null;
				try {
					pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
				} catch(PackageManager.NameNotFoundException e) {
					e.printStackTrace();
				}
				//int versionNumber = pinfo.versionCode;
				//String versionName = pinfo.versionName;


				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/html");
				intent.putExtra(Intent.EXTRA_EMAIL, getString(R.string.emailAddress));
				intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.emailSubject).concat(" ").concat(pinfo != null ? pinfo.versionName : null));
				intent.putExtra(Intent.EXTRA_TEXT, pakReadInternal());
				startActivity(Intent.createChooser(intent, "Send Email"));


				//TODO is there a way to see if the email was sent?
				// Clear the content of the internal file by not appending
				pakStartInternalFile();

				return true;


/*	TIP			regex find expression  = ^(.*)"@string/(.*)"(,)(.*$)
				Toast.makeText(speedsignfinderActivity.this, "@string/dilate", Toast.LENGTH_SHORT).show();
				---------------------------------------------         ++++++ -----------------------------
				regex replace expression  =                $1getString(R.string.$2)$3$4
				Toast.makeText(speedsignfinderActivity.this, getString(R.string.dilate), Toast.LENGTH_SHORT).show();
				---------------------------------------------                   ++++++ -----------------------------
*/
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	//MENU CODE END


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
		Core.inRange(mThreshold, new Scalar(H_MIN, S_MIN, V_MIN), new Scalar(H_MAX, S_MAX, V_MAX), mThreshold);

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
			fullArea = fullArea + area;
			// Check the object size
			//if (boundRect.width > 30 && boundRect.width < 200 && boundRect.height > 30 && boundRect.height < 200)
			//if((area > 900) && (area < 40000))
			if((area > minAreaofDetectableObject) && (area < maxAreaofDetectableObject)) {
				//Cut out the image segment where the object (possible circle) is into its own Mat (smaller image)
				boundRect = setContourRect(contours, k);
				double aSquare;

				if(boundRect.height > boundRect.width) {
					aSquare = ((double) boundRect.height / (double) boundRect.width);
				} else {
					aSquare = ((double) boundRect.width / (double) boundRect.height);
				}

				if(aSquare < 1.1 && aSquare > 0.9) {


					cropped = new Mat(mCameraFeed, boundRect).clone();
					Mat croppedT = new Mat(mThreshold, boundRect).clone();
					if(doFancyDisplay)
						copyMakeBorder(cropped, cropped, CROPPED_BORDER, CROPPED_BORDER, CROPPED_BORDER, CROPPED_BORDER, BORDER_CONSTANT, new Scalar(0, 0, 0));
					copyMakeBorder(croppedT, croppedT, CROPPED_BORDER, CROPPED_BORDER, CROPPED_BORDER, CROPPED_BORDER, BORDER_CONSTANT, new Scalar(0, 0, 0));
					if(doDebug) {
						Core.rectangle(mThreshold, boundRect.tl(), boundRect.br(), new Scalar(255, 255, 0), 2, 8, 0);
						Core.rectangle(mCameraFeed, boundRect.tl(), boundRect.br(), new Scalar(255, 255, 0), 2, 8, 0);                        //cropped.copyTo(roi);
					}

					//put HOUGH in here so that is only gets called if rectangle is the correct size range
					Mat circles = new Mat();
					HoughCircles(croppedT, circles, CV_HOUGH_GRADIENT, 1, cropped.rows() / 8, 100, H_NOR, (int) (cropped.rows() / 4), cropped.rows() / 2);

					if(circles.cols() > 0) {
						//TODO do I need to loop through ALL circles as the 1st circle will set foundCircle
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
							foundCircle = true;

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


								//http://maps.googleapis.com/maps/api/streetview?size=480x320&fov=90&heading=%2090&pitch=0&sensor=false&location=-33.7165435,150.961225
								//toWR.generateNoteOnSD(String.valueOf(String.valueOf(gpsListener.getLat()).concat(",").concat(String.valueOf(gpsListener.getLon()))));

								pakWritetoInternal(String.valueOf(gpsListener.getBearing()).concat(String.valueOf(String.valueOf(gpsListener.getLat()).concat(",").concat(String.valueOf(gpsListener.getLon())))));


								// see private Runnable timedTask = new Runnable() above
								handler.postDelayed(timedTask, 2000);

							}


						}
					}

				}
			}
		}
		// fullArea is the number of pixels in the Threshold image
		// this will alert if red reduces.
		String myConcatedString;
		myConcatedString = String.valueOf(lastFullArea).concat(",").concat(String.valueOf(fullArea));
		Core.putText(mCameraFeed, myConcatedString, new Point(50, 250), 1, 2, new Scalar(0, 0, 255), 2);

		if((lastFullArea * 0.9) > fullArea && alertOnGreenLight) {
			try {
				Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
				Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
				r.play();
			} catch(Exception e) {
				e.printStackTrace();
			}
			alertOnGreenLight = false;
			lastFullArea = 0;

		}
		lastFullArea = fullArea;
		if(doFancyDisplay) {
			if(cropped2.cols() > 0)
				//TODO do I need foundCircle anymore


/*
	TODO stuff
	@DATA

*/

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

		if(hashDefineTrue) {
			Core.rectangle(mCameraFeed, new Point(100, 100), new Point(100, 100 + minSizeofDetectableObject), new Scalar(255, 255, 255), 2, 8, 0);
			Core.rectangle(mCameraFeed, new Point(100, 100), new Point(100, 100 + maxSizeofDetectableObject), new Scalar(255, 255, 255), 2, 8, 0);
		}


		// pop the threshold image back in the unused right side of the screen image
		//TODO this is crashing the app, why
		//mThreshold.copyTo(mCameraFeed.submat(new Rect (mCameraFeed.width()/2-1,0,mCameraFeed.width()-1,mCameraFeed.height()-1)));


		//Added a menu to switch between Threshold view and Camera view
		if(hashDefineTrue)
			//return null;
			return mThreshold;
			//return cropped;
		else
			return mCameraFeed;
	}


	// Dooh

	private void pakWritetoInternal(String myData) {
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(myInternalFile, true);
			fos.write(myData.concat("\n\r").getBytes());
			fos.close();
		} catch(IOException e) {
			e.printStackTrace();
		}

	}


	private void pakStartInternalFile() {
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(myInternalFile);
			fos.write("Start\n\r".getBytes());
			fos.close();
		} catch(IOException e) {
			e.printStackTrace();
		}

	}


	private String pakReadInternal() {
		FileInputStream fis;
		String myData = null;
		try {
			fis = new FileInputStream(myInternalFile);
		} catch(FileNotFoundException e) {
			e.printStackTrace();
			return "No Data";
		}
		DataInputStream in = new DataInputStream(fis);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		String strLine;
		try {
			while((strLine = br.readLine()) != null) {
				myData = myData + strLine;
			}
		} catch(IOException e) {
			e.printStackTrace();
			return "No Data";
		}
		try {
			in.close();
		} catch(IOException e) {
			e.printStackTrace();
			return "No Data";

		}

		return myData;
	}

}

