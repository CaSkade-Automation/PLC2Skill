package plc2skill.mapping;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.ugent.idlab.knows.functions.agent.Agent;
import be.ugent.idlab.knows.functions.agent.AgentFactory;
import be.ugent.rml.Executor;
import be.ugent.rml.Utils;
import be.ugent.rml.records.RecordsFactory;
import be.ugent.rml.store.Quad;
import be.ugent.rml.store.QuadStore;
import be.ugent.rml.store.QuadStoreFactory;
import be.ugent.rml.store.RDF4JStore;
import be.ugent.rml.term.Literal;
import be.ugent.rml.term.NamedNode;
import be.ugent.rml.term.Term;

public class RmlMapper {

	static String mappingDefinition = "PLC2SkillMappingRules.ttl";

	public QuadStore executeRmlMapping(String xmlSourceDocument) {

		// mapping file that needs to be executed
		String userDirectory = System.getProperty("user.dir");
		File mappingFile = new File(userDirectory + "\\" + mappingDefinition);
		Path fullXmlSourcePath = Paths.get(userDirectory, xmlSourceDocument);
		Files.exists(fullXmlSourcePath);
		try {

			URL resource = this.getClass().getClassLoader().getResource(mappingDefinition);
			InputStream mappingStream = resource.openStream();

			// Load the mapping in a QuadStore
			QuadStore rmlStore = QuadStoreFactory.read(mappingStream);

			//Get all the triples which contain the rml:source and placeholder as predicate and object, respectively
			Term predicate = new NamedNode("http://semweb.mmlab.be/ns/rml#source");
			Term object = new Literal("${XMLFileToMap}");

			List<Quad> sourceQuads = rmlStore.getQuads(null, predicate, object);
			List<Term> subjects = new ArrayList<Term>();
			for (Quad quad : sourceQuads) {
				subjects.add(quad.getSubject());
			}

			rmlStore.removeQuads(null, predicate, object);

			Term newObject = new Literal(fullXmlSourcePath.toString());
			for (Term subject : subjects) {
				rmlStore.addQuad(subject, predicate, newObject);
			}

			// Set up the basepath for the records factory, i.e., the basepath for the
			// (local file) data sources
			RecordsFactory factory = new RecordsFactory(mappingFile.getParent());

			// Set up the functions used during the mapping
			Map<String, String> libraryMap = new HashMap<>();

			Agent functionAgent = AgentFactory.createFromFnO(libraryMap);
			
			// Set up the outputstore (needed when you want to output something else than
			// nquads)
			QuadStore outputStore = new RDF4JStore();

			// Create the Executor
			Executor executor = new Executor(rmlStore, factory, outputStore,Utils.getBaseDirectiveTurtle(mappingStream), functionAgent);

			// Execute the mapping
			QuadStore mappedQuads = executor.execute(null).get(new NamedNode("rmlmapper://default.store"));
						
			// Return the quads
			return mappedQuads;
		} catch (Exception e) {
			System.out.println("An error happend while executing the RML mapping" + e.toString());
			return new RDF4JStore();
		}
	}
	
	public static String convertResultToString(QuadStore resultQuads) {
		Writer sW = new StringWriter();
		try {
			resultQuads.write(sW, "turtle");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String resultString = sW.toString();
		return resultString;
	}
}
