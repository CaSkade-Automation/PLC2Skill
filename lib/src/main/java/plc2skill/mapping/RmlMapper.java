package plc2skill.mapping;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.ugent.rml.Executor;
import be.ugent.rml.records.RecordsFactory;
import be.ugent.rml.store.Quad;
import be.ugent.rml.store.QuadStore;
import be.ugent.rml.store.QuadStoreFactory;
import be.ugent.rml.store.RDF4JStore;
import be.ugent.rml.term.Literal;
import be.ugent.rml.term.NamedNode;
import be.ugent.rml.term.Term;

public class RmlMapper {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	static String mappingDefinition = "/PLC2SkillMappingRules.ttl";

	/**
	 * Executes the overall mapping consisting of an RML mapping as well as creation of the ISA 88 state machine 
	 * and replacing of placeholder (nodeIDs)
	 * 
	 * @param xmlSourceDocument Absolute path to a source document
	 * @return
	 * @throws Exception 
	 */
	public QuadStore executeRmlMapping(Path xmlSourcePath, String baseIri) throws Exception {

		try {
			InputStream mappingStream = this.getClass().getResourceAsStream(mappingDefinition);

			// Load the mapping in a QuadStore
			QuadStore rmlStore = QuadStoreFactory.read(mappingStream);

			// Get all the triples which contain the rml:source and placeholder as predicate and object, respectively
			Term predicate = new NamedNode("http://semweb.mmlab.be/ns/rml#source");
			Term object = new Literal("${XMLFileToMap}");

			List<Quad> sourceQuads = rmlStore.getQuads(null, predicate, object);
			List<Term> subjects = new ArrayList<Term>();
			for (Quad quad : sourceQuads) {
				subjects.add(quad.getSubject());
			}

			rmlStore.removeQuads(null, predicate, object);

			Term newObject = new Literal(xmlSourcePath.toString());
			for (Term subject : subjects) {
				rmlStore.addQuad(subject, predicate, newObject);
			}

			// Set up the basepath for the records factory, i.e., the basepath for the
			// (local file) data sources
			String parent = xmlSourcePath.getParent().toString();
	        File userDirectory = new File(System.getProperty("user.dir"));
			RecordsFactory factory = new RecordsFactory(parent);

			// Set up the outputstore (needed when you want to output something else than
			// nquads)
			QuadStore outputStore = new RDF4JStore();

			// Create the Executor
			Executor executor = new Executor(rmlStore, factory, outputStore, baseIri, null);
			
			// Execute the mapping
			QuadStore mappedQuads = executor.execute(null).get(new NamedNode("rmlmapper://default.store"));

			// Return the quads
			return mappedQuads;
		} catch (Exception e) {
			logger.error("An error happend while executing the RML mapping: " + e.toString());
			throw new Exception("Error while mapping PLC2Skill mapping rules.\n" + e.toString());
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
