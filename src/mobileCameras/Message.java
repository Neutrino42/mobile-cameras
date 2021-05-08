package mobileCameras;

public class Message {
	private double time;
	private Camera sender;
	private Human human;
	
	public Message(double time, Camera sender, Human human) {
		this.time = time;
		this.sender = sender;
		this.human = human;
	}
	
	public double getTime() {
		return time;
	}
	
	public Camera getSender() {
		return sender;
	}
	
	public Human getHuman() {
		return human;
	}
	
	public String toString() {
		return String.format("{%.1f|%d|%d}", time, sender.getID(), human.getID());
	}
	

}
