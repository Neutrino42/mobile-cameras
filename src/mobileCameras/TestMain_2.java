package mobileCameras;

import java.io.File;

import repast.simphony.engine.environment.RunEnvironment;

public class TestMain_2 {

	public static void main(String[] args){
		
		if (args.length == 0) {
			args = new String[] { "/Users/Nann/eclipse-workspace/mobileCameras/mobileCameras.rs" };
		}
		File file = new File(args[0]); // the scenario dir
		
		double endTime = 1000.0;  // default end time
		if (args.length == 2) {
			endTime = Double.parseDouble(args[1]); 
		}

		TestRunner_2 runner = new TestRunner_2();

		try {
			runner.load(file);     // load the repast scenario
		} catch (Exception e) {
			e.printStackTrace();
		}


		// Run the sim a few times to check for cleanup and init issues.
		for(int i=0; i<1; i++){

			runner.runInitialize();  // initialize the run

			//RunEnvironment.getInstance().endAt(endTime);

			while (runner.getActionCount() > 0){  // loop until last action is left
				if (runner.getModelActionCount() == 0) {
					runner.setFinishing(true);
				}
				runner.step();  // execute all scheduled actions at next tick
				
				if (endTime <= RunEnvironment.getInstance().getCurrentSchedule().getTickCount()) {
					break;
				}
			}

			runner.stop();          // execute any actions scheduled at run end
			runner.cleanUpRun();
		}
		runner.cleanUpBatch();    // run after all runs complete
	}
}