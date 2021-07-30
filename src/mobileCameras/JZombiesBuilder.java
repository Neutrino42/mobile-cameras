package mobileCameras;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import repast.simphony.context.Context;
import repast.simphony.context.DefaultContext;
import repast.simphony.context.space.continuous.ContinuousSpaceFactory;
import repast.simphony.context.space.continuous.ContinuousSpaceFactoryFinder;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.context.space.grid.GridFactory;
import repast.simphony.context.space.grid.GridFactoryFinder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.engine.watcher.Watch;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.continuous.RandomCartesianAdder;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.space.grid.BouncyBorders;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridBuilderParameters;
import repast.simphony.space.grid.SimpleGridAdder;
import repast.simphony.space.grid.WrapAroundBorders;
import repast.simphony.util.ContextUtils;

/**
 * For Mac with M1 chip
 * Note that after any change, we need to first switch to java 11 and run, 
 * After it crashes, then switch back to java 8.
 * This should allow us to run successfully
 * 
 * schedule:
 * camera step()
 * camera clearMsg()
 * @author Nann
 * 
 * 
 */

public class JZombiesBuilder implements ContextBuilder<Object> {
	// size of the world, origin (0,0)
	private double maxX = 50;
	private double maxY = 50;
	private int cameraRange = 10;
	private double humanSpeed = 1;
	private int startTime = 1;
	private int userSeed = 999;

	
	@Override
	public Context build(Context<Object> context) {
		context = new MyContext();
		context.setId("mobileCameras");
		
		NetworkBuilder<Object> netBuilder = new NetworkBuilder<Object>("communication network", context, false);
		Network<Object> net = netBuilder.buildNetwork();
		
		NetworkBuilder<Object> netBuilderCoverage = new NetworkBuilder<Object>("coverage network", context, true);
		Network<Object> covNet = netBuilderCoverage.buildNetwork();
		
		
		ContinuousSpaceFactory spaceFactory = 
				ContinuousSpaceFactoryFinder.createContinuousSpaceFactory(null);
		ContinuousSpace<Object> space = 
				spaceFactory.createContinuousSpace("space", context, 
						new RandomCartesianAdder<Object>(),
						new repast.simphony.space.continuous.BouncyBorders(),
						maxX, maxY);
		
		GridFactory gridFactory = GridFactoryFinder.createGridFactory(null);
		Grid<Object> grid = gridFactory.createGrid("grid", context,
				new GridBuilderParameters<Object>(new BouncyBorders(),
						new SimpleGridAdder<Object>(),
						true, (int)maxX, (int)maxY));
		
		// parse parameters.xml file
		Parameters params = RunEnvironment.getInstance().getParameters();
		int zombieCount = params.getInteger("camera_count");
		int humanCount = params.getInteger("human_count");

		addCameras(context, space, grid, zombieCount);
		addHumans(context, space, grid, humanCount, false);
		
		// add edge with weight 0 to every pair of cameras
		Stream<Object> s = context.getObjectsAsStream(Camera.class);
		List<Object> camList = s.collect(Collectors.toList());
		while (camList.size() > 1) {
			Object tmp = camList.remove(0);
			for (Object cam : camList) {
				net.addEdge(tmp, cam, 0);
			}
		}
	
		for (Object obj : context) {
			NdPoint pt = space.getLocation(obj);
			grid.moveTo(obj, (int)pt.getX(), (int)pt.getY());
		}
		
		// Scheduling
		MyUtil.scheduling(context, startTime);

		
		boolean redirectOutput = true;
		if (redirectOutput) {
			// Redirecting printing to file
			String baseDir = "./trace/";
			String fileName = "sample.txt";
			File directory = new File(baseDir);
			if (! directory.exists()) {
				directory.mkdir();
			}
			File file = new File(baseDir + fileName);
	        PrintStream stream;
			try {
				stream = new PrintStream(file);
				System.out.println("All \"System.out\" is directed to "+file.getAbsolutePath());
		        System.setOut(stream);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	    
		return context;
	}


	private void addCameras(Context<Object> context, ContinuousSpace<Object> space, Grid<Object> grid,
			int zombieCount) {
		for (int i = 0; i < zombieCount; i++) {
			context.add(new Camera(i, space, grid, cameraRange));
		}
	}
	
	private void addCameras_1(Context<Object> context, ContinuousSpace<Object> space, Grid<Object> grid, int zombieCount) {
		for (int i = 0; i < zombieCount; i++) {
			Camera cc = new Camera(i, space, grid, cameraRange);
			context.add(cc);
			space.moveByVector(cc, 30, 0, 0);
		}
	}


	private void addHumans(Context<Object> context, ContinuousSpace<Object> space, Grid<Object> grid, int humanCount, boolean isRandomised) {
		for (int i = 0; i < humanCount; i++) {
			int angle = RandomHelper.nextIntFromTo(0, 4) * 90;
			context.add(new Human(i, space, grid, angle, humanSpeed, userSeed, isRandomised));
		}
	}
	
	private void addHumans_1(Context<Object> context, ContinuousSpace<Object> space, Grid<Object> grid, int humanCount, boolean isRandomised) {
		for (int i = 0; i < humanCount; i++) {
			int angle = 90;
			Human h = new Human(i, space, grid, angle, humanSpeed, userSeed, isRandomised);
			context.add(h);
			space.moveTo(h, new double[] {maxX-5 -5 * (i%2), i * maxY/humanCount +0.01});
		}
	}
	
	private void addHumans_1_2(Context<Object> context, ContinuousSpace<Object> space, Grid<Object> grid, int humanCount, boolean isRandomised) {
		for (int i = 0; i < humanCount; i=i+2) {
			int angle = 90;
			Human h = new Human(i, space, grid, angle, humanSpeed, userSeed, isRandomised);
			context.add(h);
			space.moveTo(h, new double[] {maxX-5 -10*((i/2)%2), i * maxY/humanCount/2 +0.01});
			
		}
		for (int i = 1; i < humanCount; i=i+2) {
			int angle = 90;
			Human h = new Human(i, space, grid, angle, humanSpeed, userSeed, isRandomised);
			context.add(h);
			space.moveTo(h, new double[] {maxX-5 -10*(((i+1)/2)%2), i * maxY/humanCount/2 + maxY/2 -0.01});
			
		}
	}
	
	private void addHumans_2(Context<Object> context, ContinuousSpace<Object> space, Grid<Object> grid, int humanCount, boolean isRandomised) {
		for (int i = 0; i < humanCount; i++) {
			int angle;
			if (i % 2 == 0) {
				angle = 90;
			} else {
				angle = 270;
			}
			
			Human h = new Human(i, space, grid, angle, humanSpeed, userSeed, isRandomised);
			context.add(h);
			
			if (i % 2 ==0) {
				space.moveTo(h, new double[] {maxX/humanCount * (i/2 + 0.5), maxY/humanCount * (i/2+0.5)});
			} else {
				space.moveTo(h, new double[] {maxX/humanCount * (i/2 + 0.5) + maxX/2, maxY/humanCount * (i/2+0.5) + maxY/2});
			}
			
		}
	}
	
	private void addHumans_3(Context<Object> context, ContinuousSpace<Object> space, Grid<Object> grid, int humanCount, boolean isRandomised) {
		for (int i = 0; i < humanCount; i++) {
			int angle;
			if (i%2==0) {
				angle = 90;
			} else {
				angle = 0;
			}
			
			Human h = new Human(i, space, grid, angle, humanSpeed, userSeed, isRandomised);
			context.add(h);
				
			if (i % 2 ==0) {
				space.moveTo(h, new double[] {maxX/2 + maxX/humanCount * (i/2 + 0.5), maxY/humanCount * (i/2+0.5) + maxY/4});
			} else {
				space.moveTo(h, new double[] {maxX/humanCount * (i/2 + 0.5), maxY/humanCount * (i/2+0.5) + maxY/2});
			}
			
		}
	}
		
	private void addHumans_4(Context<Object> context, ContinuousSpace<Object> space, Grid<Object> grid, int humanCount, boolean isRandomised) {
		for (int i = 0; i < humanCount; i++) {
			int angle;
			if (i < humanCount/2) {
				angle = 0;
			} else {
				angle = 90;
			}
			
			Human h = new Human(i, space, grid, angle, humanSpeed, userSeed, isRandomised);
			context.add(h);
				
			
			if (i < humanCount/2) {
				if (i%2 == 0) {
					double[] pos = {5, 45 - i/2 * 6};
					space.moveTo(h, pos);
					System.out.println(i);
					System.out.println(pos[0]);
					System.out.println(pos[1]);
				} else {
					double[] pos = {11, 49.9 - i/2 * 6};
					space.moveTo(h, pos);
				}
			} else {
				if (i%2 == 0) {
					double[] pos = {44 - (i-humanCount/2)/2 * 6, 15};
					space.moveTo(h, pos);
				} else {
					double[] pos = {49.9 - (i-humanCount/2)/2 * 6, 9};
					space.moveTo(h, pos);
				}
			}
			
			/*
			if (i % 2 ==0) {
				space.moveTo(h, new double[] {maxX/2 + maxX/humanCount * (i/2 + 0.5), maxY/humanCount * (i/2+0.5) + maxY/4});
			} else {
				space.moveTo(h, new double[] {maxX/humanCount * (i/2 + 0.5), maxY/humanCount * (i/2+0.5) + maxY/2});
			}
			*/
			
		}
	}
	


	

}
