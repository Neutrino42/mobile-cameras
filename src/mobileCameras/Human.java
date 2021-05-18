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
	private double speed;
	private boolean isImportant;
	private int imptDuration;
	private int seed;
	
	public Human(int id, ContinuousSpace<Object> space, Grid<Object> grid, int angle, double speed, int seed) {
		this.id = id;
		this.space = space;
		this.grid = grid;
		this.angleDeg = angle;
		this.speed = speed;
		this.isImportant = false;
		this.imptDuration = 0;
		this.seed = seed;
	}
	
	/*
	 * human run() first, camera step() next
	 */
	//@ScheduledMethod(start = 1, interval = 1, priority = ScheduleParameters.FIRST_PRIORITY)
	public void run() {		
		this.angleDeg = MyUtil.returnBounceAngle(space, space.getLocation(this), this.angleDeg, this.speed);
		
		space.moveByVector(this, speed, Math.toRadians(angleDeg), 0);
		NdPoint myPoint = space.getLocation(this);
		grid.moveTo(this, (int)myPoint.getX(), (int)myPoint.getY());
		
		// seed := currentSimulationTimeTick, repastSeedFromUser, humanID, humanSeedFromUser
		String seedStr = Double.toString(RunEnvironment.getInstance().getCurrentSchedule().getTickCount()) + 
				Double.toString(RandomHelper.getSeed()) + "seed" +
				Integer.toString(this.id+1) + Integer.toString(this.seed);
		int seed = new StringBuilder(seedStr).reverse().toString().hashCode();
		
		updateImportance(new Random(seed));
	}
	

	

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
		return String.format("{%d|%.2f|%.2f|%d|%s|%d}", this.id, myPoint.getX(), myPoint.getY(), this.angleDeg,
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
	
	public void setDuration(int duration) {
		if (duration > 0) {
			this.imptDuration = duration;
			this.isImportant = true;
		}
		
	}

}
