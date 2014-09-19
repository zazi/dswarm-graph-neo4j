package org.dswarm.graph.gdm;

import org.dswarm.graph.DMPGraphException;
import org.dswarm.graph.SimpleNeo4jProcessor;
import org.dswarm.graph.json.ResourceNode;
import org.dswarm.graph.model.GraphStatics;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.IndexHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author tgaengler
 */
public class SimpleGDMNeo4jProcessor extends GDMNeo4jProcessor {

	private static final Logger	LOG	= LoggerFactory.getLogger(SimpleGDMNeo4jProcessor.class);

	public SimpleGDMNeo4jProcessor(final GraphDatabaseService database) throws DMPGraphException {

		super(new SimpleNeo4jProcessor(database));
	}

	@Override
	protected IndexHits<Node> getResourceNodeHits(final ResourceNode resource) {

		return processor.getResourcesIndex().get(GraphStatics.URI, resource.getUri());
	}
}
