package com.dynatrace.diagnostics.plugin.endecamonitor;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Date;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;

import com.dynatrace.diagnostics.pdk.Migrator;
import com.dynatrace.diagnostics.pdk.Monitor;
import com.dynatrace.diagnostics.pdk.MonitorEnvironment;
import com.dynatrace.diagnostics.pdk.MonitorMeasure;
import com.dynatrace.diagnostics.pdk.Property;
import com.dynatrace.diagnostics.pdk.PropertyContainer;
import com.dynatrace.diagnostics.pdk.Status;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.w3c.dom.NodeList;

/**
 * Monitor which requests the Endeca Status page (XML) via HTTP GET and parses out key measures around the health and performance of Endeca. 
 
 */
public class EndecaMonitor implements Monitor, Migrator {


	// configuration constants
	private static final String CONFIG_PROTOCOL = "protocol";
	private static final String CONFIG_PATH = "path";
	private static final String CONFIG_HTTP_PORT = "httpPort";
	private static final String CONFIG_HTTPS_PORT = "httpsPort";
	
	// measure constants
	private static final String METRIC_GROUP = "Endeca Monitor";
	private static final String MSR_HOST_REACHABLE = "HostReachable";
	private static final String MSR_INDEX_LAST_UPDATE_TIME = "TimeSinceLastIndexUpdate";
	private static final String MSR_NUM_OF_REQUESTS = "NumOfRequests";
	private static final String MSR_CPU_USAGE = "CpuUsage";
	private static final String MSR_REQ_TIME_AVG = "RequestTimeAvg";
	private static final String MSR_REQ_TIME_MAX = "RequestTimeMax";
	private static final String MSR_QUERY_PERF_AVG = "QueryPerformanceAvg";
	private static final String MSR_QUERY_PERF_MAX = "QueryPerformanceMax";
	
	private static final Logger log = Logger.getLogger(EndecaMonitor.class.getName());
	
	private static final String PROTOCOL_HTTPS = "https";
	private static final String PROTOCOL_HTTP = "http";	
	
	//xml element tag constants
	private static final String DATA_INFO = "data_information";
	private static final String REQ_INFO = "requests_information";
	private static final String RUSAGE = "rusage";
	private static final String STAT = "stat";
	
	//xml attribute constants
	private static final String USER_CPU_TIME = "user_time_seconds";
	//private static final String USER_CPU_TIME_REGEX = "(.*[0-9] | [.] | [^a-z] | [^A-Z])";
	private static final String NUM_REQS = "num_requests";
	private static final String DATA_DATE = "data_date";
	private static final String STAT_ATTR_NAME = "name";
	private static final String STAT_ATTR_AVG = "avg";
	private static final String STAT_ATTR_MAX = "max";
	
	//xml "name" attribute value constants
	private static final String STAT_QUERY_PERF_NAME = "Query";
	private static final String SERVER_STAT_NAME_REQ_TIME = "HTTP: Total request time";
		
	Document doc;	
	URLConnection connection;		

	private Config config;

	//params used for final measure assignments
	private double numRequests;
	private double reqTimeAvg;
	private double reqTimeMax;	
	private double cpuUsage;
	private String cpuUsageString;
	private String indexUpdateTime;
	private double timeSinceLastUpdate;
	private double queryPerfAvg;
	private double queryPerfMax;
	
	
	
	@Override
	public Status setup(MonitorEnvironment env) throws Exception {
		Status status = new Status(Status.StatusCode.Success);
		
		config = readConfig(env);
				
		

		if (config.url == null || (!config.url.getProtocol().equals("http") && !config.url.getProtocol().equals("https"))) {
		    status.setShortMessage("only protocols http and https are allowed.");
		    status.setMessage("only protocols http and https are allowed." );
		    status.setStatusCode(Status.StatusCode.PartialSuccess);
		    return status;
		}

		return status;
	}

	@Override
	public void teardown(MonitorEnvironment env) throws Exception {
		//disconnects from your host		
	}
	
