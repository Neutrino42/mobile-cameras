package mobileCameras;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import repast.simphony.context.DefaultContext;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;

public class MyContext extends DefaultContext {
	
	
	//@ScheduledMethod(start = 1, interval = 1, priority = 3)
	public void evaporateNetwork() {
		Network<Object> net = (Network<Object>) getProjection("communication network");
		net.getEdges().forEach(edge -> edge.setWeight(edge.getWeight() * 0.9));
	}
	
	
	//@ScheduledMethod(start = 1, interval = 1, priority = 2)
	public void strengthenNetwork() {
		Network<Object> commNet = (Network<Object>) getProjection("communication network");
		Network<Object> covNet = (Network<Object>) getProjection("coverage network");
		
		Stream<Object> s = getObjectsAsStream(Human.class);
		List<Object> multiCoveredHumanList = s.filter(
				human -> covNet.getDegree(human) > 1
				).collect(Collectors.toList());
		
		List<Object> tmpCamList = new ArrayList<>();
		for (Object human : multiCoveredHumanList) {
			covNet.getAdjacent(human).forEach(tmpCamList::add);
			
			// loop every pair of cameras that is covering this human, and increase its edge weight
			while(tmpCamList.size() > 1) {
				Object cam1 = tmpCamList.remove(0);
				for (Object cam2: tmpCamList) {
					RepastEdge edge = commNet.getEdge(cam1, cam2);
					edge.setWeight(edge.getWeight() + 1);
				}
			}
			tmpCamList.clear();
		}
	}
	
	/*
	 * Print trace for communication graph and uncovered Humans
	 */
	//@ScheduledMethod(start = 1, interval = 1, priority = ScheduleParameters.LAST_PRIORITY)
	public void collectTraceForEnv() {
		String out = String.format("%.1f,graph", RunEnvironment.getInstance().getCurrentSchedule().getTickCount());

		Network<Object> net = (Network<Object>) getProjection("communication network");
		for (RepastEdge<Object> edge: net.getEdges()) {
			out += String.format(",{%d|%d|%f}", ((Camera) edge.getSource()).getID(), 
					((Camera) edge.getTarget()).getID(),
					edge.getWeight());
		}
		System.out.println(out);
		
		
		Network<Object> covNet = (Network<Object>) getProjection("coverage network");
		Stream<Object> s = getObjectsAsStream(Human.class);
		List<Object> uncoveredHumanList = s.filter(
				human -> covNet.getDegree(human) == 0
				).collect(Collectors.toList());
		out = String.format("%.1f,objs,%d", RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), uncoveredHumanList.size());
		for (Object human : uncoveredHumanList) {
			out += String.format(",%s",human.toString());
		}
		
		System.out.println(out);
		
	}
	

}
