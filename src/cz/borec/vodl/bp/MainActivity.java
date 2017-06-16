package cz.borec.vodl.bp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.xmlpull.v1.XmlPullParserException;

import cz.borec.vodl.bp.R;
import cz.borec.vodl.bp.XMLParsing;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.media.FaceDetector;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;



public class MainActivity extends Activity implements CvCameraViewListener2 {
	
	/* ruzne deklarace */
    private static final String    TAG = "OCVSample::Activity";
    public static final String     CLASSIFIER = "faces_Juranek.xml"; //"faces.xml";//"faces_Juranek.xml";
    
    private static final int      MAX_FACES_ANDROID = 30;

    private static final int       VIEW_MODE_RGBA     = 0;
    private static final int       VIEW_MODE_GRAY     = 1;
    private static final int       VIEW_MODE_CANNY    = 2;
    private static final int       VIEW_MODE_MYDETECT = 5;
    private static final int       VIEW_MODE_STILL = 6;
    private static final int       VIEW_MODE_STILL2 = 7;
    private static final int       VIEW_MODE_OCVJD = 8;
    private static final int       VIEW_MODE_ANDDET = 9;

    private int                    mViewMode;
    private Mat                    mRgba;
    private Mat                    mIntermediateMat;
    private Mat                    mGray;

    private MenuItem               mItemPreviewRGBA;
    private MenuItem               mItemPreviewStill;
    private MenuItem               mItemPreviewMyDetect;
    private MenuItem			mItemPreviewOCVJD;
    private MenuItem			mItemPreviewANDDET;

    
    private CameraBridgeViewBase   mOpenCvCameraView;
    private Stage[]                parsedStages = null;
    
    private volatile boolean parsingInProgress = false;
    private volatile boolean onFrameLock = false;
    
