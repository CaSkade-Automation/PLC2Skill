package cli;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import mapping.MtpToSkillMapper;

public class ConsoleApplication {
	MtpToSkillMapper mapping = new MtpToSkillMapper();
	String outputFilename = "MappingOutput.ttl";
	
	public void run(String[] args) {
		CommandLine line = parseArguments(args);

		if (line.hasOption("filename")) {
			System.out.println("Started PLC-Code Mapping to Skills");
			String path = line.getOptionValue("filename");
			String result = mapping.executeMapping(path);
			writeFile(result, outputFilename);
		} else {
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

			System.err.println("Failed to parse command line arguments");
			System.err.println(ex.toString());

			System.exit(1);
		}
		return line;
	}

	private Options getOptions() {
		Options options = new Options();
		options.addOption("f", "filename", true, "File name of the MTP manifest file that should be mapped");
		
		return options;
	}
	
	/**
	 * Writes the mapped model to a file
	 * @param mappedModel String containing the complete skill model
	 * @param filePath Path to the file that will be created
	 */
	private void writeFile(String mappedModel, String filePath) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
			writer.write(mappedModel);
			writer.close();
		} catch (IOException e) {
			System.out.println("Error while writing the file");
			e.printStackTrace();
		}
	}
	
	/**
     * Prints application help with all possible arguments
     */
    private void printHelp() {

        Options options = getOptions();

        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java -jar mtp-to-skill-cli.jar", options, true);
    }
	
}
