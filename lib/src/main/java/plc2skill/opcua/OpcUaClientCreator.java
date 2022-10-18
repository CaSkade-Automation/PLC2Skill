package plc2skill.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.client.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

public class OpcUaClientCreator {

	static DefaultTrustListManager trustListManager;
	
	protected static OpcUaClient createClient(String endpointUrl, String user, String password) throws Exception {

		// Setup a temporary key store
		Path securityTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "client", "security");
        Files.createDirectories(securityTempDir);
        if (!Files.exists(securityTempDir)) {
            throw new IOException("unable to create security dir: " + securityTempDir);
        }

        File pkiDir = securityTempDir.resolve("pki").toFile();
		KeyStoreLoader loader = new KeyStoreLoader().load(securityTempDir);

		trustListManager = new DefaultTrustListManager(pkiDir);

		DefaultClientCertificateValidator certificateValidator = new DefaultClientCertificateValidator(trustListManager);

		OpcUaClient client = OpcUaClient.create(
				endpointUrl, 
				endpoints -> filterEndpointsAndTrustServer(endpoints),
				configBuilder -> 
					configBuilder
						.setApplicationName(LocalizedText.english("PLC2Skill OpcUa Client"))
						.setApplicationUri("urn:plc2skill:client")
	                    .setKeyPair(loader.getClientKeyPair())
	                    .setCertificate(loader.getClientCertificate())
	                    .setCertificateChain(loader.getClientCertificateChain())
	                    .setCertificateValidator(certificateValidator)
						.setIdentityProvider(getIdentityProvider(user, password))
						.setRequestTimeout(uint(5000))
						.build());

		return client;
	}

	/**
	 * Create an IdentityProvider depending on the username and password provided
	 * @param user
	 * @param password
	 * @return
	 */
	private static IdentityProvider getIdentityProvider(String user, String password) {
		if(user != null) {
			return new UsernameProvider(user, password);
		}
		return new AnonymousProvider();
    }
	
	/**
	 * Filter all endpoints for the one matching the security policy and trust the server certificate
	 * @param endpoints
	 * @return
	 */
	private static Optional<EndpointDescription> filterEndpointsAndTrustServer(List<EndpointDescription>endpoints) {
		Optional<EndpointDescription> endpoint = endpoints.stream()
				.filter(endpointFilter())
				.findFirst();
			
			ByteString certBytes = endpoint.get().getServerCertificate();
			CertificateFactory certFactory;
			try {
				certFactory = CertificateFactory.getInstance("X.509");
				InputStream in = new ByteArrayInputStream(certBytes.bytes());
				X509Certificate cert = (X509Certificate) certFactory.generateCertificate(in);
				trustListManager.addTrustedCertificate(cert);
			} catch (CertificateException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			return endpoint;
	}

	
	/**
	 * Just wraps the endpoint filter predicate into a function
	 * @return
	 */
	private static Predicate<EndpointDescription> endpointFilter() {
		return e -> getSecurityPolicy().getUri().equals(e.getSecurityPolicyUri());
	}

	
	/**
	 * Returns the desired security policy
	 * @return
	 */
	static SecurityPolicy getSecurityPolicy() {
		// Note that this is currently fixed, there may also be an option or logic to find a "good" one
		return SecurityPolicy.Basic256Sha256;
	}
}