    //for OCV detection
    private File                   mCascadeFile;
    private CascadeClassifier      mJavaDetector;

    
    /* library init */
    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("mixed_sample");
                    OCV_detector_init();
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.surface_view);
        
        if (parsedStages == null)
        {
        	if(!parsingInProgress)
    		    new BackgroundParse().execute();
        	parsingInProgress = true;
        }
        
        

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.tutorial2_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.enableFpsMeter();
        mOpenCvCameraView.setMaxFrameSize(176, 144);
    }

    
    /* MENU *****/
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemPreviewRGBA = menu.add("Cam RGBA");
        mItemPreviewStill = menu.add("Still");
        mItemPreviewMyDetect = menu.add("My Detect");
        mItemPreviewOCVJD = menu.add("OCV Detection");
        mItemPreviewANDDET = menu.add("Android Detect");
        return true;
    }

    
    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }
    
    

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
    }

    
    
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    
    
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mIntermediateMat = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
    }

    
    
    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
        mIntermediateMat.release();
    }
    
    

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
    	 Bitmap bmp;
    	
        final int viewMode = mViewMode;
        switch (viewMode) {
        case VIEW_MODE_GRAY:
            // input frame has gray scale format
            Imgproc.cvtColor(inputFrame.gray(), mRgba, Imgproc.COLOR_GRAY2RGBA, 4);
            break;
            
        case VIEW_MODE_RGBA:
            // input frame has RBGA format
            mRgba = inputFrame.rgba();
            break;
            
        case VIEW_MODE_CANNY:
            // input frame has gray scale format
            mRgba = inputFrame.rgba();
            Imgproc.Canny(inputFrame.gray(), mIntermediateMat, 80, 100);
            Imgproc.cvtColor(mIntermediateMat, mRgba, Imgproc.COLOR_GRAY2RGBA, 4);
            break;
            
        case VIEW_MODE_OCVJD:
             mRgba = inputFrame.rgba();
             mGray = inputFrame.gray();
        	 MatOfRect faces = new MatOfRect();
             mJavaDetector.detectMultiScale(mGray, faces, 1.3f, 0, 0, new Size(), new Size() );
             Rect[] facesArray = faces.toArray();
             for (int i = 0; i < facesArray.length; i++)
                 Core.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(),  new Scalar(0, 255, 0, 255), 3); 
        	 break;
        	 
        case VIEW_MODE_ANDDET:
        	mRgba = inputFrame.rgba();
        	bmp = Bitmap.createBitmap(mRgba.width(), mRgba.height(), Config.RGB_565);
        	Utils.matToBitmap(mRgba, bmp);
        	FaceDetector face_detector = new FaceDetector(bmp.getWidth(), bmp.getHeight(), MAX_FACES_ANDROID);
        	FaceDetector.Face[] andFaces = new FaceDetector.Face[MAX_FACES_ANDROID];
        	int face_count = face_detector.findFaces(bmp, andFaces);
            for (int i = 0; i < face_count; i++){
            	FaceDetector.Face andFace = andFaces[i];
            	PointF andPoint = new PointF();
            	andFace.getMidPoint(andPoint);
            	Core.circle(mRgba, new Point((double)andPoint.x, (double)andPoint.y), (int) andFace.eyesDistance(), new Scalar(0, 255, 0, 255));
            }
            bmp.recycle();
        	break;
        	        	
            
        case VIEW_MODE_STILL:
        	BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            if (mOpenCvCameraView.getWidth() == 800)
                bmp = BitmapFactory.decodeResource(getResources(),R.drawable.frisbee3, options);
            else
            	bmp = BitmapFactory.decodeResource(getResources(),R.drawable.frisbee3202, options);
            Utils.bitmapToMat(bmp, mIntermediateMat); 
            bmp.recycle();
            Imgproc.cvtColor(mIntermediateMat, mGray, Imgproc.COLOR_RGBA2GRAY, 1);
            Imgproc.cvtColor(mGray, mRgba, Imgproc.COLOR_GRAY2RGBA, 4);
            myDetect();
        	break;
        	
        case VIEW_MODE_STILL2:        	
        	BitmapFactory.Options options1 = new BitmapFactory.Options();
            options1.inScaled = false;
            Bitmap bmp1 = BitmapFactory.decodeResource(getResources(),R.drawable.frisbee2, options1);
            Utils.bitmapToMat(bmp1, mIntermediateMat);      
            bmp1.recycle();
            Imgproc.cvtColor(mIntermediateMat, mGray, Imgproc.COLOR_RGBA2GRAY, 1);
            Imgproc.cvtColor(mGray, mRgba, Imgproc.COLOR_GRAY2RGBA, 4);
            myDetect();
        	break;
           
        case VIEW_MODE_MYDETECT:
            // input frame has RGBA format
            mRgba = inputFrame.rgba();
            mGray = inputFrame.gray();

            myDetect();

            break;
        }

        return mRgba;
    }
    
    void myDetect()
    {
        if (parsedStages != null)
            MyDetector(mGray.getNativeObjAddr(), mRgba.getNativeObjAddr(), parsedStages);
    }
    
    

    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);

        if (item == mItemPreviewRGBA) {
            mViewMode = VIEW_MODE_RGBA;
        } else if (item == mItemPreviewStill) {
            mViewMode = VIEW_MODE_STILL;
        } else if (item == mItemPreviewMyDetect) {
            mViewMode = VIEW_MODE_MYDETECT;
        }else if (item == mItemPreviewOCVJD) {
            mViewMode = VIEW_MODE_OCVJD;
        }else if (item == mItemPreviewANDDET) {
            mViewMode = VIEW_MODE_ANDDET;
        }

        return true;
    }
    
	private final class BackgroundParse extends AsyncTask<Void, Void, Stage[]>
	{

		@Override
		protected Stage[] doInBackground(Void... params) {
			InputStream in_s;
			try {
				in_s = getApplicationContext().getAssets().open(CLASSIFIER);
				XMLParsing xp = new XMLParsing(in_s);
				return xp.parseXML();
				
			} catch (IOException e) {
				e.printStackTrace();
			} catch (XmlPullParserException e) {
				e.printStackTrace();
			}
			
			return null;
		}
		
		@Override
		protected void onPostExecute(Stage[] stages) {
			super.onPostExecute(stages);
			
			parsedStages = stages;
		}
		
	}
	
	/**
	 * Reused from OpenCV sample - face detection
	 * this code is not under any licence
	 */
	public void OCV_detector_init()
	{    
		try {
			
        // load cascade file from application resources
        InputStream is = getApplicationContext().getAssets().open("lbpcascade_frontalface.xml");
        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
        FileOutputStream os = new FileOutputStream(mCascadeFile);

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        is.close();
        os.close();

        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
        if (mJavaDetector.empty()) {
            Log.e(TAG, "Failed to load cascade classifier");
            mJavaDetector = null;
        } else
            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

        cascadeDir.delete();

    } catch (IOException e) {
        e.printStackTrace();
        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
    }}

    public native void MyDetector(long matAddrGr, long matAddrRgba, Stage[] stages);
}
