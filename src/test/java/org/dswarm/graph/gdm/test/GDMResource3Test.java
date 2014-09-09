package org.dswarm.graph.gdm.test;

import java.io.IOException;
import java.net.URL;

import javax.ws.rs.core.MediaType;

import junit.framework.Assert;

import org.dswarm.graph.json.util.Util;
import org.dswarm.graph.test.BasicResourceTest;
import org.dswarm.graph.test.Neo4jDBWrapper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.multipart.BodyPart;
import com.sun.jersey.multipart.MultiPart;

/**
 * @author tgaengler
 */
public abstract class GDMResource3Test extends BasicResourceTest {

	private static final Logger	LOG	= LoggerFactory.getLogger(GDMResource3Test.class);

	private final ObjectMapper	objectMapper;

	public GDMResource3Test(final Neo4jDBWrapper neo4jDBWrapper, final String dbTypeArg) {

		super(neo4jDBWrapper, "/gdm", dbTypeArg);

		objectMapper = Util.getJSONObjectMapper();
		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
	}

	@Test
	public void mabxmlVersioningTest() throws IOException {

		final ObjectNode requestJson = getMABXMLContentSchema();

		readGDMFromDBThatWasWrittenAsGDM(requestJson, "versioning/mabxml_dmp.gson", "versioning/mabxml_dmp2.gson",
				"http://data.slub-dresden.de/resources/1", "http://www.ddb.de/professionell/mabxml/mabxml-1.xsd#datensatzType", 157, 149);
	}

	@Test
	public void csvVersioningTest() throws IOException {

		final ObjectNode requestJson = objectMapper.createObjectNode();
		requestJson.put("record_identifier_attribute_path", "http://data.slub-dresden.de/resources/1/schema#EBL+ID");

		readGDMFromDBThatWasWrittenAsGDM(requestJson, "versioning/Testtitel_MDunitz-US-TitleSummaryReport132968_01.csv.gson",
				"versioning/Testtitel_MDunitz-US-TitleSummaryReport132968_02.csv.gson", "http://data.slub-dresden.de/resources/2",
				"http://data.slub-dresden.de/resources/1/schema#RecordType", 36, 35);
	}

	@Test
	public void selectedMabxmlVersioning01Test() throws IOException {

		final ObjectNode requestJson = getMABXMLContentSchema();

		readGDMFromDBThatWasWrittenAsGDM(requestJson, "versioning/selectedOriginalsDump2011_01_v1.xml.gson", "versioning/selectedUpdates_01_v2.xml.gson",
				"http://data.slub-dresden.de/resources/3", "http://www.ddb.de/professionell/mabxml/mabxml-1.xsd#datensatzType", 113, 95);
	}

	@Test
	public void selectedMabxmlVersioning02Test() throws IOException {

		final ObjectNode requestJson = getMABXMLContentSchema();

		readGDMFromDBThatWasWrittenAsGDM(requestJson, "versioning/selectedOriginalsDump2011_02_v1.xml.gson", "versioning/selectedUpdates_02_v2.xml.gson",
				"http://data.slub-dresden.de/resources/4", "http://www.ddb.de/professionell/mabxml/mabxml-1.xsd#datensatzType", 75, 55);
	}

	private void readGDMFromDBThatWasWrittenAsGDM(final ObjectNode contentSchemaRequestJSON, final String resourcePathV1,
			final String resourcePathV2, final String resourceGraphURI, final String recordClassURI, final long statementCountCurrentVersion,
			final long statementCountV1) throws IOException {

		LOG.debug("start read test for GDM resource at " + dbType + " DB");

		writeGDMToDBInternal(resourcePathV1, resourceGraphURI);
		writeGDMToDBInternalWithContentSchema(resourcePathV2, resourceGraphURI, contentSchemaRequestJSON);

		final ObjectMapper objectMapper = Util.getJSONObjectMapper();
		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
		final ObjectNode requestJson = objectMapper.createObjectNode();

		requestJson.put("record_class_uri", recordClassURI);
		requestJson.put("resource_graph_uri", resourceGraphURI);

		final String requestJsonString = objectMapper.writeValueAsString(requestJson);

		// POST the request
		final ClientResponse response = target().path("/get").type(MediaType.APPLICATION_JSON_TYPE).accept(MediaType.APPLICATION_JSON)
				.post(ClientResponse.class, requestJsonString);

		Assert.assertEquals("expected 200", 200, response.getStatus());

		final String body = response.getEntity(String.class);

		final org.dswarm.graph.json.Model model = objectMapper.readValue(body, org.dswarm.graph.json.Model.class);

		LOG.debug("read '" + model.size() + "' statements");

		Assert.assertEquals("the number of statements should be " + statementCountCurrentVersion, statementCountCurrentVersion, model.size());

		// read first version
		final ObjectNode requestJson2 = objectMapper.createObjectNode();

		requestJson2.put("record_class_uri", recordClassURI);
		requestJson2.put("resource_graph_uri", resourceGraphURI);
		requestJson2.put("version", 1);

		final String requestJsonString2 = objectMapper.writeValueAsString(requestJson2);

		// POST the request
		final ClientResponse response2 = target().path("/get").type(MediaType.APPLICATION_JSON_TYPE).accept(MediaType.APPLICATION_JSON)
				.post(ClientResponse.class, requestJsonString2);

		Assert.assertEquals("expected 200", 200, response2.getStatus());

		final String body2 = response2.getEntity(String.class);

		final org.dswarm.graph.json.Model model2 = objectMapper.readValue(body2, org.dswarm.graph.json.Model.class);

		LOG.debug("read '" + model2.size() + "' statements");

		Assert.assertEquals("the number of statements should be " + statementCountV1, statementCountV1, model2.size());

		LOG.debug("finished read test for GDM resource at " + dbType + " DB");
	}

