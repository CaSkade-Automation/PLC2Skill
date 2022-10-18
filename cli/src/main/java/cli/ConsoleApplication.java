package cli;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import plc2skill.mapping.Plc2SkillMapper;

public class ConsoleApplication {
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	String outputFilename = "MappingOutput.ttl";

	public void run(String[] args) {
		CommandLine line = parseArguments(args);
		
		if(line.hasOption("help")) {
			printHelp();
			return;
		}
		
		// fileName and endpointUrl are required, rest is optional		
		if (line.hasOption("filename") && line.hasOption("endpointUrl")) {
			logger.info("Started PLC-Code Mapping to Skills");
			Path plcOpenPath = Path.of(line.getOptionValue("filename"));
			String endpointUrl = line.getOptionValue("endpointUrl");
			String user = line.getOptionValue("user");
			String password = line.getOptionValue("password");
			String nodeIdRoot = line.getOptionValue("nodeIdRoot");
			logger.info("fileName: " + plcOpenPath + "\nendpointUrl: " + endpointUrl + "\nnodeIdRoot: " + nodeIdRoot);
			Plc2SkillMapper mapper = new Plc2SkillMapper.Builder(plcOpenPath, endpointUrl)
													.setUser(user, password)
													.setNodeIdRoot(nodeIdRoot)
													.build();
			String result = mapper.executeMapping();
			writeFile(result, outputFilename);
		} else {
			logger.error("Missing one or both mandatory parameters -f and -e...");
			printHelp();
		}
	}

	private CommandLine parseArguments(String[] args) {
		Options options = getOptions();
		CommandLine line = null;

		CommandLineParser parser = new DefaultParser();

		try {
			line = parser.parse(options, args);

		} catch (ParseException ex) {
			logger.error("Failed to parse command line arguments");
			logger.error(ex.toString());
			System.exit(1);
		}
		return line;
	}

	private Options getOptions() {
		Options options = new Options();
		options.addOption("h", "help", false, "Print help");
		options.addRequiredOption("f", "filename", true, "File name of the PLCopen XML file that should be mapped");
		options.addRequiredOption("e", "endpointUrl", true, "EndpointUrl of this skills OPC UA Server");
		options.addOption("u", "user", true, "Username of the OPC UA Server");
		options.addOption("p", "password", true, "Password of the OPC UA Server");
		options.addOption("n", "nodeIdRoot", true, "Root component of this OPC UA Server's node IDs");

		return options;
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

	/**
	 * Prints application help with all possible arguments
	 */
	private void printHelp() {

		Options options = getOptions();

		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("java -jar PLC2Skill-cli.jar", options, true);
	}

}
