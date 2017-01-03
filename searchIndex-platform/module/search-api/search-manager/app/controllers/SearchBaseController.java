package controllers;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilimi.common.dto.Request;
import com.ilimi.common.dto.RequestParams;

import play.mvc.Controller;
import play.mvc.Http.RequestBody;

public class SearchBaseController extends Controller {

	private static final String API_ID_PREFIX = "ekstep";
	protected ObjectMapper mapper = new ObjectMapper();
	private static Logger LOGGER = LogManager.getLogger(SearchBaseController.class.getName());

	protected String getAPIId(String apiId) {
		return API_ID_PREFIX + "." + apiId;
	}
	
	protected String getAPIVersion(String path) {
		String version = "3.0";
		if(path.contains("/v2")||path.contains("/search-service")){
			version = "2.0";
		} else if (path.contains("/v3")){
			version = "3.0";
		}
		return version;
	}

	@SuppressWarnings("unchecked")
	protected Request getRequest(RequestBody requestBody, String apiId, String path) {
		LOGGER.info(apiId);
		Request request = new Request();
		if (null != requestBody) {
			JsonNode data = requestBody.asJson();
			Map<String, Object> requestMap = mapper.convertValue(data, Map.class);
			if (null != requestMap && !requestMap.isEmpty()) {
				String id = requestMap.get("id") == null ? getAPIId(apiId) : (String) requestMap.get("id");
				String ver = requestMap.get("ver") == null ? getAPIVersion(path) : (String) requestMap.get("ver");
				String ts = (String) requestMap.get("ts");
				request.setId(id);
				request.setVer(ver);
				request.setTs(ts);
				Object reqParams = requestMap.get("params");
				if (null != reqParams) {
					try {
						RequestParams params = (RequestParams) mapper.convertValue(reqParams, RequestParams.class);
						request.setParams(params);
					} catch (Exception e) {
					}
				}
				Object requestObj = requestMap.get("request");
				if (null != requestObj) {
					try {
						String strRequest = mapper.writeValueAsString(requestObj);
						Map<String, Object> map = mapper.readValue(strRequest, Map.class);
						if (null != map && !map.isEmpty())
							request.setRequest(map);
					} catch (Exception e) {
					}
				}
			}
		} else {
			request.setId(apiId);
			request.setVer(getAPIVersion(path));
		}
		return request;
	}

}