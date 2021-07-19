/**
 * 
 */
package mobileCameras;

import java.util.Random;

import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.Dimensions;
import repast.simphony.space.SpatialMath;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridPoint;

/**
 * @author Nann
 *
 */
public class Human {
	
	private ContinuousSpace<Object> space;
	private Grid<Object> grid;
	
	private int id;
	private int angleDeg; // in degree
	private int angleDegLog; // the angle used for printing trace log
	private double speed;
	private boolean isImportant = false;
	private int imptDuration = 0;
	private int seed;
	private int imptTotalTime = 30;  // how long it will remain important
	private boolean isRandomised;
	
	// Constructor to be used by TraceBasedBuilder
	public Human(int id, ContinuousSpace<Object> space, Grid<Object> grid, int angle, double speed, int seed, boolean isRandomised) {
		this.id = id;
		this.space = space;
		this.grid = grid;
		this.angleDeg = angle;
		this.angleDegLog = angle;
		
		this.speed = speed;
		this.seed = seed;
		this.isRandomised = isRandomised;
		
		if (isRandomised) {
			this.angleDeg = angle + 3;
			this.speed = speed / Math.cos(Math.toRadians(3));
		}
	}
	
	// Constructor to be used by JZombiesBuilder
	public Human(int id, ContinuousSpace<Object> space, Grid<Object> grid, int angle, double speed, int seed) {
		this(id, space, grid, angle, speed, seed, true);
	}
	
	/*
	 * human run() first, camera step() next
	 */
	//@ScheduledMethod(start = 1, interval = 1, priority = ScheduleParameters.FIRST_PRIORITY)
	public void run() {		
		double newDistance = speed;
		double newAngleRad = Math.toRadians(angleDeg);
		
		space.moveByVector(this, newDistance, newAngleRad, 0);
		NdPoint myPoint = space.getLocation(this);
		grid.moveTo(this, (int)myPoint.getX(), (int)myPoint.getY());
		
		// update angle if there is any bounce
		this.angleDeg = MyUtil.returnBounceAngle(space, space.getLocation(this), this.angleDeg, this.speed);
		this.angleDegLog = MyUtil.returnBounceAngle(space, space.getLocation(this), this.angleDegLog, this.speed);

		updateImportance();
	}

	private int calculateRandSeed() {
		String seedStr = Double.toString(RunEnvironment.getInstance().getCurrentSchedule().getTickCount()) + 
				Double.toString(RandomHelper.getSeed()) + "seed" +
				Integer.toString(this.id+1) + Integer.toString(this.seed);
		int seed = new StringBuilder(seedStr).reverse().toString().hashCode();
		return seed;
	}
	
	private void updateImportance() {
		int currTime = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		if ((currTime / imptTotalTime) % 2 == id % 2) { // integer division rounded towards zero
			isImportant = true;
			imptDuration = -1;
		} else {
			isImportant = false;
			imptDuration = 0;
		}
	}
	
	@Deprecated
	private void updateImportanceOld() {
		
		if (!isImportant) {
			int currTime = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
			if (currTime % imptTotalTime == 1) { 
				if ((currTime / imptTotalTime) % 2 == id % 2) { // integer division rounded towards zero
					isImportant = true;
					imptDuration = imptTotalTime;
				}
			}
		} else {
			imptDuration--;
			if (imptDuration == 0) {
				isImportant = false;
			}
		}
	}
	
	
	@Deprecated
	private void updateImportance(Random randGen) {
		
		if (!isImportant) {
			int rand = randGen.nextInt(100);
			if (rand < 5) {
				isImportant = true;
				imptDuration = 5 + randGen.nextInt(96);
			}
		} else {
			imptDuration--;
			if (imptDuration < 1) {
				imptDuration = 0;
				isImportant = false;
			}
		}
	}
	
	public void moveTowards(GridPoint pt) {
		// only move if we are not already in this grid location
		if (!pt.equals(grid.getLocation(this))) {
			NdPoint myPoint = space.getLocation(this);
			NdPoint otherPoint = new NdPoint(pt.getX(), pt.getY());
			double angle = SpatialMath.calcAngleFor2DMovement(space, myPoint, otherPoint);
			space.moveByVector(this, speed, angle, 0);
			myPoint = space.getLocation(this);
			grid.moveTo(this, (int)myPoint.getX(), (int)myPoint.getY());
		}
	}
	
	
	public String toString() {
		NdPoint myPoint = space.getLocation(this);
		return String.format("{%d|%.2f|%.2f|%d|%s|%d}", this.id, myPoint.getX(), myPoint.getY(), this.angleDegLog,
				String.valueOf(this.isImportant), this.imptDuration);
	}
	
	
	public int getID() {
		return id;
	}
	
	public boolean isImportant() {
		return isImportant;
	}
	
	public double getAngle() {
		return angleDeg;
	}
	public double getSpeed() {
		return speed;
	}
	
	public int getDuration() {
		return imptDuration;
	}
	
	public void setDuration(int currTime, int deviation) {
		if ((currTime / imptTotalTime) % 2 == id % 2) { 
			
			if (deviation < 0) {
				deviation = -deviation;
			}
			if (deviation > imptTotalTime) {
				deviation = deviation % imptTotalTime;
			}
			
			isImportant = true;
			imptDuration = imptTotalTime - currTime % imptTotalTime + 1;
			imptDuration -= deviation;
		}
		
	}

}
