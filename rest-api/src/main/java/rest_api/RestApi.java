package rest_api;

import static spark.Spark.*;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;

import mapping.MtpToSkillMapper;

public class RestApi {

	static MtpToSkillMapper mapping = new MtpToSkillMapper();

	public static void main(String[] args) {

		port(9191);								// Set port of this webservice
		System.out.println("Running MTP-Mapping-Service at localhost:9191");
		
		File uploadDir = new File("upload");	
		uploadDir.mkdir();						// Create new folder if it doesn't exist and add it to static files folder
		staticFiles.externalLocation("upload");
		
		
		// Setup a post route at base path
		post("/", (request, response) -> {
			request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));
			
			Part uploadedFileObject = request.raw().getPart("mtp-file");
			
			File file = new File(uploadDir.getAbsolutePath() + File.separator + getFileName(uploadedFileObject));
			file.deleteOnExit();
			

			// make sure to send the file as a multipart/form-data with key "mtp-file"
			try (InputStream input = request.raw().getPart("mtp-file").getInputStream()) {
				Files.copy(input, file.toPath() , StandardCopyOption.REPLACE_EXISTING);
			}

			System.out.println("Uploaded file '" + getFileName(request.raw().getPart("mtp-file")) + "' saved as '"
					+ file + "'");
			
//			Thread thread = new Thread() {
//				public void run() {
					String mappingResult = mapping.executeMapping(file.toString());
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
