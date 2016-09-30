package org.ekstep.language.actorTest;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.ekstep.language.cache.GradeComplexityCache;
import org.ekstep.language.common.enums.LanguageActorNames;
import org.ekstep.language.common.enums.LanguageObjectTypes;
import org.ekstep.language.common.enums.LanguageOperations;
import org.ekstep.language.common.enums.LanguageParams;
import org.ekstep.language.router.LanguageRequestRouterPool;
import org.junit.Assert;
import org.junit.Test;

import com.ilimi.common.dto.Request;
import com.ilimi.common.dto.Response;
import com.ilimi.graph.common.enums.GraphHeaderParams;
import com.ilimi.graph.dac.enums.GraphDACParams;
import com.ilimi.graph.dac.enums.SystemNodeTypes;
import com.ilimi.graph.dac.model.Node;
import com.ilimi.graph.engine.router.GraphEngineManagers;

public class LanguageCacheActorTest extends BaseLanguageTest {
	
	private static ObjectMapper mapper = new ObjectMapper();
	private static Logger LOGGER = LogManager.getLogger(LanguageCacheActorTest.class
			.getName());

	static {
		LanguageRequestRouterPool.init();
	}
	
	@Test
	public void getGradeLevelComplexityTest() throws IOException{
        Request request = new Request();
        request.setManagerName(LanguageActorNames.LANGUAGE_CACHE_ACTOR.name());
        request.setOperation(LanguageOperations.getGradeLevelComplexities.name());
		request.getContext().put(GraphHeaderParams.graph_id.name(),
				TEST_LANGUAGE);
        request.getContext().put(LanguageParams.language_id.name(), TEST_LANGUAGE);
		LOGGER.info("List | Request: " + request);
		Response response = LanguageRequestRepsonseHelper.getResponse(
				request, LOGGER);
		LOGGER.info("List | Response: " + response);		
		Assert.assertEquals("successful", response.getParams().getStatus());
		Map<String, Object> result = response.getResult();
		List<Node> gradeLevelComplexities = (List<Node>) result
				.get("grade_level_complexity");
		Assert.assertNotNull(gradeLevelComplexities);
	}
	
	@Test
	public void LoadCacheTest() throws IOException{
		Node newGradeLevelcomplexity = getNode(TEST_LANGUAGE, "Grade 1", "First", (double) 25, "Rajastan");
		createGradeLevelComplexity(newGradeLevelcomplexity);
		
        Request request = new Request();
        request.setManagerName(LanguageActorNames.LANGUAGE_CACHE_ACTOR.name());
        request.setOperation(LanguageOperations.loadGradeLevelComplexityCache.name());
        request.getContext().put(LanguageParams.language_id.name(), TEST_LANGUAGE);
		request.getContext().put(GraphHeaderParams.graph_id.name(),
				TEST_LANGUAGE);

        LOGGER.info("List | Request: " + request);
		Response response = LanguageRequestRepsonseHelper.getResponse(
				request, LOGGER);
		LOGGER.info("List | Response: " + response);		
		Assert.assertEquals("successful", response.getParams().getStatus());
	}
	
	
	@Test
	public void loadGradeLevelComplexityNodeTest() throws IOException{
		Node newGradeLevelcomplexity = getNode(TEST_LANGUAGE, "Grade 1", "Second", (double) 20, "Rajastan");
		String node2 = createGradeLevelComplexity(newGradeLevelcomplexity);
		
        Request request = new Request();
        request.setManagerName(LanguageActorNames.LANGUAGE_CACHE_ACTOR.name());
        request.setOperation(LanguageOperations.loadGradeLevelComplexity.name());
        request.getContext().put(LanguageParams.language_id.name(), TEST_LANGUAGE);
		request.getContext().put(GraphHeaderParams.graph_id.name(),
				TEST_LANGUAGE);
		request.put(LanguageParams.node_id.name(), node2);
        LOGGER.info("List | Request: " + request);
		Response response = LanguageRequestRepsonseHelper.getResponse(
				request, LOGGER);
		LOGGER.info("List | Response: " + response);		
		Assert.assertEquals("successful", response.getParams().getStatus());
		getGradeLevelComplexityTest();
	}
	
