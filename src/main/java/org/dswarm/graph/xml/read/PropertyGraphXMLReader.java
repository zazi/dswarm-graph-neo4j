/**
 * This file is part of d:swarm graph extension.
 *
 * d:swarm graph extension is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * d:swarm graph extension is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with d:swarm graph extension.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.dswarm.graph.xml.read;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.codehaus.stax2.XMLOutputFactory2;

import org.dswarm.common.types.Tuple;
import org.dswarm.common.web.URI;
import org.dswarm.graph.DMPGraphException;
import org.dswarm.graph.GraphIndexStatics;
import org.dswarm.graph.gdm.read.PropertyGraphGDMReader;
import org.dswarm.graph.json.LiteralNode;
import org.dswarm.graph.json.NodeType;
import org.dswarm.graph.json.Predicate;
import org.dswarm.graph.json.Statement;
import org.dswarm.graph.model.GraphStatics;
import org.dswarm.graph.versioning.Range;
import org.dswarm.graph.versioning.VersioningStatics;
import org.dswarm.graph.xml.utils.XMLStreamWriterUtils;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.hp.hpl.jena.vocabulary.RDF;

/**
 * @author tgaengler
 */
public class PropertyGraphXMLReader implements XMLReader {

	private static final Logger LOG = LoggerFactory.getLogger(PropertyGraphXMLReader.class);

	private static final String XML_VERSION = "1.0";
	private static final XMLOutputFactory2 xmlOutputFactory;

	static {

		System.setProperty("javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl");

		xmlOutputFactory = (XMLOutputFactory2) XMLOutputFactory.newFactory();
		xmlOutputFactory.configureForSpeed();
	}

	private final String dataModelUri;
	private final URI    recordTagURI;

	private final Map<String, Tuple<Predicate, URI>> predicates = new HashMap<>();

	private final GraphDatabaseService database;

	//private Resource currentResource;
	//private final Map<Long, Statement> currentResourceStatements = new HashMap<>();

	private Integer version;

	private Transaction tx = null;

	public PropertyGraphXMLReader(final String recordClassUriArg, final String dataModelUriArg, final Integer versionArg,
			final GraphDatabaseService databaseArg) throws DMPGraphException {

		recordTagURI = new URI(recordClassUriArg);
		dataModelUri = dataModelUriArg;
		database = databaseArg;

		if (versionArg != null) {

			version = versionArg;
		} else {

			tx = database.beginTx();

			PropertyGraphXMLReader.LOG.debug("start read XML TX");

			try {

				version = getLatestVersion();
			} catch (final Exception e) {

				final String message = "couldn't retrieve latest version successfully";

				PropertyGraphXMLReader.LOG.error(message, e);
				PropertyGraphXMLReader.LOG.debug("couldn't finish read XML TX successfully");

				tx.failure();
				tx.close();

				throw new DMPGraphException(message);
			}
		}
	}

