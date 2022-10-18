package plc2skill.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import be.ugent.rml.store.Quad;
import be.ugent.rml.store.QuadStore;
import be.ugent.rml.term.Literal;
import be.ugent.rml.term.NamedNode;
import be.ugent.rml.term.Term;
import plc2skill.opcua.NodeIdResolvingException;
import plc2skill.opcua.OpcUaBrowser;

public class Plc2SkillMapper {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private QuadStore rmlMappingResult;
	static String stateMachineTemplateFile = "PLCStateMachine.ttl";
	static String pattern = "_Replace_";

	private Path plcOpenFilePath;
	private String endpointUrl;
	private String user;
	private String password;
	private String nodeIdRoot;
	
	private Plc2SkillMapper(Path plcOpenFilePath, String endpointUrl) {
		this.plcOpenFilePath = plcOpenFilePath;
		this.endpointUrl = endpointUrl;
	}
	
	public static class Builder {
		
		Plc2SkillMapper mapper;
		
		public Builder(Path plcOpenFilePath, String endpointUrl){
			mapper = new Plc2SkillMapper(plcOpenFilePath.toAbsolutePath(), endpointUrl);
		}
		
		public Builder setUser(String user, String password) {
			mapper.user = user;
			mapper.password = password;
			return this;
		}
		
		public Builder setNodeIdRoot(String nodeIdRoot) {
			mapper.nodeIdRoot = nodeIdRoot;
			return this;
		}
		
		public Plc2SkillMapper build() {
			return this.mapper;
		}
	}
	
	
	/**
	 * Maps an MTP file with a given file path to the ontological skill model
	 * 
	 * @param mtpFilePath File path of the MTP AML file
	 * @return Skill ontology in turtle syntax
	 */
	public String executeMapping() {

		// 1. Execute RML mapping
		RmlMapper rmlMapper = new RmlMapper();
		this.rmlMappingResult = rmlMapper.executeRmlMapping(this.plcOpenFilePath);

		// 2. Add PLC Identifier and EndpointURL of OPC UA Server
		String completedRmlMappingResult = completeMappingResult();

		// 3. Create state machines for each procedure
		String stateMachines = createStateMachines(this.plcOpenFilePath);

		// 4. Combine completed rml mapping result with state machines
		String mappingResult = completedRmlMappingResult + "\n" + stateMachines;
		logger.info("Completed mapping");
		
		return mappingResult;
	}

	/**
	 * Replaces the ServerIP and PLCIdentifier
	 */
	private String completeMappingResult() {
		String resultWithIpPlaceholder = "";

		// If a nodeIdRoot is given by the user, use it
		if (nodeIdRoot != null) {
			String stringMappingResult = RmlMapper.convertResultToString(this.rmlMappingResult);
			String placeholderIdentifier = "PLCIdentifier";
			resultWithIpPlaceholder = stringMappingResult.replaceAll(placeholderIdentifier, this.nodeIdRoot); // Replaces nodeID-Placeholder
			
		} else {
			// Try to browse all variables to resolve the proper nodeID
			Term predicate = new NamedNode("http://www.hsu-ifa.de/ontologies/OpcUa#nodeId");

			List<Quad> sourceQuads = rmlMappingResult.getQuads(null, predicate, null);
			try {
				OpcUaBrowser browser = new OpcUaBrowser(this.endpointUrl, this.user, this.password);
				for (Quad quad : sourceQuads) {
					String nodeIdWithPlaceholder = quad.getObject().getValue();
					try {
						NodeId nodeId = browser.findNodeId(nodeIdWithPlaceholder);
						Term nodeIdTerm = new Literal(nodeId.getIdentifier().toString());
						this.rmlMappingResult.addQuad(quad.getSubject(), quad.getPredicate(), nodeIdTerm);
						this.rmlMappingResult.removeQuads(quad);
					} catch (NodeIdResolvingException e) {
						logger.error("A connection to the server was made but an incomplete nodeId could not be resolved.\n"
								+ "The mapping result will contain incomplete nodeIds with unreplaced template strings.");
						}
					
					}
			} catch (Exception e) {
				logger.error("Error while making a connection to the OPC UA server. Please check your endpointUrl and make sure the server is running.\n"
						+ "No nodeIdRoot was provided and a connection to the OPC UA could not be made. The mapping result will contain incomplete nodeIds with unreplaced template strings.");
				e.printStackTrace();
			}
			
			resultWithIpPlaceholder = RmlMapper.convertResultToString(rmlMappingResult);
		}
		
		String placeholderEndpointURL = "ServerIP";
		String result = resultWithIpPlaceholder.replaceAll(placeholderEndpointURL, this.endpointUrl);
		return result;
	}
	
	
	/**
	 * Creates a state machine for every service procedure of the given MTP. Note that in our skill model, every Skill (=MTP procedure) has to have its
	 * own state machine
	 * 
	 * @param mtpFilePath mtpFilePath File path of the MTP AML file
	 * @return A string in turtle syntax containing all state machines for this MTP
	 */
	private String createStateMachines(Path plcOpenFilePath) {

		DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		domFactory.setNamespaceAware(false);
		DocumentBuilder builder;
		Document doc = null;

		// Open the file
		try {
			builder = domFactory.newDocumentBuilder();
			doc = builder.parse(plcOpenFilePath.toUri().toString());
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
			logger.error("Error while executing XPath to get all ServiceProcedures");
			e.printStackTrace();
		}
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
			String stateMachine = stateMachineTemplate.replaceAll(pattern, nodeName);
			mappedStateMachines += stateMachine;
		}

		try {
			br.close();
		} catch (IOException e) {
			logger.error("Error while closing the file");
			e.printStackTrace();
		}

		return mappedStateMachines;
	}

}