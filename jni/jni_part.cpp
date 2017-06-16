#include <jni.h>
#include <D:\TegraPack2\OpenCV-2.4.5-Tegra-sdk-r2\sdk\native\jni\include\opencv2/core/core.hpp>
#include <D:\TegraPack2\OpenCV-2.4.5-Tegra-sdk-r2\sdk\native\jni\include\opencv2\imgproc/imgproc.hpp>
#include <D:\TegraPack2\OpenCV-2.4.5-Tegra-sdk-r2\sdk\native\jni\include\opencv2\features2d/features2d.hpp>
#include <vector>
#include <android/log.h>

#define APPNAME "MyApp"
#define INFO "info"

//Scanning window size
#define IMG_SIZE_X 24//26//24//26
#define IMG_SIZE_Y 24//26//24//26
#define LAST_PYR_ROW_SIZE 50
#define THICKNESS 2

//used namespaces
using namespace std;
using namespace cv;

//native part have to be stated as "C" due to compiler issues
extern "C" {


//declarations
JNIEXPORT void JNICALL Java_cz_borec_vodl_bp_MainActivity_MyDetector(
		JNIEnv *env, jobject, jlong addrGray, jlong addrRgba, jobjectArray stages);

class CStages
{
   public:
	jint * posXs;
	jint * posYs;
	jint * widths;
	jint * heights;

	jfloat * negTs;
	jfloat *  posTs;

	jfloatArray* fArrays;
	float** predictionValuesArray;

	int len;
};

void detect(Mat& mGr, vector<Rect>& highLight, JNIEnv *env, jobjectArray stages);
bool processLbp(Mat& window, JNIEnv *env, jobjectArray stages);
bool processLbp2(Mat& window, CStages stages);
void freeStages(JNIEnv *env, CStages stages);
CStages loadStages(JNIEnv *env, jobjectArray stages);


/**
 * Function to be called from java code
 */
JNIEXPORT void JNICALL Java_cz_borec_vodl_bp_MainActivity_MyDetector(
		JNIEnv *env, jobject, jlong addrGray, jlong addrRgba,jobjectArray stages) {

	//Mat - basic structure in OpenCV to work with images
	Mat& mGr = *(Mat*) addrGray;
	Mat& mRgb = *(Mat*) addrRgba;

	vector<Rect> highLight; // vector with rectangles to highlight detected items

	detect(mGr, highLight, env, stages);

	//highlight detected items on screen (surfaceView)
	for (unsigned int i = 0; i < highLight.size(); i++) {
		rectangle(mRgb, highLight[i], Scalar(255, 0, 0, 255),THICKNESS);
		//rectangle(mGr, highLight[i], Scalar(255, 0, 0, 255),THICKNESS);// if I leave this, ghosts appear...
	}
}


/**
 * Function for scanning matrix for objects
 */
void detect(Mat& mGr, vector<Rect>& highLight, JNIEnv *env, jobjectArray stages) {

	int size;
	int index;
	int posX;
	int posY;
	float downsampled = 1.0;

	Mat tmp, dst; //Mats for downscaling image pyramid
	mGr.copyTo(tmp);
	dst = tmp;

	CStages cstages = loadStages(env,stages);

	//For all images in the pyramid will be performed scanning and for each window LBP computing
	do
	{

		__android_log_print(ANDROID_LOG_DEBUG, "DEBUGR", "DETECT IS COMMING!!!");

		//Rows is Y and Cols is X
		for (int y = 0; y < dst.rows - IMG_SIZE_Y; y = y + THICKNESS)
		{
			for (int x = 0; x < dst.cols - IMG_SIZE_X; x = x + THICKNESS)
			{
				//__android_log_print(ANDROID_LOG_VERBOSE, "DEBUGR", "detecting for: X %d,  Y %d, rows %d, cols %d", x, y, dst.rows, dst.cols);
				Rect r(x, y, IMG_SIZE_X, IMG_SIZE_Y); // Create ROI
				Mat window = dst(r); //Apply ROI on current image from the pyramid



				//if (processLbp(window, env, stages)){
				if (processLbp2(window, cstages)){
					highLight.push_back(Rect(r.x * downsampled, r.y * downsampled, r.width * downsampled, r.height * downsampled ));
				    //store detected positions of ROI into vector
					__android_log_print(ANDROID_LOG_INFO, "DEBUGR", "******* DETECT FOUND FOR  X %d,  Y %d, rows %d, cols %d **************\n", x, y, dst.rows, dst.cols);

				}

			}
		}

		// going down in pyramid images hierarchy
		//pyrDown(tmp, dst, Size(tmp.cols / 2, tmp.rows / 2));
		resize(tmp, dst, Size(tmp.cols / 1.3, tmp.rows / 1.3), INTER_CUBIC);
		downsampled *= 1.3;
		tmp = dst;

	}while (tmp.rows > IMG_SIZE_Y && tmp.cols > IMG_SIZE_X);
	freeStages(env, cstages);
}


/** Gets sum of intensities of pixel, depending on width and height of area */
int getIntensity(int width, int height, int x, int y, Mat& mat) {

	int sum = 0; // tu sem mel uchar....!!!
	uchar * data = mat.data;

	for (int h = y; h < y + height; h++) {
		for (int w = x; w < x + width; w++) {

		   //__android_log_print(ANDROID_LOG_VERBOSE, "DEBUGR","SUM %d = %d + %d z X = %d, Y = %d, (BW = %d, BH =%d, x = %d, y=%d)" , sum + mat.at <uchar> (h, w), sum, mat.at <uchar> (h, w), w, h, width, height, x, y);

			//mat.at has order (y, x)
			//sum += mat.at <uchar> (h, w);
			sum += data[w+h*mat.step];
		}
	}

	return sum;
}




jint getIntFromStage(jclass cls, JNIEnv *env, jobject *stage,
		const char* name) {

	jfieldID id = env->GetFieldID(cls, name, "I");
	return env->GetIntField(*stage, id);
}

jfloat getFloatFromStage(jclass cls, JNIEnv *env, jobject *stage,
		const char* name) {
	jfieldID id = env->GetFieldID(cls, name, "F");
	return env->GetFloatField(*stage, id);
}

/**
 * compute frame's LBP and decide if detected
 */
bool processLbp(Mat& window, JNIEnv *env, jobjectArray stages) {
	int len = env->GetArrayLength(stages);
	jint positionX;
	jint positionY;
	jint width;
	jint height;
	jfloat negT;
	jfloat posT;

	const int array[8][2] = { { 0, 0 }, { 1, 0 }, { 2, 0 }, { 2, 1 }, { 2, 2 },
			{ 1, 2 }, { 0, 2 }, { 0, 1 } };

	float myPredictionValue = 0; //prediction values are summing into this variable while iterating through the stages



	//goes through all stages
	for (int i = 0; i < len; ++i) {
		//__android_log_print(ANDROID_LOG_VERBOSE, APPNAME, "ahoj z foru pro projizdeni stagi, i je %d, len je %d", i, len);

		env->PushLocalFrame(10);// I counted references to be 10

		jobject stage = (jobject) env->GetObjectArrayElement(stages, i);
		jclass cls = env->GetObjectClass(stage); //todo: possible optimalization

		positionX = getIntFromStage(cls, env, &stage, "positionX");
		positionY = getIntFromStage(cls, env, &stage, "positionY");
		width = getIntFromStage(cls, env, &stage, "blockWidth");
		height = getIntFromStage(cls, env, &stage, "blockHeight");

		posT = getFloatFromStage(cls, env, &stage, "posT");
		negT = getFloatFromStage(cls, env, &stage, "negT");

		/* Get array with predictionValues [F is array of floats!*/
		jfieldID fieldId = env->GetFieldID(cls, "predictionValues", "[F");

		// Get the object field, returns JObject (because it’s an Array)
		jobject objArray = env->GetObjectField(stage, fieldId);

		// Cast it to a jfloatarray
		jfloatArray* fArray = reinterpret_cast<jfloatArray*>(&objArray);

		// Get the elements
		float* predictionValues = env->GetFloatArrayElements(*fArray, 0);


		/* cleanup for references (local reference overflow  bug) */
		//env->DeleteLocalRef(stage);//env->DeleteLocalRef(cls);//env->DeleteLocalRef(objArray);
		env->PopLocalFrame(NULL);

		//-------------------------------------------------------------------------------------------------------------------

		int middle = getIntensity(width, height, positionX + width, positionY + height, window); // add width and high so it gets into middle
		unsigned char result = 0;
		unsigned char multiplier = 1;
		int point;




		for (int ii = 0; ii < 8; ii++) {

			point = getIntensity(width, height,
					positionX + (width * array[ii][0]),
					positionY + (height * array[ii][1]), window);

			//if(i == 20)
			//__android_log_print(ANDROID_LOG_VERBOSE, "DEBUGR", " ---- LBP %d:  %d > %d ---> %d << %d ? .... X = %d, Y = %d",ii, point, middle, result, multiplier,positionX + (width * array[ii][0]),positionY + (height *array[ii][1]));

			if (point > middle)
				result = result | multiplier;
			multiplier = multiplier << 1;
			//__android_log_print(ANDROID_LOG_VERBOSE, "DEBUGR", " ++++ LBP %d:  %d > %d ---> %d << %d ? .... X = %d, Y = %d",ii, point, middle, result, multiplier,positionX + (width * array[ii][0]),positionY + (height *array[ii][1]));
		}
		myPredictionValue += predictionValues[result];
		__android_log_print(ANDROID_LOG_VERBOSE, "DEBUGR", "STAGE %d:  RESULT %d ---> %f ..... MPV %f > negT %f?", i, result, predictionValues[result], myPredictionValue, negT);



		env->ReleaseFloatArrayElements(*fArray, predictionValues, 0); //release field (I dont need it now, I use myPredictionValue and I will get new one for next stage)


		if (myPredictionValue < negT)
			return false;
		else if (myPredictionValue > posT)
			return true;
	}
	return true;

}




/**
 * compute frame's LBP and decide if detected
 */
bool processLbp2(Mat& window, CStages stages) {

	jint positionX;
	jint positionY;
	jint width;
	jint height;

	const int array[8][2] = { { 0, 0 }, { 1, 0 }, { 2, 0 }, { 2, 1 }, { 2, 2 },
			{ 1, 2 }, { 0, 2 }, { 0, 1 } };

	float myPredictionValue = 0.0; //prediction values are summing into this variable while iterating through the stages



	//goes through all stages
	for (int i = 0; i < stages.len; ++i) {
		//__android_log_print(ANDROID_LOG_VERBOSE, APPNAME, "ahoj z foru pro projizdeni stagi, i je %d, len je %d", i, len);

		positionX = stages.posXs[i];
		positionY = stages.posYs[i];
		width = stages.widths[i];
		height = stages.heights[i];


		// Get the elements
		float* predictionValues = stages.predictionValuesArray[i];


		//-------------------------------------------------------------------------------------------------------------------


		//int * intensities = getIntensities(width,height,positionX,positionY, window);
		int middle = getIntensity(width, height, positionX + width, positionY + height, window); // add width and high so it gets into middle
		//int middle = intensities[4];

		unsigned char result = 0;
		unsigned char multiplier = 1;
		int point;


		for (int ii = 0; ii < 8; ii++) {


			point = getIntensity(width, height,
					positionX + (width * array[ii][0]),
					positionY + (height * array[ii][1]), window);

			//point= intensities[array[ii][0]+3*array[ii][1]];


			if (point > middle)
				result = result | multiplier;
			multiplier = multiplier << 1;
			//__android_log_print(ANDROID_LOG_VERBOSE, "DEBUGR", " ++++ LBP %d:  %d > %d ---> %d << %d ? .... X = %d, Y = %d",ii, point, middle, result, multiplier,positionX + (width * array[ii][0]),positionY + (height *array[ii][1]));
		}
		myPredictionValue += predictionValues[result];

		//delete [] intensities;

		//__android_log_print(ANDROID_LOG_VERBOSE, "DEBUGR", "STAGE %d:  RESULT %d ---> %f ..... MPV %f > negT %f?", i, result, predictionValues[result], myPredictionValue, stages.negTs[i]);


		if (myPredictionValue < stages.negTs[i])
			return false;
		else if (myPredictionValue > stages.posTs[i])
			return true;
	}
	return true;

}




CStages loadStages(JNIEnv *env, jobjectArray stages){
	__android_log_print(ANDROID_LOG_VERBOSE, "DEBUGR", "going to load stages");

	int len = env->GetArrayLength(stages);
		jint positionX;
		jint positionY;
		jint width;
		jint height;
		jfloat negT;
		jfloat posT;

		jint * positionXarray;
		jint * positionYarray;
		jint * widthArray;
		jint * heightArray;
		jfloat * negTarray;
		jfloat *  posTarray;

		positionXarray = new jint[len];
		positionYarray = new jint[len];
		widthArray = new jint[len];
		heightArray = new jint[len];
		negTarray = new jfloat[len];
		posTarray = new jfloat[len];

		jfloatArray* fArrayArray = new jfloatArray[len];
		float** predictionValuesArray = new float*[len];

		//goes through all stages
		for (int i = 0; i < len; ++i) {

			//env->PushLocalFrame(10);// I counted references to be 10
			//__android_log_print(ANDROID_LOG_VERBOSE, "DEBUGR", "pushnul sem lical frame");

			jobject stage = (jobject) env->GetObjectArrayElement(stages, i);
			jclass cls = env->GetObjectClass(stage); //todo: possible optimalization


			positionXarray[i] = getIntFromStage(cls, env, &stage, "positionX");
			positionYarray[i] = getIntFromStage(cls, env, &stage, "positionY");
			widthArray[i] = getIntFromStage(cls, env, &stage, "blockWidth");
			heightArray[i] = getIntFromStage(cls, env, &stage, "blockHeight");

			posTarray[i] = getFloatFromStage(cls, env, &stage, "posT");
			negTarray[i] = getFloatFromStage(cls, env, &stage, "negT");



			/* Get array with predictionValues [F is array of floats!*/
			jfieldID fieldId = env->GetFieldID(cls, "predictionValues", "[F");

			// Get the object field, returns JObject (because it’s an Array)
			jobject objArray = env->GetObjectField(stage, fieldId);

			// Cast it to a jfloatarray
			jfloatArray* fArray = reinterpret_cast<jfloatArray*>(&objArray);

			// Get the elements
			float* predictionValues = env->GetFloatArrayElements(*fArray, 0);
			float* newPredValues = new float[256];

			//std::copy(predictionValues,predictionValues + 256, newPredVaules);
			for(int it = 0; it< 256; it++){
				newPredValues[it] = predictionValues[it];
			}
			predictionValuesArray[i] = newPredValues;


			/* cleanup for references (local reference overflow  bug) */
			env->DeleteLocalRef(stage);
			env->DeleteLocalRef(cls);
			env->DeleteLocalRef(objArray);
			env->ReleaseFloatArrayElements(*fArray, predictionValues, 0);
		}
		CStages result;
		result.posXs = positionXarray;
		result.posYs = positionYarray;
		result.widths = widthArray;
		result.heights = heightArray;

		result.negTs = negTarray;
		result.posTs = posTarray;

		result.predictionValuesArray = predictionValuesArray;

		result.len = len;
		return result;
}

void freeStages(JNIEnv *env, CStages stages){
	__android_log_print(ANDROID_LOG_VERBOSE, "DEBUGR", "lets free stages ;)");
	int len = stages.len;

	delete [] stages.posXs;
	delete [] stages.posYs;
	delete [] stages.widths;
	delete [] stages.heights;

	delete [] stages.negTs;
	delete [] stages.posTs;
	__android_log_print(ANDROID_LOG_VERBOSE, "DEBUGR", "pred cyklem");
	for (int i = 0; i < len; ++i) {
		delete [] stages.predictionValuesArray[i];
	}

	delete [] stages.predictionValuesArray;


}

}

/** Gets average intensity of pixel, depending on width and height of area */
/*
int * getIntensities(int width, int height, int x, int y, Mat& mat) {

	uchar * data = mat.data;
	int * intensities = new int[9];
	uchar *  pointers[9] = {data                    ,  data + width                    ,  data + 2*width,
			                data + mat.step*height  ,  data + width + mat.step* height ,  data + 2*width + mat.step*height,
	                        data + mat.step*2*height,  data + width + mat.step*2*height,  data + 2*width + mat.step*2*height};


    for (int b = 0; b < height; ++b)
    {
        // go through all pixels in row and accumulate
        int a = 0;
        while (a < width)
        {
            for (int i = 0; i < 9; ++i)
            {
                intensities[i] += *pointers[i];
                ++pointers[i];
            }
            ++a;
        }
        // set pointers to next line
        int i;
        for (i = 0; i < 9; ++i)
            pointers[i] += mat.step;
    }



	return intensities;
}
*/
