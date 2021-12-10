package mobileCameras;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
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
 * Then select this java class
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
		
		//System.setOut(System.out);
		//System.out.println("TraceBasedBuilder");
		
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
		
		// Parse parameters.xml
		Parameters params = RunEnvironment.getInstance().getParameters();
		String scenarioPath = params.getString("init_scenario_path");
		int humanCount = params.getInteger("human_count");
		int cameraCount = params.getInteger("camera_count");
		int humanSeed = params.getInteger("user_seed");
		double humanPositionUncertainty;
		try {
			humanPositionUncertainty = params.getDouble("human_position_uncertainty");
		} catch (repast.simphony.parameter.IllegalParameterException e) {
			// if there is no such a parameter in `parameters.xml`
			humanPositionUncertainty = 0;
		}
		Boolean updateKnowledge;
		try {
			updateKnowledge = params.getBoolean("update_knowledge");
		} catch (repast.simphony.parameter.IllegalParameterException e) {
			// if there is no such a parameter in `parameters.xml`
			updateKnowledge = true;
		}
		//System.out.println(updateKnowledge);
		String outFile = params.getString("output_trace_path");
		
		
		// Parse init_scenario xml file
		Document scenario = MyUtil.parseScenarioXML(scenarioPath);
		int startTime = 1 + Integer.parseInt(((Element) scenario.getElementsByTagName("scenario").item(0)).getAttribute("time"));
		int scenarioTime = Integer.parseInt(((Element) scenario.getElementsByTagName("scenario").item(0)).getAttribute("time"));
		HashMap<Integer, Element> covHumMap = MyUtil.parseCoveredHumans(scenario);
		HashMap<Integer, Element> uncovHumMap = MyUtil.parseUncoveredHumans(scenario);
		

		// https://stackoverflow.com/questions/6469969/pseudo-random-number-generator-from-two-inputs
		// Use user provided seed when re-initializing humans
		int seedOriginal = RandomHelper.getSeed();  // The seed is automatically loaded from parameters.xml
		//System.out.println(seedOriginal);	
		int userInitSeed = (humanSeed * 7919 + scenarioTime* 7057 + 17) ^ 13579;
		
		// Initialize the environment: human moving direction, but NOT POSITION
		RandomHelper.setSeed(userInitSeed);
		for (int id = 0; id < humanCount; id++) {
			// check whether the human is covered or uncovered
			Element humanInfo = null;
			if (covHumMap.containsKey(id)) {
				humanInfo = covHumMap.get(id);
			} else if (uncovHumMap.containsKey(id)){
				humanInfo = uncovHumMap.get(id);
			}
			int angle = Integer.parseInt(humanInfo.getAttribute("angle"));
			// !! Note that here we do not introduce randomness in human's behavior
			Human human = new Human(id, space, grid, angle, humanSpeed, humanSeed, false);
			context.add(human);

			// if the human is important, set the importance duration without uncertainty
			if (humanInfo.getAttribute("is_important").equals("true")) {
				int uncertainty = 0;
				human.setDuration(startTime, RandomHelper.nextIntFromTo(0, uncertainty)); 
			}
		}
		// set back to default seed for cameras
		RandomHelper.setSeed(seedOriginal);

		
		// Initialize the cameras: position, messages, covered humans
		for (int i = 0; i < cameraCount; i++) {
			context.add(new Camera(i, space, grid, cameraRange));
		}
		
		NodeList cameraListFromXML = scenario.getElementsByTagName("camera");
		assert (cameraCount == cameraListFromXML.getLength());
		double[] location = new double[2];
		for (int i = 0; i < cameraListFromXML.getLength(); i++) {
			Element cameraFromXML = (Element) cameraListFromXML.item(i);
			int id = Integer.parseInt(cameraFromXML.getAttribute("id"));
			location[0] = Double.parseDouble(cameraFromXML.getAttribute("x"));
			location[1] = Double.parseDouble(cameraFromXML.getAttribute("y"));
			location = MyUtil.updateByBoundaryCheck(location[0], location[1], maxX, maxY);
			
			Stream<Object> s1 = context.getObjectsAsStream(Camera.class);
			List<Object> cam = s1.filter(c -> ((Camera) c).getID() == id).collect(Collectors.toList());
			assert (cam.size() == 1);
			
			// initialize camera position
			space.moveTo(cam.get(0), location);
			
			// initialize covered humans for the camera
			List<Object> newCoveredHumans = new ArrayList<>();
			NodeList humanList = cameraFromXML.getElementsByTagName("object");
			for (int j = 0; j < humanList.getLength(); j++) {
				Element hum = (Element) humanList.item(j);
				int idHum = Integer.parseInt(hum.getAttribute("id"));
				
				Stream<Object> ss = context.getObjectsAsStream(Human.class);
				List<Object> humCov = ss.filter(h -> ((Human) h).getID() == idHum).collect(Collectors.toList());
				newCoveredHumans.add(humCov.get(0));
			}
			((Camera) cam.get(0)).setCoveredHumans(newCoveredHumans);
			
			
			// initialize camera messages
			NodeList msgListFromXML = cameraFromXML.getElementsByTagName("message");
			for (int j = 0; j < msgListFromXML.getLength(); j++) {
				//System.out.println("Loading msg for camera" + i);
				Element msgFromXML = (Element) msgListFromXML.item(j);
				int msgTime = Integer.parseInt(msgFromXML.getAttribute("time"));
				int msgSender = Integer.parseInt(msgFromXML.getAttribute("camera_id"));
				int msgHum = Integer.parseInt(msgFromXML.getAttribute("object_id"));
				
				s1 = context.getObjectsAsStream(Camera.class);
				List<Object> sender = s1.filter(c -> ((Camera) c).getID() == msgSender).collect(Collectors.toList());
				assert sender.size() == 1;
				
				Stream<Object> sh = context.getObjectsAsStream(Human.class);
				List<Object> covHuman = sh.filter(h -> ((Human) h).getID() == msgHum).collect(Collectors.toList());
				assert covHuman.size() == 1;
				((Camera) cam.get(0)).receiveMsg(new Message(msgTime, (Camera) (sender.get(0)), (Human) (covHuman.get(0))));
			}
		}
		

		// Add edge weight to every pair of cameras
		Stream<Object> s2 = context.getObjectsAsStream(Camera.class);
		List<Object> camList = s2.collect(Collectors.toList());

		if(updateKnowledge) {
			// add edge weight according to XML file
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
		} else{
			// add edge with weight 0 to every pair of cameras
			while (camList.size() > 1) {
				Object tmp = camList.remove(0);
				for (Object cam : camList) {
					net.addEdge(tmp, cam, 0);
				}
			}
		}
		
		
		// Initialize the environment: human POSITION
		RandomHelper.setSeed(userInitSeed);
		for (int id = 0; id < humanCount; id++) {
			// check whether the human is covered or uncovered
			Element humanInfo = null;
			if (covHumMap.containsKey(id)) {
				humanInfo = covHumMap.get(id);
			} else if (uncovHumMap.containsKey(id)){
				humanInfo = uncovHumMap.get(id);
			}
			location[0] = Double.parseDouble(humanInfo.getAttribute("x"));
			location[1] = Double.parseDouble(humanInfo.getAttribute("y"));
			location = MyUtil.updateByBoundaryCheck(location[0], location[1], maxX, maxY);
			
			final int id_query = id;
			Stream<Object> s3 = context.getObjectsAsStream(Human.class);
			Human human = (Human) s3.filter(h -> ((Human) h).getID() == id_query).collect(Collectors.toList()).get(0);
			
			// Particularly, if the human is uncovered, estimate its location and importance
			if (humanPositionUncertainty > 0 && uncovHumMap.containsKey(id)){
				while(true) {
					double deltaX = RandomHelper.nextDoubleFromTo(-humanPositionUncertainty, humanPositionUncertainty);
					double deltaY = RandomHelper.nextDoubleFromTo(-humanPositionUncertainty, humanPositionUncertainty);
					location = estimateLocation(location, deltaX, deltaY);
					
					final NdPoint location_candidate = new NdPoint(location);
					Stream<Object> s4 = context.getObjectsAsStream(Camera.class);
					List<Object> coveringCams = s4.filter(camera -> space.getDistance(location_candidate, space.getLocation(camera)) <= ((Camera) camera).getRange())
							.collect(Collectors.toList());
					// Only if this estimated position is not covered by any camera, then return
					if(coveringCams.size() == 0) {
						break;
					}
				}
			}
			// Initialize the human's location
			space.moveTo(human, location);
		}
		// set back to default seed for cameras
		RandomHelper.setSeed(seedOriginal);


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

			File file = new File(outFile);
			if (!file.exists()) {
				file.getParentFile().mkdirs();
			}
			
			//PrintStream stream;
			try {
				//stream = new PrintStream(file);
				//System.out.println("All \"System.out\" is directed to " + file.getAbsolutePath());
				//System.out.flush();
				System.setOut(new PrintStream(new FileOutputStream(outFile, true)));
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
