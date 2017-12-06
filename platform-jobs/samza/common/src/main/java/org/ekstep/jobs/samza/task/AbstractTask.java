package org.ekstep.jobs.samza.task;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.samza.config.Config;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.OutgoingMessageEnvelope;
import org.apache.samza.system.SystemStream;
import org.apache.samza.task.InitableTask;
import org.apache.samza.task.MessageCollector;
import org.apache.samza.task.StreamTask;
import org.apache.samza.task.TaskContext;
import org.apache.samza.task.TaskCoordinator;
import org.apache.samza.task.WindowableTask;
import org.ekstep.jobs.samza.service.ISamzaService;
import org.ekstep.jobs.samza.service.task.JobMetrics;
import org.ekstep.jobs.samza.util.SamzaCommonParams;

import com.ilimi.common.Platform;
import com.ilimi.common.logger.LoggerEnum;
import com.ilimi.common.logger.PlatformLogger;

public abstract class AbstractTask implements StreamTask, InitableTask, WindowableTask {

	protected JobMetrics metrics;
	private Config config = null;
	private String eventId = "";
	protected String jobType = "";
	protected String jobStartMessage = "";
	protected String jobEndMessage = "";
    protected String jobClass = "";
    
    private static String mid = "LP."+System.currentTimeMillis()+"."+UUID.randomUUID();
    private static String startJobEventId = "JOB_START";
	private static String endJobEventId = "JOB_END";
	private static int MAXITERTIONCOUNT= 2;
	@Override
	public void init(Config config, TaskContext context) throws Exception {
		metrics = new JobMetrics(context);
		ISamzaService service = initialize();
		service.initialize(config);
		this.config = config;
		this.eventId = "BE_JOB_REQUEST";
	}

	public abstract ISamzaService initialize() throws Exception;

	@SuppressWarnings("unchecked")
	@Override
	public void process(IncomingMessageEnvelope envelope, MessageCollector collector, TaskCoordinator coordinator)
			throws Exception {
		Map<String, Object> message = (Map<String, Object>) envelope.getMessage();
		long jobStartTime = 0;
		preProcess(message, collector, jobStartTime);
		process(message, collector, coordinator);
		postProcess(message, collector, jobStartTime);
	}

	public abstract void process(Map<String, Object> message, MessageCollector collector, TaskCoordinator coordinator)
			throws Exception;

