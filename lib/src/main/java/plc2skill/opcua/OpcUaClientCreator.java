package plc2skill.opcua;

import java.util.List;
import java.util.Optional;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

public class OpcUaClientCreator {

	MessageSecurityMode messageSecurityMode;
	SecurityPolicy securityPolicy;
	EndpointSelector selector;
	Optional<EndpointDescription> selectedEndpoint;
	
	protected OpcUaClient createClient(String endpointUrl, String user, String password) throws Exception {
		this.selector = new EndpointSelector(this, user, password);
		OpcUaClient client = OpcUaClient.create(endpointUrl, endpoints -> this.selectSuitableEndpoint(endpoints), configBuilder -> selector.getConfig());

		return client;
	}

	private Optional<EndpointDescription> selectSuitableEndpoint(List<EndpointDescription> endpoints) {
		this.selectedEndpoint = this.selector.filterEndpointsAndTrustServer(endpoints);
		return this.selectedEndpoint;
	}
	
	public EndpointDescription getSelectedEndpoint() {
		return this.selectedEndpoint.get();
	}
	
	public MessageSecurityMode getMessageSecurityMode() {
		return messageSecurityMode;
	}

	public void setMessageSecurityMode(MessageSecurityMode messageSecurityMode) {
		this.messageSecurityMode = messageSecurityMode;
	}

	public SecurityPolicy getSecurityPolicy() {
		return securityPolicy;
	}

	public void setSecurityPolicy(String securityPolicyUri) {
		try {
			this.securityPolicy = SecurityPolicy.fromUri(securityPolicyUri);
		} catch (UaException e) {
			// Error, defaulting to none
			this.securityPolicy = SecurityPolicy.None;
		}
	}

}
