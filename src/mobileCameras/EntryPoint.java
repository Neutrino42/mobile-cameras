package mobileCameras;

import py4j.GatewayServer;

public class EntryPoint {

    private TestRunner_2 runner;

    public EntryPoint() {
    	runner = new TestRunner_2();
    }

    public TestRunner_2 getRunner() {
        return runner;
    }

    public static void main(String[] args) {
    	if(args.length == 1) {
    		int port = Integer.parseInt(args[0]); 
    		GatewayServer gatewayServer = new GatewayServer(new EntryPoint(), port);
    		gatewayServer.start();
    		
    	} else {
    		GatewayServer gatewayServer = new GatewayServer(new EntryPoint());
    		gatewayServer.start();
    		System.out.println("Gateway Server Started");
    	}
        
    }

}