	@Override public XMLStreamWriter read(final OutputStream outputStream) throws DMPGraphException, XMLStreamException {

		if (tx == null) {

			try {

				PropertyGraphXMLReader.LOG.debug("start read XML TX");

				tx = database.beginTx();
			} catch (final Exception e) {

				final String message = "couldn't acquire tx successfully";

				PropertyGraphXMLReader.LOG.error(message, e);
				PropertyGraphXMLReader.LOG.debug("couldn't finish read XML TX successfully");

				throw new DMPGraphException(message);
			}
		}

		ResourceIterator<Node> recordNodesIter = null;

		try {

			final Label recordClassLabel = DynamicLabel.label(recordTagURI.toString());

			final ResourceIterable<Node> recordNodes = database.findNodesByLabelAndProperty(recordClassLabel, GraphStatics.DATA_MODEL_PROPERTY,
					dataModelUri);

			if (recordNodes == null) {

				tx.success();

				PropertyGraphXMLReader.LOG.debug("there are no root nodes for '" + recordClassLabel + "' in data model '" + dataModelUri
						+ "'finished read XML TX successfully");

				return null;
			}

			recordNodesIter = recordNodes.iterator();

			if (recordNodesIter == null) {

				tx.success();

				PropertyGraphXMLReader.LOG.debug("there are no root nodes for '" + recordClassLabel + "' in data model '" + dataModelUri
						+ "'finished read XML TX successfully");

				return null;
			}

			if (!recordNodesIter.hasNext()) {

				recordNodesIter.close();
				tx.success();

				PropertyGraphXMLReader.LOG.debug("there are no root nodes for '" + recordClassLabel + "' in data model '" + dataModelUri
						+ "'finished read XML TX successfully");

				return null;
			}

			// (XMLStreamWriter2)
			final XMLStreamWriter writer = xmlOutputFactory.createXMLStreamWriter(outputStream);

			writer.writeStartDocument(Charsets.UTF_8.toString(), XML_VERSION);
			// TODO: write root XML elements tags (from root AP)

			final XMLRelationshipHandler relationshipHandler = new CBDRelationshipHandler(writer);
			final XMLNodeHandler nodeHandler = new CBDNodeHandler(writer, relationshipHandler);
			final XMLNodeHandler startNodeHandler = new CBDStartNodeHandler(writer, relationshipHandler);

			// iterate over the records
			while (recordNodesIter.hasNext()) {

				final Node recordNode = recordNodesIter.next();
				final String resourceUri = (String) recordNode.getProperty(GraphStatics.URI_PROPERTY, null);

				if (resourceUri == null) {

					LOG.debug("there is no resource URI at record node '" + recordNode.getId() + "'");

					continue;
				}

				//currentResource = new Resource(resourceUri);
				// open record XML tag
				XMLStreamWriterUtils.writeXMLElementTag(writer, recordTagURI);

				startNodeHandler.handleNode(recordNode);

				// we should write the content directly, i.e., not at once
				//				if (!currentResourceStatements.isEmpty()) {
				//
				//					// note, this is just an integer number (i.e. NOT long)
				//					final int mapSize = currentResourceStatements.size();
				//
				//					long i = 0;
				//
				//					final Set<Statement> statements = new LinkedHashSet<>();
				//
				//					while (i < mapSize) {
				//
				//						i++;
				//
				//						final Statement statement = currentResourceStatements.get(i);
				//
				//						statements.add(statement);
				//					}
				//
				//					currentResource.setStatements(statements);
				//				}
				//
				//				model.addResource(currentResource);
				//
				//				currentResourceStatements.clear();

				// close record
				writer.writeEndElement();
			}

			recordNodesIter.close();
			tx.success();

			PropertyGraphXMLReader.LOG.debug("finished read XML TX successfully");

			// TODO: close root tags
			// close document
			writer.writeEndDocument();

			// TODO: do this, when necessary and needed
			//writer.flush();
			//writer.close();

			return writer;
		} catch (final Exception e) {

			PropertyGraphXMLReader.LOG.error("couldn't finished read XML TX successfully", e);

			if (recordNodesIter != null) {

				recordNodesIter.close();
			}

			tx.failure();
		} finally {

			PropertyGraphXMLReader.LOG.debug("finished read GDM TX finally");

			tx.close();
		}

		// not fine, but okay for now ;)
		return null;
	}

	private class CBDNodeHandler implements XMLNodeHandler {

		private final XMLStreamWriter        writer;
		private final XMLRelationshipHandler relationshipHandler;

		protected CBDNodeHandler(final XMLStreamWriter writerArg, final XMLRelationshipHandler relationshipHandlerArg) {

			writer = writerArg;
			relationshipHandler = relationshipHandlerArg;
			((CBDRelationshipHandler) relationshipHandler).setNodeHandler(this);
		}

