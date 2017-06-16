package cz.borec.vodl.bp;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.util.Log;

public class XMLParsing 
{
	
	XmlPullParserFactory pullParserFactory;
	XmlPullParser parser;
	
	public XMLParsing( InputStream in_s){
		try {
			pullParserFactory = XmlPullParserFactory.newInstance();
			parser = pullParserFactory.newPullParser();

			parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
			parser.setInput(in_s, null);

		} catch (XmlPullParserException e) {

			e.printStackTrace();
		}
	}
	
	public Stage[] parseXML() throws XmlPullParserException,IOException
	{
		ArrayList<Stage> stages = null;
        int eventType = parser.getEventType();
        Stage currentStage = null;

        while (eventType != XmlPullParser.END_DOCUMENT){
            String name = null;
            switch (eventType){
                case XmlPullParser.START_DOCUMENT:
                	stages = new ArrayList<Stage>();
                    break;
                    
                case XmlPullParser.START_TAG:
                    name = parser.getName();
                    if (name.equalsIgnoreCase("stage")){
                        currentStage = new Stage();
                        currentStage.negT = Float.parseFloat(parser.getAttributeValue(null, "negT"));
                        currentStage.posT = Float.parseFloat(parser.getAttributeValue(null, "posT"));
                        
                        
                    } else if (currentStage != null){
                    
                        //Histogram hypothesis values
                        if (name.equalsIgnoreCase("HistogramWeakHypothesis")){
                            currentStage.predictionValues = this.strings2floats(parser.getAttributeValue(null, "predictionValues").split(" "));
                            
                        //LBP feature parameters
                        } else if (name.equalsIgnoreCase("LBPFeature")){
                        	currentStage.positionX = Integer.parseInt(parser.getAttributeValue(null, "positionX"));
                        	currentStage.positionY = Integer.parseInt(parser.getAttributeValue(null, "positionY"));
                        	currentStage.blockWidth = Integer.parseInt(parser.getAttributeValue(null, "blockWidth"));
                        	currentStage.blockHeight = Integer.parseInt(parser.getAttributeValue(null, "blockHeight"));
                        }
                    }
                    break;
                case XmlPullParser.END_TAG:
                    name = parser.getName();
                    if (name.equalsIgnoreCase("stage") && currentStage != null){
                    	stages.add(currentStage);
                        Log.v("Parsovacka backgroundozni", "Vyprasoval som stage x je " + currentStage.positionX + "a y je " + currentStage.positionY + "width " + currentStage.blockWidth + " heigh " + currentStage.blockHeight);
                    } 
            }
            eventType = parser.next();
        }

        Log.i("Parsovacka backgroundozni", "XML PARSING FINISHED");
        return stages.toArray(new Stage[stages.size()]);
	}


	
	private double[] strings2doubles(String[] strings){
		
	double[] doubles = new double[strings.length];
	for (int i = 0; i < doubles.length; i++) {
	    doubles[i] = Double.parseDouble(strings[i]);
	}
	return doubles;
	}
	
	private float[] strings2floats(String[] strings){
		
	float[] floats = new float[strings.length];
	for (int i = 0; i < floats.length; i++) {
	    floats[i] = Float.parseFloat(strings[i]);
	}
	return floats;
	}
}
