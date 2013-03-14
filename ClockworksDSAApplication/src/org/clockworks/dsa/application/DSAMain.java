package org.clockworks.dsa.application;

import android.content.Context;

public class DSAMain extends Thread{
	private String simulationResults, environmentID, segmentID;
	private boolean done;
	private ServerContacter requester;
	private PythonProcessor processor;
	
	public DSAMain(String server, Context context){
		this.requester = new ServerContacter(server, context);
		this.processor = new PythonProcessor();
	}
	
	public void run(){
		done = false;
		//discard any old results from last run
		simulationResults = null;
		environmentID = "123";
		segmentID = "145";
		while(!done){
			while(!requester.onAllowedNetwork()){
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			RTPResponse serverResponse = requester.sendRTPPing(simulationResults, environmentID, segmentID);
			
			if(serverResponse.getResponseCode() == 200){
				String simulationPath = serverResponse.getSimulationFilePath();
				// TODO Create thread for processing the python script
				boolean abort = false;
				// TODO while not finished processing and abort is false
					// TODO If timeout reached
						if(requester.onAllowedNetwork()){
							requester.sendRTOPing();
						}
						else{
							abort = true;
						}
			}
		}
	}
	
	public void exit(){
		done = true;
	}

}
