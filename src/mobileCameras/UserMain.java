package mobileCameras;

public class UserMain {
	public UserMain() {};

	public void start() {

		String[] args = new String[] { "/Users/Nann/eclipse-workspace/mobileCameras/mobileCameras.rs" };

		repast.simphony.runtime.RepastMain.main(args);
		
	}

	public static void main(String[] args) {

		UserMain um = new UserMain();
		um.start();
	}

}
