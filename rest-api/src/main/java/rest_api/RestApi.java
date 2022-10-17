package rest_api;

import static spark.Spark.*;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import plc2skill.mapping.Plc2SkillMapper;

public class RestApi {

	private final static Logger logger = LoggerFactory.getLogger(RestApi.class);
	static Plc2SkillMapper mapping = new Plc2SkillMapper();

	public static void main(String[] args) {

		port(9191);								// Set port of this webservice
		logger.info("Running MTP-Mapping-Service at localhost:9191");
		
		File uploadDir = new File("upload");	
		uploadDir.mkdir();						// Create new folder if it doesn't exist and add it to static files folder
		staticFiles.externalLocation("upload");
		
		
		// Setup a post route at base path
		post("/", (request, response) -> {
			request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));
			
			String endpointUrl = request.raw().getParameter("endpointUrl");
			String nodeIdRoot = request.raw().getParameter("nodeIdRoot");
			Part uploadedFileObject = request.raw().getPart("plc-file");
			
			File file = new File(uploadDir.getAbsolutePath() + File.separator + getFileName(uploadedFileObject));
			file.deleteOnExit();
			

			// make sure to send the file as a multipart/form-data with key "mtp-file"
			try (InputStream input = request.raw().getPart("plc-file").getInputStream()) {
				Files.copy(input, file.toPath() , StandardCopyOption.REPLACE_EXISTING);
			}

			System.out.println("Uploaded file '" + getFileName(request.raw().getPart("plc-file")) + "' saved as '"
					+ file + "'");
			
//			Thread thread = new Thread() {
//				public void run() {
					String mappingResult = mapping.executeMapping(endpointUrl, nodeIdRoot, file.toString());
//					System.out.println("Mapping done");
//				}
//			};
//			thread.start();

			return mappingResult;
		});
	}


	/**
	 * Returns the file name of one part (i.e. one file) of a multipart file-upload
	 * @param part
	 * @return
	 */
	private static String getFileName(Part part) {
		for (String cd : part.getHeader("content-disposition").split(";")) {
			if (cd.trim().startsWith("filename")) {
				return cd.substring(cd.indexOf('=') + 1).trim().replace("\"", "");
			}
		}
		return null;
	}

}
