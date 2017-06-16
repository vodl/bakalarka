package cz.borec.vodl.bp;

public class Stage
{
		public int positionX;
	public int positionY;
	public float negT;
	public float posT;
	
	public float[] predictionValues;
	
	public int blockWidth;
	public int blockHeight;
	

	public Stage() {		
		super();
	}

	public Stage(int positionX, int positionY, float negT, float posT,
			float[] predictionValues, int blockWidth, int blockHeight) {
		super();
		
		
		
		
		this.positionX = positionX;
		this.positionY = positionY;
		this.negT = negT;
		this.posT = posT;
		this.predictionValues = predictionValues;
		this.blockWidth = blockWidth;
		this.blockHeight = blockHeight;
	}
	
	

}