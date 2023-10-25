package cli;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import plc2skill.mapping.Plc2SkillMapper;

@Command(name = "PLC2Skill CLI", mixinStandardHelpOptions = true)
public class Plc2SkillCli implements Runnable {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	String outputFilename = "MappingOutput.ttl";

	@Option(names = { "-f", "--filename" }, description = "File name of the PLCopen XML file that should be mapped")
	String fileName = "";

	@Option(names = { "-e", "--endpointUrl" }, description = "EndpointUrl of this skills OPC UA Server")
	String endpointUrl = "";

	@Option(names = { "-u", "--user" }, description = "Username of the OPC UA Server")
	String user = "";

	@Option(names = { "-pw", "--password" }, description = "Password of the OPC UA Server")
	String password = "";

	@Option(names = { "-rI", "--resourceIri" }, description = "Explicit resourceIri definition. Optional, but will be guessed rather poorly if none is given.")
	String resourceIri = "";
	
	@Option(names = { "-bI", "--baseIri" }, description = "Explicit baseIri definition for all individuals that are created. Optional, a default one is used if none is given")
	String baseIri = "";
	
	@Option(names = { "-n", "--nodeIdRoot" }, description = "Root component of this OPC UA Server's node IDs")
	String nodeIdRoot = "";

	@Override
	public void run() {
		// fileName and endpointUrl are required, rest is optional
		if (fileName.isBlank() || endpointUrl.isBlank()) {
			logger.error("Missing one or both mandatory parameters -f and -e...");
			return;
		}
		
		logger.info("Started PLC-Code Mapping to Skills");
		Path plcOpenPath = Path.of(fileName);
		logger.info("fileName: " + plcOpenPath + "\nendpointUrl: " + endpointUrl + "\nnodeIdRoot: " + nodeIdRoot);
		Plc2SkillMapper mapper = new Plc2SkillMapper.Builder(plcOpenPath, endpointUrl).setUser(user, password).setNodeIdRoot(nodeIdRoot).setResourceIri(resourceIri).setBaseIri(baseIri).build();
		String result = mapper.executeMapping();
		writeFile(result, outputFilename);
		logger.info("Completed PLC-Code Mapping to Skills");
	}

	public static void main(String[] args) {
		int exitCode = new CommandLine(new Plc2SkillCli()).execute(args);
		System.exit(exitCode);
	}

	/**
	 * Writes the mapped model to a file
	 * 
	 * @param mappedModel String containing the complete skill model
	 * @param filePath    Path to the file that will be created
	 */
	private void writeFile(String mappedModel, String filePath) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
			writer.write(mappedModel);
			writer.close();
		} catch (IOException e) {
			logger.error("Error while writing the file");
			e.printStackTrace();
		}
	}

}
