package mobileCameras;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import repast.simphony.context.Context;
import repast.simphony.context.space.continuous.ContinuousSpaceFactory;
import repast.simphony.context.space.continuous.ContinuousSpaceFactoryFinder;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.context.space.grid.GridFactory;
import repast.simphony.context.space.grid.GridFactoryFinder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.Schedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.continuous.RandomCartesianAdder;
import repast.simphony.space.graph.Network;
import repast.simphony.space.grid.BouncyBorders;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridBuilderParameters;
import repast.simphony.space.grid.SimpleGridAdder;

/**
 * To use this builder, first run the repast,
 * Then in the GUI set Data Loaders,
 * In the "Select Data Source Type" window, click "Custom ContextBuilder Implementation"
 * The select this java class
 * 
 * @author Nann
 *
 */

public class TraceBasedBuilder implements ContextBuilder<Object> {
	// size of the world, origin (0,0)
	private double maxX = 50;
	private double maxY = 50;
	private double humanSpeed = 1;
	private int cameraRange = 10;



	@Override
	public Context build(Context<Object> context) {
		
		System.setOut(System.out);
		System.out.println("TraceBasedBuilder");
		
		context = new MyContext();

		context.setId("mobileCameras");

		NetworkBuilder<Object> netBuilder = new NetworkBuilder<Object>("communication network", context, false);
		Network<Object> net = netBuilder.buildNetwork();

		NetworkBuilder<Object> netBuilderCoverage = new NetworkBuilder<Object>("coverage network", context, true);
		Network<Object> covNet = netBuilderCoverage.buildNetwork();

		ContinuousSpaceFactory spaceFactory = ContinuousSpaceFactoryFinder.createContinuousSpaceFactory(null);
		ContinuousSpace<Object> space = spaceFactory.createContinuousSpace("space", context,
				new RandomCartesianAdder<Object>(), new repast.simphony.space.continuous.BouncyBorders(), maxX, maxY);

		GridFactory gridFactory = GridFactoryFinder.createGridFactory(null);
		Grid<Object> grid = gridFactory.createGrid("grid", context,
				new GridBuilderParameters<Object>(new BouncyBorders(), new SimpleGridAdder<Object>(), true, (int)maxX, (int)maxY));
		
		Parameters params = RunEnvironment.getInstance().getParameters();
			
		String initScenario = params.getString("init_scenario_path");
		Document scenario = MyUtil.parseScenarioXML(initScenario);

		int zombieCount = params.getInteger("camera_count");
		for (int i = 0; i < zombieCount; i++) {
			context.add(new Camera(i, space, grid, cameraRange));
		}

		int startTime = 1 + Integer.parseInt(((Element) scenario.getElementsByTagName("scenario").item(0)).getAttribute("time"));


		// Get all covered and uncovered objects from file
		HashMap<Integer, Element> covObjMap = MyUtil.returnCoveredObjects(scenario);
		HashMap<Integer, Element> uncovObjMap = MyUtil.returnUncoveredObjects(scenario);
		
		// https://stackoverflow.com/questions/6469969/pseudo-random-number-generator-from-two-inputs
		// Use user provided seed when re-initializing humans
		int seedOriginal = RandomHelper.getSeed();
		
		int scenarioTime = Integer.parseInt(((Element) scenario.getElementsByTagName("scenario").item(0)).getAttribute("time"));
		int humanSeed = params.getInteger("user_seed");
		int userInitSeed = (humanSeed * 7919 + scenarioTime* 7057 + 17) ^ 13579;
		RandomHelper.setSeed(userInitSeed);

		
		// Initialize human moving direction and position
		int humanCount = params.getInteger("human_count");
		double[] location = new double[2];

		for (int id = 0; id < humanCount; id++) {
			// check whether the human is covered or uncovered
			Element humanInfo = null;
			if (covObjMap.containsKey(id)) {
				humanInfo = covObjMap.get(id);
			} else if (uncovObjMap.containsKey(id)){
				humanInfo = uncovObjMap.get(id);
			}
			
			int angle = Integer.parseInt(humanInfo.getAttribute("angle"));
			location[0] = Double.parseDouble(humanInfo.getAttribute("x"));
			location[1] = Double.parseDouble(humanInfo.getAttribute("y"));
			if (location[0] >= maxX) {
				location[0] = location[0] - 0.01;
			}
			if (location[1] >= maxY) {
				location[1] = location[1] - 0.01;
			}
			
			// !! Note that here we do not introduce randomness in human's behavior
			Human human = new Human(id, space, grid, angle, humanSpeed, humanSeed, false);
			context.add(human);

			// if the human is important, set the importance duration without uncertainty
			if (humanInfo.getAttribute("is_important").equals("true")) {
				int uncertainty = 0;
				human.setDuration(startTime, RandomHelper.nextIntFromTo(0, uncertainty)); 
			}
			
			
			// Particularly, if the human is uncovered, estimate its location and importance
			if (uncovObjMap.containsKey(id)){
				double deltaX = RandomHelper.nextDoubleFromTo(-0.1,0.1);
				double deltaY = RandomHelper.nextDoubleFromTo(-0.1,0.1);
				location = estimateLocation(location, deltaX, deltaY);
			}
			
			// Decide the human's location
			space.moveTo(human, location);
			
		}
		
		// set back to default seed for cameras
		RandomHelper.setSeed(seedOriginal);
		

		// Initialize camera position, messages, covered humans
		NodeList cameraListFromXML = scenario.getElementsByTagName("camera");
		
		assert (zombieCount == cameraListFromXML.getLength());

		for (int i = 0; i < cameraListFromXML.getLength(); i++) {
			Element cameraFromXML = (Element) cameraListFromXML.item(i);
			int id = Integer.parseInt(cameraFromXML.getAttribute("id"));
			double x = Double.parseDouble(cameraFromXML.getAttribute("x"));
			double y = Double.parseDouble(cameraFromXML.getAttribute("y"));
			if (x >= maxX) {
				x = maxX - 0.01;
			}
			if (y >= maxY) {
				y = maxY - 0.01;
			}
			
			Stream<Object> s1 = context.getObjectsAsStream(Camera.class);
			List<Object> cam = s1.filter(c -> ((Camera) c).getID() == id).collect(Collectors.toList());
			assert (cam.size() == 1);
			
			// initialize camera position
			space.moveTo(cam.get(0), new double[] { x, y });
			
			// initialize covered humans for the camera
			List<Object> newCoveredHumans = new ArrayList<>();
			NodeList objectList = cameraFromXML.getElementsByTagName("object");
			for (int j = 0; j < objectList.getLength(); j++) {
				Element obj = (Element) objectList.item(j);
				int idObj = Integer.parseInt(obj.getAttribute("id"));
				
				Stream<Object> ss = context.getObjectsAsStream(Human.class);
				List<Object> objCov = ss.filter(h -> ((Human) h).getID() == idObj).collect(Collectors.toList());
				newCoveredHumans.add(objCov.get(0));
			}
			((Camera) cam.get(0)).setCoveredHumans(newCoveredHumans);
			
			
			// initialize camera messages
			NodeList msgListFromXML = cameraFromXML.getElementsByTagName("message");
			for (int j = 0; j < msgListFromXML.getLength(); j++) {
				//System.out.println("Loading msg for camera" + i);
				Element msgFromXML = (Element) msgListFromXML.item(j);
				int msgTime = Integer.parseInt(msgFromXML.getAttribute("time"));
				int msgSender = Integer.parseInt(msgFromXML.getAttribute("camera_id"));
				int msgObj = Integer.parseInt(msgFromXML.getAttribute("object_id"));
				
				s1 = context.getObjectsAsStream(Camera.class);
				List<Object> sender = s1.filter(c -> ((Camera) c).getID() == msgSender).collect(Collectors.toList());
				assert sender.size() == 1;
				
				Stream<Object> sh = context.getObjectsAsStream(Human.class);
				List<Object> covObject = sh.filter(h -> ((Human) h).getID() == msgObj).collect(Collectors.toList());
				assert covObject.size() == 1;
				Human tmph = (Human) covObject.get(0);
				Camera tmp = (Camera) sender.get(0);
				
				((Camera) cam.get(0)).receiveMsg(new Message(msgTime, (Camera) (sender.get(0)), (Human) (covObject.get(0))));
			}
		}
		

		// Add edge with input weight to every pair of cameras
		Stream<Object> s2 = context.getObjectsAsStream(Camera.class);
		List<Object> camList = s2.collect(Collectors.toList());
		NodeList edgeListFromXML = scenario.getElementsByTagName("edge");

		assert ((camList.size() - 1) * camList.size() / 2 == edgeListFromXML.getLength());

		for (int i = 0; i < edgeListFromXML.getLength(); i++) {
			Element edgeFromXML = (Element) edgeListFromXML.item(i);
			int sourceID = Integer.parseInt(edgeFromXML.getAttribute("source_id"));
			int targetID = Integer.parseInt(edgeFromXML.getAttribute("target_id"));
			double strength = Double.parseDouble(edgeFromXML.getAttribute("strength"));

			s2 = context.getObjectsAsStream(Camera.class);
			Object camSource = s2.filter(c -> ((Camera) c).getID() == sourceID).collect(Collectors.toList()).get(0);
			s2 = context.getObjectsAsStream(Camera.class);
			Object camTarget = s2.filter(c -> ((Camera) c).getID() == targetID).collect(Collectors.toList()).get(0);

			net.addEdge(camSource, camTarget, strength);
		}


		// Initialize grid
		for (Object obj : context) {
			NdPoint pt = space.getLocation(obj);
			grid.moveTo(obj, (int) pt.getX(), (int) pt.getY());
		}
		
		// Scheduling 
		MyUtil.scheduling(context, startTime);


		// Redirecting printing to file
		boolean redirectOutput = true;
		if (redirectOutput) {
			
			File directory = new File("./trace");
			if (!directory.exists()) {
				directory.mkdir();
			}
			String outFile = params.getString("output_trace_path");

			File file = new File(outFile);
			if (!file.exists()) {
				file.getParentFile().mkdirs();
			}
			
			PrintStream stream;
			try {
				stream = new PrintStream(file);
				System.out.println("All \"System.out\" is directed to " + file.getAbsolutePath());
				System.out.flush();
				System.setOut(stream);
			} catch (FileNotFoundException e) {
				System.out.println("FAILED to dreict system output to file " + file.getAbsolutePath());
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return context;
	}

	
	private double[] estimateLocation(double[] location, double deltaX, double deltaY) {
		double newX = location[0] + deltaX;
		double newY = location[1] + deltaY;
		
		// check world boundary
		if (newX < 0) {
			newX = 0;
		} else if (newX > maxX) {
			newX = maxX - 0.1;
		}
		if (newY < 0) {
			newY = 0;
		} else if (newY > maxY) {
			newY = maxY - 0.1;
		}
		
		return new double[] {newX, newY};
	}

}
