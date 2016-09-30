package require java
java::import -package java.util ArrayList List
java::import -package java.util HashMap Map
java::import -package com.ilimi.graph.dac.model Node Relation

proc getOutRelations {graph_node} {
	set outRelations [java::prop $graph_node "outRelations"]
	return $outRelations
}

proc getInRelations {graph_node} {
	set inRelations [java::prop $graph_node "inRelations"]
	return $inRelations
}

proc isNotEmpty {relations} {
	set exist false
	set hasRelations [java::isnull $relations]
	if {$hasRelations == 0} {
		set relationsSize [$relations size] 
		if {$relationsSize > 0} {
			set exist true
		}
	}
	return $exist
}

proc getNodeRelationIds {graph_node relationType property languages} {

	set relationIds [java::new ArrayList]
	set outRelations [getOutRelations $graph_node]
	set hasRelations [isNotEmpty $outRelations]
	if {$hasRelations} {
		java::for {Relation relation} $outRelations {
			if {[java::prop $relation "endNodeObjectType"] == $relationType} {
				set prop_value [java::prop $relation $property]
				set idArray [$prop_value split ":"]
				set arr_instance [java::instanceof $idArray {String[]}]
				set index [expr 0 ]
				if {$arr_instance == 1} {			
					set idArray [java::cast {String[]} $idArray]
				set idArrayLength [$idArray length]
				if {$idArrayLength == 1} {
					set idArray [$prop_value split "_"]
					set idArrayLength [$idArray length]
				}
				
				if {$idArrayLength > 1} {
					set language [$idArray get $index]
					set synsetContains [$languages contains $language]
					if {$synsetContains == 1} {
						$relationIds add $prop_value
					}
				}
				}
				
			}
		}
	}
	return $relationIds
}

proc getInNodeRelationIds {graph_node relationType property} {

	set relationIds [java::new ArrayList]
	set inRelations [getInRelations $graph_node]
	set hasRelations [isNotEmpty $inRelations]
	if {$hasRelations} {
		java::for {Relation relation} $inRelations {
			if {[java::prop $relation "startNodeObjectType"] == $relationType} {
				set prop_value [java::prop $relation $property]
				$relationIds add $prop_value
			}
		}
	}
	return $relationIds
}

set object_type "TranslationSet"
set node_id $wordId
set language_id $languageId
set get_node_response [getDataNode $language_id $node_id]
set get_node_response_error [check_response_error $get_node_response]
if {$get_node_response_error} {
	return $get_node_response
}


set word_node [get_resp_value $get_node_response "node"]
set synonym_list [getInNodeRelationIds $graph_node "Synset" "startNodeId"]
set synset_list [java::new ArrayList]
$synset_list add synonym_list

set relationMap [java::new HashMap]
$relationMap put "name" "hasMembers"
$relationMap put "objectType" "Synset"
$relationMap put "identifiers" $synset_list

set criteria_list [java::new ArrayList]
$criteria_list add $relationMap

set criteria_map [java::new HashMap]
$criteria_map put "nodeType" "SET"
$criteria_map put "objectType" $object_type
$criteria_map put "relationCriteria" $criteria_list

set graph_id "translations"

set search_criteria [create_search_criteria $criteria_map]
set search_response [searchNodes $graph_id $search_criteria]
set check_error [check_response_error $search_response]
if {$check_error} {
	return $search_response;
} else {
	set result_map [java::new HashMap]
	java::try {
		set graph_nodes [get_resp_value $search_response "node_list"]
		set set_metadata [java::new HashMap]
		set word_id_list [java::new ArrayList]
		java::for {Node graph_node} $graph_nodes {
			set synset_ids [getNodeRelationIds $graph_node "Synset" "endNodeId" $languages]
			set not_empty_list [isNotEmpty $synset_ids]
			if {$not_empty_list} {
				$synset_id_list addAll $synset_ids
				set searchResponse [multiLanguageWordSearch $synset_id_list]
				set searchResultsMap [$searchResponse getResult]
				return $searchResultsMap
			}
	
		}
		

	} catch {Exception err} {
    	$result_map put "error" [$err getMessage]
	}
	set response_list [create_response $result_map]
	return $response_list
}