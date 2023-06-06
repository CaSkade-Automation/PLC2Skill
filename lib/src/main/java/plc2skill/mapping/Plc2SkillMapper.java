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
import org.w3c.dom.Node;
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

	private final String placeholderIdentifier = "__NodeIdRootComponent__";
	private final String placeholderEndpointURL = "__ServerEndpointUrl__";

	private QuadStore rmlMappingResult;
	static String stateMachineTemplateFile = "PLCStateMachine.ttl";
	static String pattern = "__SkillNamePlaceholder__";

	private Path plcOpenFilePath;
	private String endpointUrl;
	private String user;
	private String password;
	private String resourceIri = "";
	private String nodeIdRoot;
	private String baseIri = "http://www.hsu-hh.de/aut/ontologies/PLC2Skill";

	private Plc2SkillMapper(Path plcOpenFilePath, String endpointUrl) {
		this.plcOpenFilePath = plcOpenFilePath;
		this.endpointUrl = endpointUrl;
	}

	public static class Builder {

		Plc2SkillMapper mapper;

		public Builder(Path plcOpenFilePath, String endpointUrl) {
			mapper = new Plc2SkillMapper(plcOpenFilePath.toAbsolutePath(), endpointUrl);
		}

		public Builder setUser(String user, String password) {
			mapper.user = user;
			mapper.password = password;
			return this;
		}

		public Builder setBaseIri(String baseIri) {
			// make sure null doesn't overwrite the default value
			if (baseIri == null)
				return this;

			mapper.baseIri = baseIri;
			return this;
		}

		/**
		 * Set a custom module IRI for all the skills to be created
		 * 
		 * @param moduleIri The custom moduleIri that will be used. If none is set, one is determined automatically from the PLC application's name
		 * @return
		 */
		public Builder setResourceIri(String resourceIri) {
			mapper.resourceIri = resourceIri;
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
		logger.info("Started mapping...");

		try {
			// 1. Execute RML mapping
			RmlMapper rmlMapper = new RmlMapper();
			this.rmlMappingResult = rmlMapper.executeRmlMapping(this.plcOpenFilePath, this.baseIri);

			// 2. Fix resource IRIs
			this.fixResourceIri();

			// 2. Add PLC Identifier and EndpointURL of OPC UA Server
			String completedRmlMappingResult = this.fixOpcUaInfo();

			// 3. Create state machines for each procedure
			String stateMachines = createStateMachines();

			// 4. Combine completed rml mapping result with state machines
			String mappingResult = completedRmlMappingResult + "\n" + stateMachines;
			logger.info("Completed mapping");

			return mappingResult;
		} catch (Exception e) {
			e.printStackTrace();
			return e.toString();
		}
	}

	/**
	 * Adds a resource node and the "provides" relations to capabilities and skills. Resource IRI can either be provided explicitly or be determined from
	 * the PLC device name
	 */
	private void fixResourceIri() {
		// Find all capabilities and skills that were already created
		Term typePredicate = new NamedNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
		Term skillClassObject = new NamedNode("http://www.w3id.org/hsu-aut/caskman#PlcSkill");
		List<Quad> skillQuads = rmlMappingResult.getQuads(null, typePredicate, skillClassObject);

		Term capabilityClassObject = new NamedNode("http://www.w3id.org/hsu-aut/cask#ProvidedCapability");
		List<Quad> capabilityQuads = rmlMappingResult.getQuads(null, typePredicate, capabilityClassObject);

		String resourceIri = this.resourceIri;
		if (this.resourceIri == "") {
			// If no moduleIri is given, create one from device name
			String applicationNameRegex = "./project/instances/configurations/configuration/@name";
			Node applicationNode = this.findInPlcOpenFile(applicationNameRegex).item(0); // Assumption: There is only one device
			String resourceName = applicationNode.getNodeValue();
			resourceIri = String.format("%s#%s", this.baseIri, resourceName);
		}

		// Add resource node and relations to skills and capabilities
		Term resourceTerm = new NamedNode(resourceIri);
		this.rmlMappingResult.addQuad(resourceTerm, typePredicate, new NamedNode("http://www.w3id.org/hsu-aut/css#Resource"));

		for (Quad skillQuad : skillQuads) {
			this.rmlMappingResult.addQuad(resourceTerm, new NamedNode("http://www.w3id.org/hsu-aut/css#providesSkill"), skillQuad.getSubject());
		}

		for (Quad capabilityQuad : capabilityQuads) {
			this.rmlMappingResult.addQuad(resourceTerm, new NamedNode("http://www.w3id.org/hsu-aut/css#providesCapability"), capabilityQuad.getSubject());
		}
	}

	private String fixOpcUaInfo() {
		String result = "";
		try {
			OpcUaBrowser browser = new OpcUaBrowser(this.endpointUrl, this.user, this.password);

			// 1. Fix security info
			// Currently, only securityPolicy and messageSecurityMode None are supported
			// this.fixSecurityInfo(browser);
			
			// 2. Add user and password info
			this.addUserInfo();
			
			result = fixNodeIds(browser);
		} catch (Exception e) {
			logger.error("Error while making a connection to the OPC UA server. Please check your endpointUrl and make sure the server is running.");
			logger.error("No nodeIdRoot was provided and a connection to the OPC UA could not be made. The mapping result will contain incomplete nodeIds with unreplaced template strings.");
			e.printStackTrace();
		}

		return result;
	}
	
	private void addUserInfo() {
		if(this.user.isBlank()) return;
		
		// Get the UA server
		Term typePredicate = new NamedNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
		Term serverTypeNode = new NamedNode("http://www.hsu-ifa.de/ontologies/OpcUa#UAServer");
		Quad serverQuad = this.rmlMappingResult.getQuads(null, typePredicate, serverTypeNode).get(0);	// Assumption that there is only one server per PLCopen file
		
		Term userNameProperty = new NamedNode("http://www.hsu-ifa.de/ontologies/OpcUa#requiresUserName");
		Literal userNameLiteral = new Literal(this.user);
		this.rmlMappingResult.addQuad(serverQuad.getSubject(), userNameProperty, userNameLiteral);
		
		Term passwordProperty = new NamedNode("http://www.hsu-ifa.de/ontologies/OpcUa#requiresPassword");
		Literal passwordLiteral = new Literal(this.password);
		this.rmlMappingResult.addQuad(serverQuad.getSubject(), passwordProperty, passwordLiteral);
	}

	private void fixSecurityInfo(OpcUaBrowser browser) {
		// Replace the default info with the actual security information
		String messageSecurityMode = browser.getSecurityMode().toString();
		String securityPolicy = browser.getSecurityPolicy().getUri();
		
		Term msgSecModePredicate = new NamedNode("http://www.hsu-ifa.de/ontologies/OpcUa#hasMessageSecurityMode");
		Term secPolicyPredicate = new NamedNode("http://www.hsu-ifa.de/ontologies/OpcUa#hasSecurityPolicy");
		
		Quad msgSecModeDefaultQuad = this.rmlMappingResult.getQuads(null, msgSecModePredicate, null).get(0);
		this.rmlMappingResult.addQuad(msgSecModeDefaultQuad.getSubject(), msgSecModePredicate, new NamedNode(messageSecurityMode));
		this.rmlMappingResult.removeQuads(msgSecModeDefaultQuad);
		
		Quad secPolicyDefaultQuad = this.rmlMappingResult.getQuads(null, secPolicyPredicate, null).get(0);
		this.rmlMappingResult.addQuad(secPolicyDefaultQuad.getSubject(), secPolicyPredicate, new NamedNode(securityPolicy));
		this.rmlMappingResult.removeQuads(secPolicyDefaultQuad);
	}

	/**
	 * Replaces the ServerIP and PLCIdentifier
	 */
	private String fixNodeIds(OpcUaBrowser browser) {
		String resultWithIpPlaceholder = "";

		// If a nodeIdRoot is given by the user, use it
		if (!nodeIdRoot.isBlank()) {
			String stringMappingResult = RmlMapper.convertResultToString(this.rmlMappingResult);
			resultWithIpPlaceholder = stringMappingResult.replace(placeholderIdentifier, this.nodeIdRoot); // Replaces nodeID-Placeholder

		} else {
			// Try to browse all variables to resolve the proper nodeID
			Term predicate = new NamedNode("http://www.hsu-ifa.de/ontologies/OpcUa#nodeId");

			List<Quad> sourceQuads = rmlMappingResult.getQuads(null, predicate, null);
			for (Quad quad : sourceQuads) {
				String nodeIdWithPlaceholder = quad.getObject().getValue();
				String incompleteNodeIdWithoutPlaceholder = nodeIdWithPlaceholder.replaceFirst("^__NodeIdRootComponent__", "");
				try {
					NodeId nodeId = browser.findNodeId(incompleteNodeIdWithoutPlaceholder);
					Term nodeIdTerm = new Literal(nodeId.toParseableString());
					this.rmlMappingResult.addQuad(quad.getSubject(), quad.getPredicate(), nodeIdTerm);
					this.rmlMappingResult.removeQuads(quad);
				} catch (NodeIdResolvingException e) {
					logger.error("A connection to the server was made but an incomplete nodeId could not be resolved.\n"
							+ "The mapping result will contain incomplete nodeIds with unreplaced template strings.");
				}

			}

			resultWithIpPlaceholder = RmlMapper.convertResultToString(rmlMappingResult);
		}

		String result = resultWithIpPlaceholder.replaceAll(placeholderEndpointURL, this.endpointUrl);
		return result;
	}

	private NodeList findInPlcOpenFile(String regex) {
		DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		domFactory.setNamespaceAware(false);
		DocumentBuilder builder;
		Document doc = null;

		// Open the file
		try {
			builder = domFactory.newDocumentBuilder();
			doc = builder.parse(this.plcOpenFilePath.toUri().toString());
		} catch (ParserConfigurationException | SAXException | IOException e) {
			e.printStackTrace();
		}

		XPath xpath = XPathFactory.newInstance().newXPath();
		XPathExpression expr;
		Object result = null;

		// Evaluate an XPath expression to get all ServiceProcedure / ServiceStrategy
		try {
			expr = xpath.compile(regex);
			result = expr.evaluate(doc, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			logger.error("Error while executing XPath to get all ServiceProcedures");
			e.printStackTrace();
		}

		return (NodeList) result;
	}

	/**
	 * Creates a state machine for every service procedure of the given MTP. Note that in our skill model, every Skill (=MTP procedure) has to have its
	 * own state machine
	 * 
	 * @param mtpFilePath mtpFilePath File path of the MTP AML file
	 * @return A string in turtle syntax containing all state machines for this MTP
	 */
	private String createStateMachines() {
		String skillNameRegex = "./project/instances/configurations/configuration/resource/addData/data/pou/interface/localVars/variable[./type/derived/@name=./ancestor::addData/data/pou[./interface/addData/data/Inheritance/Extends='PLC2Skill.Skill']/@name]";
		NodeList skillNodes = this.findInPlcOpenFile(skillNameRegex);

		// Open the state machine template file
		InputStream stateMachineTemplateStream = this.getClass().getClassLoader().getResourceAsStream(stateMachineTemplateFile);
		BufferedReader br = new BufferedReader(new InputStreamReader(stateMachineTemplateStream));
		String stateMachineTemplate = br.lines().collect(Collectors.joining("\n"));

		// For every procedureNode: Create a customized state machine by replacing the placeholder and add it to the total stateMachin string
		String mappedStateMachines = "";
		for (int i = 0; i < skillNodes.getLength(); i++) {
			String skillName = skillNodes.item(i).getAttributes().getNamedItem("name").getNodeValue();
			String stateMachine = stateMachineTemplate.replaceAll(pattern, skillName);
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