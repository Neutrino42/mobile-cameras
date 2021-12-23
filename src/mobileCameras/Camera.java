/**
 * 
 */
package mobileCameras;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import repast.simphony.context.Context;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.query.space.grid.GridCell;
import repast.simphony.query.space.grid.GridCellNgh;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.SpatialMath;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridPoint;
import repast.simphony.util.ContextUtils;

/**
 * @author Nann
 *
 */


public class Camera {
	
	private ContinuousSpace<Object> space;
	private Grid<Object> grid;
	
	private int id;
	private int range;
	private double maxSpeed;
	private List<Object> coveredHumans;
	private List<Message> receivedMsg;
	private List<Message> msgBuffer;
	private String actionLog;
	private double t;
	
	public static int K = 3; // k for k-coverage

	public Camera(int id, ContinuousSpace<Object> space, Grid<Object> grid, int range) {
		this.id = id;
		this.space = space;
		this.grid = grid;
		this.range = range;
		this.msgBuffer = new ArrayList<>();
		this.receivedMsg = new ArrayList<>();
		this.coveredHumans = new ArrayList<Object>();
	}
	
	//@ScheduledMethod(start = 1, interval = 1, priority = 101)
	public void sense() {
		// get current time
		this.t = RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		
		Context<Object> context = ContextUtils.getContext(this);
		Network<Object> covNet = (Network<Object>) context.getProjection("coverage network");
		
		// get the grid and space location of this Camera
		GridPoint myPt = grid.getLocation(this);
		NdPoint myNdPt = space.getLocation(this);

		// find nearby human within the circular range
		Stream<Object> s = context.getObjectsAsStream(Human.class);
		List<Object> newCoveredHumans = s.filter(human -> space.getDistance(myNdPt, space.getLocation(human)) < range)
				.collect(Collectors.toList());
		
		// update covNet:
		// (1) remove edges with humans that are not being covered any more
		coveredHumans.removeAll(newCoveredHumans);
		for (Object human : coveredHumans) {
			covNet.removeEdge(covNet.getEdge(this, human));
		}
		// (2) add new edges with newly covered humans
		for (Object human : newCoveredHumans) {
			if (!covNet.isAdjacent(this, human)) {
				covNet.addEdge(this, human);
			}
		}

		this.coveredHumans = newCoveredHumans;
		// sort the list to avoid having different orders in different simulation runs 
		Collections.sort(coveredHumans, (o1, o2) -> ((Human) o1).getID() - ((Human)o2).getID());
		
		/*
		 * (1) remove outdated messages (5 time steps max)
		 * (2) load messages from buffer
		 * (3) sort message list
		 * (4) clear buffer
		 */
		receivedMsg.removeIf(msg -> this.t - msg.getTime() >= 5);  // preserve only 4 past time steps
		receivedMsg.addAll(msgBuffer);
		receivedMsg.sort(Comparator
				.comparingDouble(Message::getTime)
				.thenComparing(msg -> msg.getSender().getID())
				);
		msgBuffer.clear();
		
	}
	

	//@ScheduledMethod(start = 1, interval = 1, priority = 100)
	public void thinkAndAct() {
		this.actionLog = "";
		Context<Object> context = ContextUtils.getContext(this);
		Network<Object> covNet = (Network<Object>) context.getProjection("coverage network");
		
		int seed = buildSeed();
		
		Collections.shuffle(coveredHumans, new Random(seed));
		
		for (Object h : coveredHumans) {
			Human human = (Human) h;
			if (human.isImportant()) {
				// notify others only if the human is not k-covered
				if (covNet.getDegree(human) < K) {
					notifyOthers(human, new Random(seed));
				}
				follow(human);
				return;
			}
		}
			
		if (receivedMsg.size() > 0) {
			respond();
			return;
		} 
		
		randomWalk(5);
		
	}
	
	//@ScheduledMethod(start = 1, interval = 5, priority = 1)
//	public void clearMsg() {
//		this.receivedMsg.clear();
//	}
	