	private void writeGDMToDBInternalWithContentSchema(final String dataResourceFileName, final String resourceGraphURI,
			final ObjectNode contentSchemaRequestJSON) throws IOException {

		LOG.debug("start writing GDM statements for GDM resource at " + dbType + " DB");

		final URL fileURL = Resources.getResource(dataResourceFileName);
		final byte[] file = Resources.toByteArray(fileURL);

		final String requestJsonString = objectMapper.writeValueAsString(contentSchemaRequestJSON);

		// Construct a MultiPart with two body parts
		final MultiPart multiPart = new MultiPart();
		multiPart.bodyPart(new BodyPart(file, MediaType.APPLICATION_OCTET_STREAM_TYPE))
				.bodyPart(new BodyPart(resourceGraphURI, MediaType.TEXT_PLAIN_TYPE))
				.bodyPart(new BodyPart(requestJsonString, MediaType.APPLICATION_JSON_TYPE));

		// POST the request
		final ClientResponse response = target().path("/put").type("multipart/mixed").post(ClientResponse.class, multiPart);

		Assert.assertEquals("expected 200", 200, response.getStatus());

		multiPart.close();

		LOG.debug("finished writing GDM statements for GDM resource at " + dbType + " DB");
	}

	private void writeGDMToDBInternal(final String dataResourceFileName, final String resourceGraphURI) throws IOException {

		LOG.debug("start writing GDM statements for GDM resource at " + dbType + " DB");

		final URL fileURL = Resources.getResource(dataResourceFileName);
		final byte[] file = Resources.toByteArray(fileURL);

		// Construct a MultiPart with two body parts
		final MultiPart multiPart = new MultiPart();
		multiPart.bodyPart(new BodyPart(file, MediaType.APPLICATION_OCTET_STREAM_TYPE)).bodyPart(
				new BodyPart(resourceGraphURI, MediaType.TEXT_PLAIN_TYPE));

		// POST the request
		final ClientResponse response = target().path("/put").type("multipart/mixed").post(ClientResponse.class, multiPart);

		Assert.assertEquals("expected 200", 200, response.getStatus());

		multiPart.close();

		LOG.debug("finished writing GDM statements for GDM resource at " + dbType + " DB");
	}

	private ObjectNode getMABXMLContentSchema() {

		final ObjectNode requestJson = objectMapper.createObjectNode();
		requestJson.put("record_identifier_attribute_path", "http://www.ddb.de/professionell/mabxml/mabxml-1.xsd#id");
		final ArrayNode keyAttributePaths = objectMapper.createArrayNode();
		keyAttributePaths.add("http://www.ddb.de/professionell/mabxml/mabxml-1.xsd#feld\u001Ehttp://www.ddb.de/professionell/mabxml/mabxml-1.xsd#nr");
		keyAttributePaths
				.add("http://www.ddb.de/professionell/mabxml/mabxml-1.xsd#feld\u001Ehttp://www.ddb.de/professionell/mabxml/mabxml-1.xsd#ind");
		requestJson.put("key_attribute_paths", keyAttributePaths);
		requestJson.put("value_attribute_path",
				"http://www.ddb.de/professionell/mabxml/mabxml-1.xsd#feld\u001Ehttp://www.w3.org/1999/02/22-rdf-syntax-ns#value");

		return requestJson;
	}
}
