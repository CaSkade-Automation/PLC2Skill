package plc2skill.mapping;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import be.ugent.rml.Executor;
import be.ugent.rml.Utils;
import be.ugent.rml.functions.FunctionLoader;
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

	public String executeRmlMapping(String xmlSourceDocument) {

		// mapping file that needs to be executed
		String userDirectory = System.getProperty("user.dir");
		File mappingFile = new File(userDirectory + "\\" + mappingDefinition);
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

			Term newObject = new Literal(xmlSourceDocument);
			for (Term subject : subjects) {
				rmlStore.addQuad(subject, predicate, newObject);
			}

			// Set up the basepath for the records factory, i.e., the basepath for the
			// (local file) data sources
			RecordsFactory factory = new RecordsFactory(mappingFile.getParent());

			// Set up the functions used during the mapping
			Map<String, Class> libraryMap = new HashMap<>();

			FunctionLoader functionLoader = new FunctionLoader(null, libraryMap);

			// Set up the outputstore (needed when you want to output something else than
			// nquads)
			QuadStore outputStore = new RDF4JStore();

			// Create the Executor
			Executor executor = new Executor(rmlStore, factory, functionLoader, outputStore,
					Utils.getBaseDirectiveTurtle(mappingStream));

			// Execute the mapping
			QuadStore mappedQuads = executor.execute(null);
						
			// Return the result as a turtle string
			Writer sW = new StringWriter();
			mappedQuads.write(sW, "turtle");
			String result = sW.toString();

			return result;
		} catch (Exception e) {
			System.out.println("An error happend while executing the RML mapping" + e.toString());
			return "";
		}
	}
}