	//@ScheduledMethod(start = 1, interval = 1, priority = ScheduleParameters.LAST_PRIORITY + 1 )
	public void printTrace() {
		
		// Collect trace
		String out = String.format("%.1f,cam,", RunEnvironment.getInstance().getCurrentSchedule().getTickCount());
		NdPoint myPoint = space.getLocation(this);
		out += String.format("{%d|%.2f|%.2f},", this.id, myPoint.getX(), myPoint.getY());
		out += String.format("objs,%d,", coveredHumans.size());
		for (Object human: coveredHumans) {
			out += String.format("%s,",human.toString());
		}
		out += String.format("msg,%d", receivedMsg.size());
		for (Message msg : receivedMsg) {
			out += "," + msg.toString();
		}
		out += "," + "act,{" + this.actionLog + "}";
		System.out.println(out);
		
	}
	

	
	private void notifyOthers(Human human, Random random) {
		// here we use k-best to demonstrate
		
		Context<Object> context = ContextUtils.getContext(this);
		Network<Object> commNet = (Network<Object>)context.getProjection("communication network");
		Network<Object> covNet = (Network<Object>)context.getProjection("coverage network");
		
		/* Collect all connected edge into a List to sort it by weight.
		 * First sort by ID, then shuffle, finally sort by weight.
		 * In such a way, for edges with the same weight, their order is randomized
		*/
		List<RepastEdge<Object>> orderedEdgeList = new ArrayList<>();
		commNet.getEdges(this).forEach(orderedEdgeList::add);
		orderedEdgeList.sort(Comparator.comparing(
				e -> ((Camera) e.getSource()).getID() + ((Camera) e.getTarget()).getID() )
		);
		Collections.shuffle(orderedEdgeList, random);
		orderedEdgeList.sort(Comparator
				.comparingDouble(RepastEdge<Object>::getWeight)
				.reversed()
		);
		
		this.actionLog += "n|";
		// loop for k-1 best cameras to pass message
		for (int i = 0; i < K-1; i++) {
			RepastEdge<Object> edge = orderedEdgeList.get(i);
			
			boolean isSource = this.equals(edge.getSource());
			boolean isTarget = this.equals(edge.getTarget());
			Camera nghCamera = null;
			
			if (isSource && !isTarget) {
				nghCamera = (Camera) edge.getTarget();
			} else if (!isSource && isTarget) {
				nghCamera = (Camera) edge.getSource();
			} else {
				throw new java.lang.Error("the edge is illigal for the given agent!");
			}
			
			// not notify if the receiver candidate camera is also covering this human
			if (covNet.isAdjacent(human, nghCamera)) {
				continue;
			}
			nghCamera.receiveMsg(new Message(this.t, this, human));
			
			this.actionLog += nghCamera.getID() + "|";
		}
		
	}

	public void receiveMsg(Message msg) {		
		msgBuffer.add(msg);
	}

	private void randomWalk(double distance) {
		//double angle = (double)randGen.nextInt(360);
		int currTime = (int) this.t;
		double rad = Math.toRadians(((currTime * 7919 + (this.id + 1)* 7057 + 17) ^ 13579) % 7 * (360/6));
		//double rad = Math.toRadians((currTime) % 4 * 90);
		space.moveByVector(this, distance, rad , 0);
		NdPoint myPoint = space.getLocation(this);
		grid.moveTo(this, (int)myPoint.getX(), (int)myPoint.getY());
		this.actionLog += "rand";
		
	}

	private void respond() {
		// accepting request from the strongest link
		
		Context<Object> context = ContextUtils.getContext(this);
		Network<Object> net = (Network<Object>)context.getProjection("communication network");
		
		// sort messages first by edge weight, then by time
		List<Message> sortedMsg = receivedMsg.stream().sorted(
				Comparator
				.comparing((Message msg) -> net.getEdge(msg.getSender(), this).getWeight())  /* ascending weight */
				.thenComparingDouble(Message::getTime)  /* ascending time */
				.reversed()  /* all reversed */
				).collect(Collectors.toList());
		// In net.getEdge(), no need to distinguish source and target, since the network (graph) is undirected
		String mystr = "";
		for (Message msg : sortedMsg) {
			mystr += (String.format("%f %f\n", net.getEdge(msg.getSender(), this).getWeight(), msg.getTime()));
		}
		Camera targetCam = sortedMsg.get(0).getSender(); // not used
		Human targetHum = sortedMsg.get(0).getHuman();
		
		moveTowards(space.getLocation(targetHum), 2);
		this.actionLog += "re|"+ targetCam.getID() + "|" + targetHum.getID();
		//System.out.print(this);
		//System.out.print(" acdepts, and move to ");
		//System.out.println(space.getLocation(targetHum));
		
	}

	private void follow(Human human) {
		NdPoint humanPoint = space.getLocation(human);
		NdPoint myPoint = space.getLocation(this);
		double angle = SpatialMath.calcAngleFor2DMovement(space, myPoint, humanPoint);
		double humanSpeed = human.getSpeed();
		space.moveByVector(this, humanSpeed, angle, 0);
		grid.moveTo(this, (int)myPoint.getX(), (int)myPoint.getY());
		this.actionLog += "f|"+human.getID();
	}
	
	
	public void moveTowards(NdPoint pt, double speed) {
		// only move if we are not already in this Nd location
		if (!pt.equals(space.getLocation(this))) {
			NdPoint myPoint = space.getLocation(this);
			double angle = SpatialMath.calcAngleFor2DMovement(space, myPoint, pt);
			space.moveByVector(this, speed, angle, 0);
			myPoint = space.getLocation(this);
			grid.moveTo(this, (int)myPoint.getX(), (int)myPoint.getY());
			
		}
	}

	
	public int getMsgCount() {
		return receivedMsg.size();
	}
	
	public int getID() {
		return id;
	}
	
	public void setCoveredHumans(List<Object> newCoveredHumans) {
		this.coveredHumans = new ArrayList<>(newCoveredHumans);
	}
	
	public int getRange() {
		return this.range;
	}
	
	private int buildSeed() {
		// Random seeding: each camera will randomize the list the same way every time
		// seed := currentSimulationTimeTick, repastSeedFromUser, ID, 999 
		String seedStr = Double.toString(this.t)
				+ Double.toString(RandomHelper.getSeed())
				+ "seed"
				+ Integer.toString(this.id+1)
				+ "999";
		int seed = new StringBuilder(seedStr).reverse().toString().hashCode() % (2^24);
		return seed;
	}

}
