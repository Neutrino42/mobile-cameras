package mobileCameras;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Test {
	public static void main(String[] args) {
		Document scenario = MyUtil.parseScenarioXML("./trace/scenario.xml");
		System.out.println(scenario.getFirstChild().getNodeName());


		// get all covered objects (with duplicates)
		System.out.println("covered objects:");
		
		HashMap<Integer, Element> covObjMap = getCoveredObjects(scenario);
		covObjMap.forEach((k, v) -> System.out.println(k + " : " + v.getAttribute("id") + " " + v.getAttribute("angle")));
		

		System.out.println("uncovered objects:");

		// get all uncovered objects
		NodeList uncovObjList = getUncoveredObjects(scenario);
		for (int i = 0; i < uncovObjList.getLength(); i++) {
			System.out.println(((Element) uncovObjList.item(i)).getAttribute("id"));
		}
		
		System.out.println("cameras:");
		
		NodeList cameraListFromXML = scenario.getElementsByTagName("camera");
		for (int i = 0; i < cameraListFromXML.getLength(); i++) {
			Element cameraFromXML = (Element) cameraListFromXML.item(i);
			int id = Integer.parseInt(cameraFromXML.getAttribute("id"));
			double x = Double.parseDouble(cameraFromXML.getAttribute("x"));
			double y = Double.parseDouble(cameraFromXML.getAttribute("y"));
			System.out.println("id " + id + " x " + x + " y " + y);
			
			NodeList msgListFromXML = cameraFromXML.getElementsByTagName("message");
			for (int j = 0; j < msgListFromXML.getLength(); j++) {
				Element msgFromXML = (Element) msgListFromXML.item(j);
				int msgTime = Integer.parseInt(msgFromXML.getAttribute("time"));
				int msgSender = Integer.parseInt(msgFromXML.getAttribute("camera_id"));
				int msgObj = Integer.parseInt(msgFromXML.getAttribute("object_id"));
				double msgX = Double.parseDouble(msgFromXML.getAttribute("x"));
				double msgY = Double.parseDouble(msgFromXML.getAttribute("y"));
				System.out.println(msgTime + " " + msgSender + " " + msgObj + " " + msgX + " " + msgY);
			}
		}


	}
	
	private static HashMap<Integer, Element> getCoveredObjects(Document scenario) {
		HashMap<Integer, Element> objMap = new HashMap<>();

		NodeList cameraList = scenario.getElementsByTagName("camera");
		for (int i = 0; i < cameraList.getLength(); i++) {
			NodeList objectList = ((Element) cameraList.item(i)).getElementsByTagName("object");
			for (int j = 0; j < objectList.getLength(); j++) {
				Element obj = (Element) objectList.item(j);
				int id = Integer.parseInt(obj.getAttribute("id"));
				objMap.putIfAbsent(id, obj);
			}
		}
		return objMap;
	}

	private static NodeList getUncoveredObjects(Document scenario) {
		Element listNode = (Element) scenario.getElementsByTagName("uncovered_objects").item(0);
		return listNode.getElementsByTagName("object");
		
	}

}
