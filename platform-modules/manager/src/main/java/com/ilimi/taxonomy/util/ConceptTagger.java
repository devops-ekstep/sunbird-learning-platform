package com.ilimi.taxonomy.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ilimi.common.dto.Request;
import com.ilimi.common.dto.Response;
import com.ilimi.common.mgr.BaseManager;
import com.ilimi.graph.dac.enums.GraphDACParams;
import com.ilimi.graph.dac.enums.RelationTypes;
import com.ilimi.graph.dac.model.Node;
import com.ilimi.graph.dac.model.Relation;
import com.ilimi.graph.engine.router.GraphEngineManagers;
import com.ilimi.graph.enums.CollectionTypes;
import com.ilimi.taxonomy.content.enums.ContentWorkflowPipelineParams;

/**
 * <code>ConceptTagger</code> is used to tag additional concepts for a given
 * content node. The content is tagged with all concepts associated with the
 * items used in the content.
 * 
 * This class doesnot remove any previously tagged concepts even after an item
 * associated with the content is removed.
 * 
 * @author rayulu
 *
 */
public class ConceptTagger extends BaseManager {

	/** The logger. */
	private static Logger LOGGER = LogManager.getLogger(ConceptTagger.class.getName());

	/**
	 * This method triggers an asynchronous method to tag the given content
	 * object with additional concepts that are tagged with items used in the
	 * content.
	 * 
	 * @param graphId
	 *            identifier of the domain graph
	 * @param contentId
	 *            identifier of the content to be tagged with new concepts
	 * @param content
	 *            the content node object
	 */
	public void tagConcepts(String graphId, String contentId, Node content) {
		ExecutorService pool = null;
		try {
			pool = Executors.newFixedThreadPool(1);
			LOGGER.info("Call Async method to tag concepts for " + contentId);
			pool.execute(new Runnable() {
				@Override
				public void run() {
					tagNewConcepts(graphId, contentId, content);
				}
			});
		} catch (Exception e) {
			LOGGER.error("Error sending Content2Vec request", e);
		} finally {
			if (null != pool)
				pool.shutdown();
		}

	}

	/**
	 * This method gets the list of concepts associated with items that are
	 * members of item sets used in the content. These concepts are then
	 * associated with the input content object.
	 * 
	 * @param graphId
	 *            identifier of the domain graph
	 * @param contentId
	 *            identifier of the content to be tagged with new concepts
	 * @param content
	 *            the content node object
	 */
	private void tagNewConcepts(String graphId, String contentId, Node content) {
		List<String> itemSets = new ArrayList<String>();
		List<Relation> outRelations = content.getOutRelations();
		LOGGER.info("Get item sets associated with the content: " + contentId);
		if (null != outRelations && !outRelations.isEmpty()) {
			for (Relation rel : outRelations) {
				if (StringUtils.equalsIgnoreCase("ItemSet", rel.getEndNodeObjectType())
						&& !itemSets.contains(rel.getEndNodeId()))
					itemSets.add(rel.getEndNodeId());
			}
		}
		if (null != itemSets && !itemSets.isEmpty()) {
			LOGGER.info("Number of item sets: " + itemSets.size());
			Set<String> itemIds = new HashSet<String>();
			for (String itemSet : itemSets) {
				List<String> members = getItemSetMembers(graphId, itemSet);
				if (null != members && !members.isEmpty())
					itemIds.addAll(members);
			}
			LOGGER.info("Total number of items: " + itemIds.size());
			if (!itemIds.isEmpty()) {
				List<String> items = new ArrayList<String>(itemIds);
				List<String> conceptIds = getItemConcepts(graphId, items);
				if (null != conceptIds && !conceptIds.isEmpty()) {
					LOGGER.info("Number of concepts: " + conceptIds.size());
					Request request = getRequest(graphId, GraphEngineManagers.GRAPH_MANAGER, "addOutRelations",
							GraphDACParams.start_node_id.name(), contentId);
					request.put(GraphDACParams.relation_type.name(), RelationTypes.ASSOCIATED_TO.relationName());
					request.put(GraphDACParams.end_node_id.name(), conceptIds);
					Response response = getResponse(request, LOGGER);
					if (checkError(response))
						LOGGER.error(
								"Error adding concepts to content: " + contentId + " | Error: " + response.getParams());
					else
						LOGGER.info("New concepts tagged successfully: " + contentId);
				}
			}
		}
	}

	/**
	 * Returns the list of identifiers of the items that are members of the
	 * given item set.
	 * 
	 * @param graphId
	 *            identifier of the domain graph
	 * @param itemSetId
	 *            identifier of the item set
	 * @return list of identifiers of member items
	 */
	@SuppressWarnings("unchecked")
	private List<String> getItemSetMembers(String graphId, String itemSetId) {
		LOGGER.info("Get members of items set: " + itemSetId);
		Request request = getRequest(graphId, GraphEngineManagers.COLLECTION_MANAGER, "getCollectionMembers",
				GraphDACParams.collection_id.name(), itemSetId);
		request.put(GraphDACParams.collection_type.name(), CollectionTypes.SET.name());
		Response response = getResponse(request, LOGGER);
		if (!checkError(response)) {
			List<String> members = (List<String>) response.get(GraphDACParams.members.name());
			return members;
		}
		return null;
	}

	/**
	 * Returns the list of identifiers of concepts associated with the given
	 * list of assessment items.
	 * 
	 * @param graphId
	 *            identifier of the domain graph
	 * @param items
	 *            list of assessment item identifiers
	 * @return list of concept identifiers associated with the input assessment
	 *         items
	 */
	@SuppressWarnings("unchecked")
	private List<String> getItemConcepts(String graphId, List<String> items) {
		Request request = getRequest(graphId, GraphEngineManagers.SEARCH_MANAGER, "getDataNodes",
				GraphDACParams.node_ids.name(), items);
		Response response = getResponse(request, LOGGER);
		List<String> conceptIds = new ArrayList<String>();
		if (!checkError(response)) {
			List<Node> nodes = (List<Node>) response.get(GraphDACParams.node_list.name());
			if (null != nodes && !nodes.isEmpty()) {
				for (Node node : nodes) {
					List<Relation> outRelations = node.getOutRelations();
					if (null != outRelations && !outRelations.isEmpty()) {
						for (Relation rel : outRelations) {
							if (StringUtils.equalsIgnoreCase("Concept", rel.getEndNodeObjectType())
									&& !conceptIds.contains(rel.getEndNodeId())) {
								String status = (String) rel.getEndNodeMetadata().get(ContentWorkflowPipelineParams.status.name());
								if (StringUtils.equalsIgnoreCase(ContentWorkflowPipelineParams.Live.name(), status))
									conceptIds.add(rel.getEndNodeId());
							}
						}
					}
				}
			}
		}
		return conceptIds;
	}
}