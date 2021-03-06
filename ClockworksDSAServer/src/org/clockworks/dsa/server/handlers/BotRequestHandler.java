package org.clockworks.dsa.server.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.clockworks.dsa.server.environment.Environment;
import org.clockworks.dsa.server.environment.EnvironmentSegment;
import org.clockworks.dsa.server.pings.RTPPing;
import org.clockworks.dsa.server.singletons.CodeQueue;
import org.clockworks.dsa.server.singletons.EnvironmentList;
import org.clockworks.dsa.server.singletons.PingQueue;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler listening for pings from bot (RTP Pings)
 *
 */

public class BotRequestHandler implements HttpHandler {
	
	private HashMap<String,BotRequestHandler.TimeOut> timers;
	private final int timeoutDuration = 10000;
	
	public BotRequestHandler(){
		super();
		timers = new HashMap<String,BotRequestHandler.TimeOut>();
	}
	
	@Override
	public void handle(HttpExchange httpExchange) throws IOException {
		System.out.println("Request received on /botrequesthandler");
		int statusCode = 0;
		String responseBody = "";
		Headers headers = httpExchange.getRequestHeaders();
		List<String> environmentIdList = headers.get("Environment-Id");
		List<String> segmentIdList = headers.get("Segment-Id");
		
		try{
		
			if(environmentIdList==null||segmentIdList==null)
			{
				System.out.println("400: Missing required headers");
				statusCode = 400;
				responseBody = "Missing required headers.";
			}else{
				
				int environmentId = Integer.parseInt(environmentIdList.get(0));
				int segmentId = Integer.parseInt(segmentIdList.get(0));
				
				if(httpExchange.getRequestMethod().equalsIgnoreCase("POST"))
				{
					
					if(environmentId!=0&&segmentId!=0)
					{
						System.out.println("Looking for environment");
						InputStream resultStream = httpExchange.getRequestBody();
						String results = resultStream.toString();
						
						
						// log results to environment
						Environment e = EnvironmentList.sharedInstance().getEnvironmentById(environmentId);
						if(e==null){
							System.out.println("404: Environment Not Found"); 
							statusCode = 404;
							responseBody = "Environment Not Found.";
						}else{
							
							int resultInputStatusCode = e.completeSegmentWithResults(results, segmentId);
							if(resultInputStatusCode==404){
								System.out.println("404: Segment Not Found");
								statusCode = 404;
								responseBody = "Segment Not Found.";
							}else if(resultInputStatusCode==409){
								System.out.println("409: Clash of results");
								statusCode = 409;
								responseBody = "Clash of results.";
							}else{
								deleteTimerForIds(environmentId, segmentId);
							}
						}
					}else if(environmentId!=0||segmentId!=0){
						System.out.println("400: Bad Headers");
						statusCode = 400;
						responseBody = "Bad Headers.";
					}
				
				
					//queue ping
					PingQueue.sharedInstance().addToQueue(new RTPPing(httpExchange));
					if(statusCode==0)
					{
						if(CodeQueue.sharedInstance().isEmpty())
						{
							statusCode = 204;
							responseBody = "Code unavailable at this time.";
							System.out.println("204: Code unavailable at this time");
						}
						else
						{
							// get from code queue
							EnvironmentSegment seg = CodeQueue.sharedInstance().popFromQueue();
							httpExchange.getResponseHeaders().add("Environment-Id", ""+seg.getParentId());
							httpExchange.getResponseHeaders().add("Segment-Id", ""+seg.getId());
							
							// package into unified object
							responseBody = seg.getUnifiedFile();
							statusCode = 200;
							System.out.println("200: Code sent.");
							createTimerForEnvironmentSegment(seg);
						}
					}
				}
				else if(httpExchange.getRequestMethod().equalsIgnoreCase("GET"))
				{
					System.out.println("RTO received: Resetting timer");
					try{
						resetTimerForIds(environmentId, segmentId);
						statusCode = 201;
					}catch(Exception e){
						statusCode = 500;
					}
					
				
				}else{
					statusCode = 405;
					responseBody = "Method not allowed";
				}
			}
		}
		catch(Exception e)
		{
			statusCode = 500;
		}
		
		if(statusCode == 204)
		{
			httpExchange.sendResponseHeaders(statusCode, -1);
		}
		else
		{
			httpExchange.sendResponseHeaders(statusCode, responseBody.length());
			httpExchange.getResponseBody().write(responseBody.getBytes());
		}
		httpExchange.close();
		
	}
	
	private void timerDidTimeOut(EnvironmentSegment s){
		System.out.println("Timer time out: Readded segment to queue.");
		CodeQueue.sharedInstance().addToQueue(s);
	}
	
	class TimeOut extends Timer{
		EnvironmentSegment segment;
		public TimeOut(EnvironmentSegment s){
			segment = s;
		}
	}
	private void createTimerForEnvironmentSegment(final EnvironmentSegment s){
		
		System.out.println("Timer started for segment");
		TimeOut newTimer = new TimeOut(s);
		newTimer.schedule(new TimerTask(){
			
			@Override
			public void run() {
				timerDidTimeOut(s);
				this.cancel();
			}
			
		},timeoutDuration);
		
		timers.put(""+s.getParentId()+s.getId(), newTimer);
		
	}
	private void resetTimerForIds(int environmentId, int segmentId){
		EnvironmentSegment s = deleteTimerForIds(environmentId, segmentId);
		System.out.println("Deleted timer");
		createTimerForEnvironmentSegment(s);
	}

	private EnvironmentSegment deleteTimerForIds(int environmentId, int segmentId){
		TimeOut timer = timers.get(""+environmentId+segmentId);
		if(timer!=null){
			timer.cancel();
			timers.remove(""+environmentId+segmentId);
		}
		return timer.segment;
		
	}

}