	@SuppressWarnings("unchecked")
	public void preProcess(Map<String, Object> message, MessageCollector collector, long jobStartTime) {
		if (isInvalidMessage(message)) {
			String event = generateEvent(LoggerEnum.ERROR.name(), "Samza job de-serialization error", message);
			collector.send(new OutgoingMessageEnvelope(new SystemStream(SamzaCommonParams.kafka.name(), this.config.get("backend_telemetry_topic")), event));
		}
		
		// check for valid instruction to process. action=publish, max retries less than iteration value.
		// generate job start event
		if(StringUtils.equalsIgnoreCase(this.eventId, (String)message.get(SamzaCommonParams.eid.name()))) {
			String requestedJobType = (String)((Map<String, Object>) message.get(SamzaCommonParams.edata.name())).get(SamzaCommonParams.action.name());
			if(StringUtils.equalsIgnoreCase(this.jobType, requestedJobType)) {
				try {
					int iterationCount = (int)((Map<String, Object>) message.get(SamzaCommonParams.edata.name())).get(SamzaCommonParams.iteration.name());
					if(iterationCount == 0) {
						Map<String, Object> jobStartEvent = getJobEvent("JOBSTARTEVENT", message);
						jobStartTime = (long)jobStartEvent.get(SamzaCommonParams.ets.name());
						pushEvent(jobStartEvent, collector, this.config.get("backend_telemetry_topic"));
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public void postProcess(Map<String, Object> message, MessageCollector collector, long jobStartTime) throws Exception {
		// check status of the processed event.
		// generate job end event with execution stats.
		if(StringUtils.equalsIgnoreCase(this.eventId, (String)message.get(SamzaCommonParams.eid.name()))) {
			String requestedJobType = (String)((Map<String, Object>) message.get(SamzaCommonParams.edata.name())).get(SamzaCommonParams.action.name());
			if(StringUtils.equalsIgnoreCase(this.jobType, requestedJobType)) {
				String eventExecutionStatus = (String)((Map<String, Object>) message.get(SamzaCommonParams.edata.name())).get(SamzaCommonParams.status.name());
				int iterationCount = (int)((Map<String, Object>) message.get(SamzaCommonParams.edata.name())).get(SamzaCommonParams.iteration.name());
				int maxIterationCount = 0;
				if(StringUtils.isNotEmpty(Platform.config.getString("MAX_ITERATION_COUNT_FOR_SAMZA_JOB"))) {
					maxIterationCount = Integer.valueOf(Platform.config.getString("MAX_ITERATION_COUNT_FOR_SAMZA_JOB"));
				}else {
					maxIterationCount = MAXITERTIONCOUNT;
				}
				if(StringUtils.equalsIgnoreCase(eventExecutionStatus, SamzaCommonParams.SUCCESS.name()) || 
						(StringUtils.equalsIgnoreCase(eventExecutionStatus, SamzaCommonParams.FAILED.name()) && 
								(iterationCount == maxIterationCount))) {
					Map<String, Object> jobEndEvent = getJobEvent("JOBENDEVENT", message);
					addExecutionTime(jobEndEvent, jobStartTime); //Call to add execution time
					pushEvent(jobEndEvent, collector, this.config.get("backend_telemetry_topic"));
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void addExecutionTime(Map<String, Object> jobEndEvent, long jobStartTime) {
		Map<String, Object> eks = (Map<String, Object>)((Map<String, Object>)jobEndEvent.get(SamzaCommonParams.edata.name())).get(SamzaCommonParams.eks.name());
		Map<String, Object> execution = new HashMap<>();
		execution.put(SamzaCommonParams.submitted_date.name(), eks.get(SamzaCommonParams.ets.name()));
		execution.put(SamzaCommonParams.processing_date.name(), jobStartTime);
		execution.put(SamzaCommonParams.completed_date.name(), jobEndEvent.get(SamzaCommonParams.ets.name()));
		execution.put(SamzaCommonParams.latency.name(), (long)eks.get(SamzaCommonParams.ets.name())-jobStartTime);
		execution.put(SamzaCommonParams.execution_time.name(), jobStartTime-(long)jobEndEvent.get(SamzaCommonParams.ets.name()));
		eks.put(SamzaCommonParams.execution.name(), execution);
		((Map<String, Object>)jobEndEvent.get(SamzaCommonParams.edata.name())).put(SamzaCommonParams.eks.name(), eks);
		
	}
	
	private void pushEvent(Map<String, Object> message, MessageCollector collector, String topicId) throws Exception {
		collector.send(new OutgoingMessageEnvelope(new SystemStream(SamzaCommonParams.kafka.name(), topicId), message));
	}
	
	@SuppressWarnings("unchecked")
	public Map<String, Object> getJobEvent(String jobEvendID, Map<String, Object> message){
		
		long unixTime = System.currentTimeMillis();
		Map<String, Object> jobEvent = new HashMap<>();
		
		jobEvent.put(SamzaCommonParams.ets.name(), unixTime);
		jobEvent.put(SamzaCommonParams.mid.name(), mid);
	
		Map<String, Object> edata = new HashMap<>();
		Map<String, Object> eks = new HashMap<>();
		eks.put(SamzaCommonParams.ets.name(), (String)message.get(SamzaCommonParams.ets.name()));
		eks.put(SamzaCommonParams.action.name(), (String)((Map<String, Object>) message.get(SamzaCommonParams.edata.name())).get(SamzaCommonParams.action.name()));
		eks.put(SamzaCommonParams.iteration.name(), (String)((Map<String, Object>) message.get(SamzaCommonParams.edata.name())).get(SamzaCommonParams.iteration.name()));
		eks.put(SamzaCommonParams.status.name(), (String)((Map<String, Object>) message.get(SamzaCommonParams.edata.name())).get(SamzaCommonParams.status.name()));
		eks.put(SamzaCommonParams.reqid.name(), (String)message.get(SamzaCommonParams.mid.name()));
		edata.put(SamzaCommonParams.eks.name(), eks);
		edata.put(SamzaCommonParams.level.name(), SamzaCommonParams.INFO.name());
		edata.put(SamzaCommonParams.jobclass.name(), this.jobClass);
		edata.put(SamzaCommonParams.object.name(), message.get("object"));
		
		
		if(StringUtils.equalsIgnoreCase(jobEvendID, "JOBSTARTEVENT")) {
			jobEvent.put(SamzaCommonParams.eid.name(), startJobEventId);
			edata.put(SamzaCommonParams.message.name(), this.jobStartMessage);
		}
		else if(StringUtils.equalsIgnoreCase(jobEvendID, "JOBENDEVENT")) {
			jobEvent.put(SamzaCommonParams.eid.name(), endJobEventId);
			edata.put(SamzaCommonParams.message.name(), this.jobEndMessage);
		}
		
		jobEvent.put(SamzaCommonParams.edata.name(), edata);
		return jobEvent;
	}
	
	private String generateEvent(String logLevel, String message, Map<String, Object> data) {
		String event = PlatformLogger.getBELog(logLevel, message, data, null);
		return event;
	}

	protected boolean isInvalidMessage(Map<String, Object> message) {
		return (message == null || (null != message && message.containsKey("serde")
				&& "error".equalsIgnoreCase((String) message.get("serde"))));
	}

	@Override
	public void window(MessageCollector collector, TaskCoordinator coordinator) throws Exception {
		metrics.clear();
	}
}