		@Override
		public void handleNode(final Node node) throws DMPGraphException, XMLStreamException {

			// TODO: find a better way to determine the end of a resource description, e.g., add a property "resource" to each
			// node that holds the uri of the resource (record)
			// => maybe we should find an appropriated cypher query as replacement for this processing
			if (!node.hasProperty(GraphStatics.URI_PROPERTY)) {

				final Iterable<Relationship> relationships = node.getRelationships(Direction.OUTGOING);

				boolean containsOnlyXMLAttributes = true;

				for (final Relationship relationship : relationships) {

					final Integer validFrom = (Integer) relationship.getProperty(VersioningStatics.VALID_FROM_PROPERTY, null);
					final Integer validTo = (Integer) relationship.getProperty(VersioningStatics.VALID_TO_PROPERTY, null);

					if (validFrom != null && validTo != null) {

						if (Range.range(validFrom, validTo).contains(version)) {

							relationshipHandler.handleRelationship(relationship);
						}
					} else {

						// TODO: remove this later, when every stmt is versioned
						relationshipHandler.handleRelationship(relationship);
					}

					if(!((CBDRelationshipHandler) relationshipHandler).predicateIsXMLAttribute()) {

						containsOnlyXMLAttributes = false;
					}
				}

				// close element here (?) - however, then we need to know whether it was a branch with elements or not
				if(!containsOnlyXMLAttributes) {

					// close inner XML tag
					writer.writeEndElement();
				}

				// try to pop something from the stack
				// TODO: do we really need the stack?
				((CBDRelationshipHandler) relationshipHandler).popRecordBNodeStack();
			}
		}
	}

	private class CBDStartNodeHandler implements XMLNodeHandler {

		private final XMLStreamWriter        writer;
		private final XMLRelationshipHandler relationshipHandler;

		protected CBDStartNodeHandler(final XMLStreamWriter writerArg, final XMLRelationshipHandler relationshipHandlerArg) {

			writer = writerArg;
			relationshipHandler = relationshipHandlerArg;
		}

		@Override
		public void handleNode(final Node node) throws DMPGraphException, XMLStreamException {

			// TODO: find a better way to determine the end of a resource description, e.g., add a property "resource" to each
			// (this is the case for model that came as GDM JSON)
			// node that holds the uri of the resource (record)
			if (node.hasProperty(GraphStatics.URI_PROPERTY)) {

				final Iterable<Relationship> relationships = node.getRelationships(Direction.OUTGOING);

				boolean containsOnlyXMLAttributes = true;

				for (final Relationship relationship : relationships) {

					final Integer validFrom = (Integer) relationship.getProperty(VersioningStatics.VALID_FROM_PROPERTY, null);
					final Integer validTo = (Integer) relationship.getProperty(VersioningStatics.VALID_TO_PROPERTY, null);

					if (validFrom != null && validTo != null) {

						if (Range.range(validFrom, validTo).contains(version)) {

							relationshipHandler.handleRelationship(relationship);
						}
					} else {

						// TODO: remove this later, when every stmt is versioned
						relationshipHandler.handleRelationship(relationship);
					}

					if(!((CBDRelationshipHandler) relationshipHandler).predicateIsXMLAttribute()) {

						containsOnlyXMLAttributes = false;
					}
				}

				// close element here (?) - however, then we need to know whether it was a branch with elements or not
				if(!containsOnlyXMLAttributes) {

					// close inner XML tag
					writer.writeEndElement();
				}

				// try to pop something from the stack
				// TODO: do we really need the stack?
				((CBDRelationshipHandler) relationshipHandler).popRecordBNodeStack();
			}
		}
	}

	private class CBDRelationshipHandler implements XMLRelationshipHandler {

		private final PropertyGraphGDMReader propertyGraphGDMReader = new PropertyGraphGDMReader();

		private final XMLStreamWriter writer;
		private       XMLNodeHandler  nodeHandler;
		private Statement previousStatement = null;
		private Statement currentStatement  = null;

		private boolean predicateIsXMLAttribute = false;

		private Stack<org.dswarm.graph.json.Node> recordBNodeStack = new Stack<>();


		protected CBDRelationshipHandler(final XMLStreamWriter writerArg) {

			writer = writerArg;
		}

		protected void setNodeHandler(final XMLNodeHandler nodeHandlerArg) {

			nodeHandler = nodeHandlerArg;
		}

		public void popRecordBNodeStack() {

			if(!recordBNodeStack.empty()) {

				recordBNodeStack.pop();
			}
		}

		public boolean predicateIsXMLAttribute() {

			return predicateIsXMLAttribute;
		}

