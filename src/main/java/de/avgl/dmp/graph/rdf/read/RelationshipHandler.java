package de.avgl.dmp.graph.rdf.read;

import org.neo4j.graphdb.Relationship;


/**
 * 
 * @author tgaengler
 *
 */
public interface RelationshipHandler {

	public void handleRelationship(Relationship rel);
}
