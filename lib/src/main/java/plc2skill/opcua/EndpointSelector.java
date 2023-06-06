package plc2skill.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.client.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

public class EndpointSelector {
	String user = "";
	String password = "";
	OpcUaClientCreator creator;
	OpcUaClientConfigBuilder configBuilder = new OpcUaClientConfigBuilder();

	public EndpointSelector(OpcUaClientCreator creator, String user, String password) {
		this.creator = creator;	// Backreference needed to later set messageMode and securityPolicy
		this.user = user;
		this.password = password;
		this.setBaseConfig();
	}

	private void setBaseConfig() {
		this.configBuilder
			.setApplicationName(LocalizedText.english("PLC2Skill OpcUa Client"))
			.setApplicationUri("urn:plc2skill:client")
			.setRequestTimeout(uint(5000));
	}

	public OpcUaClientConfig getConfig() {
		return this.configBuilder.build();
	}

	public Optional<EndpointDescription> filterEndpointsAndTrustServer(List<EndpointDescription> endpoints) {
		Optional<EndpointDescription> endpointOption = null;

		// If no user and pw is given, there needs to be an endpoint with anonymous token policy
		if (user.isBlank() || password.isBlank()) {
			endpointOption = endpoints.stream()
					.filter(e -> 
						Arrays.asList(e.getUserIdentityTokens()).stream()
							.filter(userTokenPolicy -> userTokenPolicy.getTokenType().equals(UserTokenType.Anonymous)).findFirst().isPresent()).findFirst();
			
		} else {
			
			endpointOption = endpoints.stream()
					.filter(e ->
						Arrays.asList(e.getUserIdentityTokens()).stream()
							.filter(userTokenPolicy -> userTokenPolicy.getTokenType().equals(UserTokenType.UserName)).findFirst().isPresent()).findFirst();
			this.configBuilder.setIdentityProvider(new UsernameProvider(user, password));
//
//			try {
//				// Setup a temporary key store
//				Path securityTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "client", "security");
//				Files.createDirectories(securityTempDir);
//				if (!Files.exists(securityTempDir)) {
//					throw new IOException("unable to create security dir: " + securityTempDir);
//				}
//
//				File pkiDir = securityTempDir.resolve("pki").toFile();
//				KeystoreLoader loader = new KeystoreLoader().load(securityTempDir);
//
//				DefaultTrustListManager trustListManager = new DefaultTrustListManager(pkiDir);
//
//				DefaultClientCertificateValidator certificateValidator = new DefaultClientCertificateValidator(trustListManager);
//
//				endpointOption = endpoints.stream().filter(e -> e.getSecurityPolicyUri().equals(SecurityPolicy.Aes128_Sha256_RsaOaep.getUri())).findFirst();
//				EndpointDescription endpoint = endpointOption.get();
//				this.creator.setMessageSecurityMode(endpoint.getSecurityMode());
//				this.creator.setSecurityPolicy(endpoint.getSecurityPolicyUri());
//				
//				ByteString certBytes = endpoint.getServerCertificate();
//				CertificateFactory certFactory;
//				certFactory = CertificateFactory.getInstance("X.509");
//				InputStream in = new ByteArrayInputStream(certBytes.bytes());
//				X509Certificate cert = (X509Certificate) certFactory.generateCertificate(in);
//				trustListManager.addTrustedCertificate(cert);
//				this.configBuilder
//					.setKeyPair(loader.getClientKeyPair())
//					.setCertificate(loader.getClientCertificate())
//					.setCertificateChain(loader.getClientCertificateChain())
//					.setCertificateValidator(certificateValidator)
//					.setIdentityProvider(new UsernameProvider(user, password));
//			} catch (Exception e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
		}
		
		this.configBuilder.setEndpoint(endpointOption.get());
		this.creator.setMessageSecurityMode(MessageSecurityMode.None);
		this.creator.setSecurityPolicy(SecurityPolicy.None.getUri());
		
		return endpointOption;
	}

}
