package plc2skill.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.toList;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import be.ugent.rml.store.Quad;
import be.ugent.rml.store.QuadStore;
import be.ugent.rml.term.Literal;
import be.ugent.rml.term.NamedNode;
import be.ugent.rml.term.Term;
import jena.rdfcat;
import plc2skill.opcua.NodeIdResolvingException;
import plc2skill.opcua.OpcUaBrowser;

public class Plc2SkillMapper {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private QuadStore rmlMappingResult;
	static String stateMachineTemplateFile = "PLCStateMachine.ttl";
	static String pattern = "_Replace_";

	/**
	 * Maps an MTP file with a given file path to the ontological skill model
	 * 
	 * @param mtpFilePath File path of the MTP AML file
	 * @return Skill ontology in turtle syntax
	 */
	public String executeMapping(String plcOpenFilePath, String endpointUrl, String nodeIdRoot) {

		// 1. Execute RML mapping
		RmlMapper rmlMapper = new RmlMapper();
		this.rmlMappingResult = rmlMapper.executeRmlMapping(plcOpenFilePath);

		// 2. Add PLC Identifier and EndpointURL of OPC UA Server
		String completedRmlMappingResult = completeMappingResult(endpointUrl, nodeIdRoot, rmlMappingResult);

		// 3. Create state machines for each procedure
		String stateMachines = createStateMachines(plcOpenFilePath);

		// 4. Combine completed rml mapping result with state machines
		String mappingResult = completedRmlMappingResult + "\n" + stateMachines;
		return mappingResult;
	}

	/**
	 * Replaces the ServerIP and PLCIdentifier
	 */
	private String completeMappingResult(String endpointUrl, String nodeIdRoot, QuadStore rmlMappingResult) {
		String result = "";

		// If a nodeIdRoot is given by the user, use it
		if (nodeIdRoot != null) {
			String stringMappingResult = RmlMapper.convertResultToString(rmlMappingResult);
			String placeholderIdentifier = "PLCIdentifier";
			String placeholderEndpointURL = "ServerIP";
			result = stringMappingResult.replaceAll(placeholderIdentifier, nodeIdRoot); // Replaces nodeID-Placeholder
			result = result.replaceAll(placeholderEndpointURL, endpointUrl);
		} else {
			// Try to browse all variables to resolve the proper nodeID
			Term predicate = new NamedNode("http://www.hsu-ifa.de/ontologies/OpcUa#nodeId");

			List<Quad> sourceQuads = rmlMappingResult.getQuads(null, predicate, null);
			OpcUaClient client = null;
			try {
				client = OpcUaClient.create(endpointUrl);
				client.connect().get();
			} catch (UaException | InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			OpcUaBrowser browser = null;
			try {
				browser = new OpcUaBrowser(endpointUrl);
				for (Quad quad : sourceQuads) {
					String nodeIdWithPlaceholder = quad.getObject().getValue();
					try {
						NodeId nodeId = browser.findNodeId(nodeIdWithPlaceholder);
						Term nodeIdTerm = new Literal(nodeId.getIdentifier().toString());
						this.rmlMappingResult.addQuad(quad.getSubject(), quad.getPredicate(), nodeIdTerm);
						this.rmlMappingResult.removeQuads(quad);
					} catch (NodeIdResolvingException e) {
						logger.warn("A connection to the server was made but an incomplete nodeId could not be resolved.\n"
								+ "The mapping result will contain incomplete nodeIds with unreplaced template strings.");
						}
					
					}
			} catch (UaException | InterruptedException | ExecutionException e) {
				logger.warn("Error while making a connection to the OPC UA server. Please check your endpointUrl and make sure the server is running.\n"
						+ "No nodeIdRoot was provided and a connection to the OPC UA could not be made. The mapping result will contain incomplete nodeIds with unreplaced template strings.");
				e.printStackTrace();
			}
			
			result = RmlMapper.convertResultToString(rmlMappingResult);
		}
		return result;
	}
	
	
	/**
	 * Creates a state machine for every service procedure of the given MTP. Note that in our skill model, every Skill (=MTP procedure) has to have its
	 * own state machine
	 * 
	 * @param mtpFilePath mtpFilePath File path of the MTP AML file
	 * @return A string in turtle syntax containing all state machines for this MTP
	 */
	private String createStateMachines(String plcOpenFilePath) {

		DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		domFactory.setNamespaceAware(false);
		DocumentBuilder builder;
		Document doc = null;

		// Open the file
		try {
			builder = domFactory.newDocumentBuilder();
			doc = builder.parse(plcOpenFilePath);
		} catch (ParserConfigurationException | SAXException | IOException e) {
			e.printStackTrace();
		}

		XPath xpath = XPathFactory.newInstance().newXPath();
		XPathExpression expr;
		Object result = null;

		// Evaluate an XPath expression to get all ServiceProcedure / ServiceStrategy
		try {
			expr = xpath.compile(
					"./project/instances/configurations/configuration/resource/addData/data/pou/interface/localVars/variable[./type/derived/@name=./ancestor::addData/data/pou[./interface/addData/data/Inheritance/Extends='TJ.Skill']/@name]");

			result = expr.evaluate(doc, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			System.out.println("Error while executing XPath to get all ServiceProcedures");
			e.printStackTrace();
		}
		System.out.println(result);
		NodeList procedureNodes = (NodeList) result;
		procedureNodes.getLength();
		// Open the state machine template file
		InputStream stateMachineTemplateStream = this.getClass().getClassLoader().getResourceAsStream(stateMachineTemplateFile);
		BufferedReader br = new BufferedReader(new InputStreamReader(stateMachineTemplateStream));
		String stateMachineTemplate = br.lines().collect(Collectors.joining("\n"));

		// For every procedureNode: Create a customized state machine by replacing the placeholder and add it to the total stateMachin string
		String mappedStateMachines = "";
		for (int i = 0; i < procedureNodes.getLength(); i++) {
			String nodeName = procedureNodes.item(i).getAttributes().getNamedItem("name").getNodeValue();
			System.out.println("This is a nodeName:" + nodeName);
			String stateMachine = stateMachineTemplate.replaceAll(pattern, nodeName);
			mappedStateMachines += stateMachine;
		}

		try {
			br.close();
		} catch (IOException e) {
			System.out.println("Error while closing the file");
			e.printStackTrace();
		}

		return mappedStateMachines;
	}

}