		@Override
		public void handleRelationship(final Relationship rel) throws DMPGraphException, XMLStreamException {

			// note: we can also optionally check for the "resource property at the relationship (this property will only be
			// written right now for model that came as GDM JSON)
			if (rel.getProperty(GraphStatics.DATA_MODEL_PROPERTY).equals(dataModelUri)) {

				// subject => start element (???)

				final Node subjectNode = rel.getStartNode();
				final org.dswarm.graph.json.Node subjectGDMNode = propertyGraphGDMReader.readSubject(subjectNode);
				// => TODO, we need to compare the node, with the previous node, to write the content (key(predicate)/value(object)) into the current element or another of this tag
				// TODO: how to determine, when we should close a tag (or parent tag etc.) => we need to keep a stack, of open elements

				// predicate => XML element or XML attribute

				final String predicateString = rel.getType().name();
				//final URI uriPredicate = URI.create(predicate);
				final Tuple<Predicate, URI> predicateTuple = getPredicate(predicateString);
				final URI predicateURI = predicateTuple.v2();

				// object => XML Element value or XML attribute value or further recursion

				final Node objectNode = rel.getEndNode();
				final org.dswarm.graph.json.Node objectGDMNode = propertyGraphGDMReader.readObject(objectNode);

				currentStatement = new Statement(subjectGDMNode, predicateTuple.v1(), objectGDMNode);

				if (!(RDF.type.getURI().equals(predicateString) || RDF.value.getURI().equals(predicateString)) && NodeType.Literal
						.equals(objectGDMNode.getType())) {

					// predicate is an XML Attribute => write XML Attribute to this XML Element
					predicateIsXMLAttribute = true;

					XMLStreamWriterUtils.writeXMLAttribute(writer, predicateURI, ((LiteralNode) objectGDMNode).getValue());
				} else if
						(RDF.value.getURI().equals(predicateString) && NodeType.Literal
								.equals(objectGDMNode.getType())) {

					// predicate is an XML Element

					// TODO: maybe should compare with subject from stack, i.e., this might not always be the direct predecessor
					final boolean subjectsAreEqual =
							previousStatement != null && previousStatement.getSubject().equals(currentStatement.getSubject());

					if (subjectsAreEqual) {

						// close previous

						writer.writeEndElement();
					} else {

						if(!recordBNodeStack.empty()) {

							// TODO: how to determine the right place, when one should close a bnode
						}
					}

					XMLStreamWriterUtils.writeXMLElementTag(writer, predicateURI);

					// TODO: what should we do with objects that are resources?
					if (objectGDMNode.getType().equals(NodeType.Literal)) {

						writer.writeCData(((LiteralNode) objectGDMNode).getValue());
					}
				} else {

					// ???
				}

				//
				//				// index should never be null (when resource was written as GDM JSON)
				//				final Long index = (Long) rel.getProperty(GraphStatics.INDEX_PROPERTY, null);
				//
				//				if (index != null) {
				//
				//					currentResourceStatements.put(index, statement);
				//				} else {
				//
				//					// note maybe improve this here (however, this is the case for model that where written from RDF)
				//
				//					currentResource.addStatement(statement);
				//				}

				previousStatement = currentStatement;
				currentStatement = null;

				// note: we can only iterate deeper into one direction, i.e., we need to cut the stream, when the object is another resource => i.e. we iterate only when object are bnodes
				// TODO: what should we do with objects that are resources?
				if (objectGDMNode.getType().equals(NodeType.BNode)) {

					recordBNodeStack.push(objectGDMNode);

					// continue traversal with object node
					nodeHandler.handleNode(rel.getEndNode());
				}
			}
		}
	}

	private int getLatestVersion() {

		int latestVersion = 1;

		final Index<Node> resources = database.index().forNodes(GraphIndexStatics.RESOURCES_INDEX_NAME);
		final IndexHits<Node> hits = resources.get(GraphStatics.URI, dataModelUri);

		if (hits != null && hits.iterator().hasNext()) {

			final Node dataModelNode = hits.iterator().next();
			final Integer latestVersionFromDB = (Integer) dataModelNode.getProperty(VersioningStatics.LATEST_VERSION_PROPERTY, null);

			if (latestVersionFromDB != null) {

				latestVersion = latestVersionFromDB;
			}
		}

		if (hits != null) {

			hits.close();
		}

		return latestVersion;
	}

	private Tuple<Predicate, URI> getPredicate(final String predicateString) {

		if (!predicates.containsKey(predicateString)) {

			predicates.put(predicateString, Tuple.tuple(new Predicate(predicateString), new URI(predicateString)));
		}

		return predicates.get(predicateString);
	}
}