	@Test
	public void validateGradeComplexitySuccessTest()throws IOException{
		
		String apiId = "validate.gradeLevelComplexityNode";
		
		Node newGradeLevelcomplexity = getNode(TEST_LANGUAGE, "Grade 2", "First", (double) 40, "Rajastan");
		String node2 = createGradeLevelComplexity(newGradeLevelcomplexity);
		
		
        Request request = new Request();
        request.setManagerName(LanguageActorNames.LANGUAGE_CACHE_ACTOR.name());
        request.setOperation(LanguageOperations.loadGradeLevelComplexity.name());
        request.getContext().put(LanguageParams.language_id.name(), TEST_LANGUAGE);
		request.getContext().put(GraphHeaderParams.graph_id.name(),
				TEST_LANGUAGE);
		request.put(LanguageParams.node_id.name(), node2);
        LOGGER.info("List | Request: " + request);
		Response response = LanguageRequestRepsonseHelper.getResponse(
				request, LOGGER);
		LOGGER.info("List | Response: " + response);		
		Assert.assertEquals("successful", response.getParams().getStatus());
		
		newGradeLevelcomplexity.getMetadata().put("averageComplexity",(double) 35);
		
        request.setManagerName(LanguageActorNames.LANGUAGE_CACHE_ACTOR.name());
        request.setOperation(LanguageOperations.validateComplexityRange.name());
        request.getContext().put(LanguageParams.language_id.name(), TEST_COMMON_LANGUAGE);
		request.getContext().put(GraphHeaderParams.graph_id.name(),
				TEST_LANGUAGE);
		request.put(LanguageParams.grade_level_complexity.name(), newGradeLevelcomplexity);
        LOGGER.info("List | Request: " + request);
		response = LanguageRequestRepsonseHelper.getResponse(
				request, LOGGER);
		LOGGER.info("List | Response: " + response);		
		Assert.assertEquals("successful", response.getParams().getStatus());

		
	}
	
	
	@Test
	public void validateGradeComplexityFailTest()throws IOException{
		
		Node newGradeLevelcomplexity1 = getNode(TEST_LANGUAGE, "Grade 2", "Second", (double) 30, "Rajastan");
		createGradeLevelComplexity(newGradeLevelcomplexity1);
		
		Node newGradeLevelcomplexity = getNode(TEST_LANGUAGE, "Grade 3", "Second", (double) 50, "Rajastan");
		String node2 = createGradeLevelComplexity(newGradeLevelcomplexity);
		
		
        LoadCacheTest();
		newGradeLevelcomplexity.getMetadata().put("averageComplexity",(double) 10);
		
		Request request = new Request();
        request.setManagerName(LanguageActorNames.LANGUAGE_CACHE_ACTOR.name());
        request.setOperation(LanguageOperations.validateComplexityRange.name());
        request.getContext().put(LanguageParams.language_id.name(), TEST_LANGUAGE);
		request.getContext().put(GraphHeaderParams.graph_id.name(),
				TEST_LANGUAGE);
		request.put(LanguageParams.grade_level_complexity.name(), newGradeLevelcomplexity);
        LOGGER.info("List | Request: " + request);
		Response response = LanguageRequestRepsonseHelper.getResponse(
				request, LOGGER);
		LOGGER.info("List | Response: " + response);		
		Assert.assertEquals("failed", response.getParams().getStatus());

		
	}
	
	private Node getNode(String languageId, String gradeLevel, String languageLevel, Double averageComplexity, String source){
		Node node = new Node();
		node.setObjectType(LanguageObjectTypes.GradeLevelComplexity.name());
		node.setNodeType(SystemNodeTypes.DATA_NODE.name());
		Map<String, Object> metaData = new HashMap<String, Object>();
		metaData.put("languageId", languageId);
		metaData.put("gradeLevel", gradeLevel);
		metaData.put("languageLevel", languageLevel);
		metaData.put("averageComplexity", averageComplexity);
		metaData.put("sources", Arrays.asList(source));
		
		node.setMetadata(metaData);
		return node;
	}
	
	private String createGradeLevelComplexity(Node node) {
        Request request = new Request();
        request.setManagerName(GraphEngineManagers.NODE_MANAGER);
        request.setOperation("createDataNode");
        request.getContext().put(LanguageParams.language_id.name(), TEST_COMMON_LANGUAGE);
        request.getContext().put(GraphHeaderParams.graph_id.name(),
        		TEST_COMMON_LANGUAGE);
        request.put(GraphDACParams.node.name(), node);
		Response res = LanguageCommonTestHelper.getResponse(request, LOGGER);		
		Assert.assertEquals("successful", res.getParams().getStatus());
		Map<String, Object> result = res.getResult();
		String node_id = (String) result.get("node_id");
		return node_id;
	}

	private String updateGradeLevelComplexity(Node node) {
        Request request = new Request();
        request.setManagerName(GraphEngineManagers.NODE_MANAGER);
        request.setOperation("updateDataNode");
        request.getContext().put(LanguageParams.language_id.name(), TEST_COMMON_LANGUAGE);
        request.getContext().put(GraphHeaderParams.graph_id.name(),
        		TEST_COMMON_LANGUAGE);
        request.put(GraphDACParams.node_id.name(), node.getIdentifier());
        request.put(GraphDACParams.node.name(), node);
		Response res = LanguageCommonTestHelper.getResponse(request, LOGGER);		
		Assert.assertEquals("successful", res.getParams().getStatus());
		Map<String, Object> result = res.getResult();
		String node_id = (String) result.get("node_id");
		return node_id;
	}
}