	@Override
	public Status execute(MonitorEnvironment env) throws Exception {
		Status status = new Status();				
				
		log.fine("fine");
		log.info("info");
		log.warning("warning");

		//in case plug-in returns PartialSuccess the hostReachable measure will always return 0
		Collection<MonitorMeasure> hostReachableMeasures = env.getMonitorMeasures(METRIC_GROUP, MSR_HOST_REACHABLE);
		if (status.getStatusCode().getBaseCode() == Status.StatusCode.PartialSuccess.getBaseCode() && hostReachableMeasures != null) {
			for (MonitorMeasure measure : hostReachableMeasures)
				measure.setValue(0);
								
		}	
		//if plug-in returns Success, the hostReachable measure will be 1, and considered up or available
		if (status.getStatusCode().getBaseCode() == Status.StatusCode.Success.getBaseCode() && hostReachableMeasures != null) {
			for (MonitorMeasure measure : hostReachableMeasures)
				measure.setValue(1);
										
		}
			
				

		StringBuilder messageBuffer = new StringBuilder("URL: ");
		messageBuffer.append(config.url).append("\r\n");
		
		try {
			if (log.isLoggable(Level.INFO))
				log.info("Executing method: " + /**config.method +**/ ", URI: " + config.url.toString()); //httpRequest.getURI());
			
			//URL to grab Endeca XML file
			connection = config.url.openConnection();
						
			log.info("Parrsing XML from: " + config.url.toString());
			//parse XML response
			parseXmlFile(connection.getInputStream());
						
			log.info("Retrieving XML Results...");
			//retrieve measure for last time Endeca index was updated			
			this.indexUpdateTime = retrieveXmlElementTags(DATA_INFO, DATA_DATE, "", "");			
			//retrieve measure for number of Endeca requests during monitoring interval
			this.numRequests = Double.parseDouble(retrieveXmlElementTags(REQ_INFO, NUM_REQS, "", ""));
			//retrieve measure for CPU usage for Endeca
			this.cpuUsageString = retrieveXmlElementTags(RUSAGE, USER_CPU_TIME, "", "");
			//retrieve measure for Request Time Avg
			this.reqTimeAvg = Double.parseDouble(retrieveXmlElementTags(STAT, STAT_ATTR_NAME, STAT_ATTR_AVG, SERVER_STAT_NAME_REQ_TIME));
			//retrieve measure for Request Time Max
			this.reqTimeMax = Double.parseDouble(retrieveXmlElementTags(STAT, STAT_ATTR_NAME, STAT_ATTR_MAX, SERVER_STAT_NAME_REQ_TIME));
			//retrieve measure for Endeca Avg Query Performance
			this.queryPerfAvg = Double.parseDouble(retrieveXmlElementTags(STAT, STAT_ATTR_NAME, STAT_ATTR_AVG, STAT_QUERY_PERF_NAME));
			//retrieve measure for Endeca Max Query Performance
			this.queryPerfMax = Double.parseDouble(retrieveXmlElementTags(STAT, STAT_ATTR_NAME, STAT_ATTR_MAX, STAT_QUERY_PERF_NAME));
								
		} catch (ConnectException ce) {
			status.setException(ce);
			status.setStatusCode(Status.StatusCode.PartialSuccess);
			status.setShortMessage(ce == null ? "" : ce.getClass().getSimpleName());
			messageBuffer.append(ce == null ? "" : ce.getMessage());
		} catch (IOException ioe) {
			status.setException(ioe);
			status.setStatusCode(Status.StatusCode.PartialSuccess);
			status.setShortMessage(ioe == null ? "" : ioe.getClass().getSimpleName());
			messageBuffer.append(ioe == null ? "" : ioe.getMessage());
			if (log.isLoggable(Level.SEVERE))
				log.severe("Requesting URL " + config.url.toString() /**httpRequest.getURI()**/ + " caused exception: " + ioe);
		} 	
		// calculate and set the measurements
		Collection<MonitorMeasure> measures;						
		if (status.getStatusCode().getCode() == Status.StatusCode.Success.getCode()) {
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_NUM_OF_REQUESTS)) != null) {				 
				for (MonitorMeasure measure : measures) {
					//log.severe("Number of requests=" + numRequests);
					measure.setValue(this.numRequests);
					
				}
			}		
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_INDEX_LAST_UPDATE_TIME)) != null) {				 				
				//convert and compare current date/time against the date of the last index update
				calcLastIndexUpdate();				
				for (MonitorMeasure measure : measures) {										  
					//log.severe("Time Since Last Update= " + timeSinceLastUpdate);
					measure.setValue(this.timeSinceLastUpdate);					
				}
			}
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_CPU_USAGE)) != null) {				 
				//log.severe("CPU user time= " + cpuUsageString);
				this.cpuUsage = Double.parseDouble(parseCpuUsage(cpuUsageString));
				for (MonitorMeasure measure : measures) {
					//log.severe("CPU user time=" + cpuUsageString);
					measure.setValue(this.cpuUsage);
					
				}
			}	
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_REQ_TIME_AVG)) != null) {				 
				for (MonitorMeasure measure : measures) {
					//log.severe("Request time Avg=" + reqTimeAvg);
					measure.setValue(this.reqTimeAvg);
					
				}
			}		
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_REQ_TIME_MAX)) != null) {				 
				for (MonitorMeasure measure : measures) {
					//log.severe("Request Time Maxe=" + reqTimeMax);
					measure.setValue(this.reqTimeMax);
					
				}
			}	
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_QUERY_PERF_AVG)) != null) {				 
				for (MonitorMeasure measure : measures) {
					
					measure.setValue(this.queryPerfAvg);
					
				}
			}		
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_QUERY_PERF_MAX)) != null) {				 
				for (MonitorMeasure measure : measures) {
					
					measure.setValue(this.queryPerfMax);
					
				}
			}		
		}

		status.setMessage(messageBuffer.toString());
		return status;
	}

	private Config readConfig(MonitorEnvironment env) throws MalformedURLException {
		Config config = new Config();

    	String protocol = env.getConfigString(CONFIG_PROTOCOL);
    	int port = 0;
    	if (protocol != null && protocol.contains("https")) {
			port = env.getConfigLong(CONFIG_HTTPS_PORT).intValue();
			protocol = PROTOCOL_HTTPS;
		}
    	if (protocol != null && protocol.contains("http")){
			port = env.getConfigLong(CONFIG_HTTP_PORT).intValue();
			protocol = PROTOCOL_HTTP;
    	}
		String path = fixPath(env.getConfigString(CONFIG_PATH));
		//config.ignorecert = env.getConfigBoolean(CONFIG_IGNORE_CERTIFICATE);
		config.url = new URL(protocol, env.getHost().getAddress(), port, path);
		config.method = "GET";
		
		return config;
	}

	private String fixPath(String path) {
		if (path == null) return "/";
		if (!path.startsWith("/")) return "/" + path;
		return path;
	}

	private void parseXmlFile(InputStream stream)throws Exception {						
		
		//get the factory
		DocumentBuilderFactory dbfactory = DocumentBuilderFactory.newInstance();
		try {			
			//Using factory get an instance of document builder
			DocumentBuilder dBuilder = dbfactory.newDocumentBuilder();						
			//parse using builder to get DOM representation of the XML file
			doc = dBuilder.parse(stream);						 
		}catch(ParserConfigurationException pce) {
			log.severe(pce.getMessage());			
		}catch(IOException ioe) {
			log.severe(ioe.getMessage());			
		}
		 
				
	}

	private String retrieveXmlElementTags(String elementTag, String attribute, String attribute1, String attribute2) throws Exception {
		
		String xmlAttribute = "0";		
		
		//get the root elememt
		Element docEl = doc.getDocumentElement();		
		//get a nodelist of <EndecaMeasures> elements		
		NodeList nodes = docEl.getElementsByTagName(elementTag);
		log.fine ("The element tag is set to: " + elementTag);
		//log.severe ("The element tag is set to: " + elementTag);
		log.fine("The XML attribute is equal to: " + attribute + ":" + attribute2);
		//log.severe("The XML attribute is equal to: " + attribute + ":" + attribute2);
							
		try {		
			if (nodes != null && nodes.getLength() > 0) {
				
				//log.info("Node Count: " + nodes.getLength());				
				//if only one node attribute exists, assign out the values; else, loop through the nodelist for the correct attribute string
				if (nodes.getLength() == 1){ 
					
					//get the EndecaMeasure element
					Element el = (Element)nodes.item(0);
					xmlAttribute = el.getAttribute(attribute);
					log.fine ("The xml attribute measure value is equal to: " + xmlAttribute);
					//log.severe ("The xml attribute measure value is equal to: " + xmlAttribute);
				}
				else if (nodes.getLength() > 1) {										
					for(int i = 0 ; i < nodes.getLength();i++) {	
						//get the EndecaMeasure element
						Element el = (Element)nodes.item(i);						
						
						//capture the String value of each attribute associated with the elementTag being passed in (i.e. name)
						String value = el.getAttribute(attribute); 				         
						//log.severe ("The attribute value is: " + attribute);
						//Capture the numeric value of the specific attribute to be charted with this monitor (i.e. avg, max, etc)
						String value2 = el.getAttribute(attribute1);
						//log.severe ("The attribute1 value is: " + attribute1);
			            
			            //looks for String match on the value of the attribute and specific measure this monitor is configured to capture (i.e. "HTTP Total Request Time")
			            if (value.equals(attribute2) && value != null) {
			            	//makes sure the string values are not null or nan
			            	if (value2 != null && value2 != "nan") {	            	
			            	
			            		xmlAttribute = value2;
			            		//log.severe ("Attribute Values Match: '" + attribute2 + " = '" + value + "'");
			            		log.fine ("The xml attribute measure value is equal to: " + xmlAttribute);
			            		//log.severe ("The xml attribute measure value is equal to: " + xmlAttribute);
			            	}
			            	else {
			            		xmlAttribute = "0";
			            		log.severe ("The value of " + attribute2 + "(" + value2 + ") is null or nan, so a 0.0 value was returned:");
			            	}
			            } 			           
					}										
					
				}
				else {
					
					log.severe ("Unknown error with parsing XML elements...");
				}
								
			} 			
			else {
				
				log.severe("Node(s) are null: " + elementTag + " and " + attribute);
			}
		}catch(Exception ex) {
			log.severe(ex.getMessage());			
		}
		
		return xmlAttribute;		
				
	}		
	
	
	private void calcLastIndexUpdate () throws Exception {
    	
		long seconds = 0;    	
    	    	
    	Date currTime = new Date();
    	String dateStr = this.indexUpdateTime;    	
    	
    	try {
    		DateFormat formatter = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy");
    		Date date = formatter.parse(dateStr);
    		//log.severe("After parse: " + date.getTime());
    		//log.severe ("The Last Index Update was at: " + date);    		
    		//log.severe ("The current time is: " + currTime.getTime());
    		//timeSinceLastUpdate = ((System.currentTimeMillis() - date.getTime())) / 1000;
    		seconds = ((currTime.getTime() - date.getTime()) / 1000);    		
    		//log.severe ("Time Update Diff: " + seconds);
    		this.timeSinceLastUpdate = seconds;
    		log.fine ("The time elapsed since the Index was last updated is: " + timeSinceLastUpdate);
    	
    	} catch(Exception ex) {
    		log.severe (ex.getMessage());    	
    	}
    }	
	
	private String parseCpuUsage (String cpuString) {
		
		String cpuUsage;
		String cpuStr;
		
		cpuStr = cpuString;
	
	    double minutes = 0;
		double seconds = 0;
		
		if (cpuStr.contains("minutes")){
			minutes = Double.parseDouble(cpuStr.substring(0,cpuStr.indexOf(' ')));
			cpuStr = cpuStr.substring(cpuStr.indexOf(',')+2);
			//log.severe ("New cpuStr = " + cpuStr);
		}
		
		if (cpuStr.contains("seconds")) {
			seconds = Double.parseDouble(cpuStr.substring(0,cpuStr.indexOf(' ')));
		}
		
		seconds = seconds + minutes*60;
		cpuUsage = Double.toString(seconds);
		return cpuUsage;
	}

	@Override
	public void migrate(PropertyContainer properties, int major, int minor, int micro, String qualifier) {
		//JLT-41859: change protocol value from http:// and https:// to http and https
		Property prop = properties.getProperty(CONFIG_PROTOCOL);
		if (prop != null) {
			if (prop.getValue() != null && prop.getValue().contains("https")) {
				prop.setValue("https");
			} else {
				prop.setValue("http");
			}
		}
	}
	
	private static class Config {
	    URL url;
		String method;
	}
}