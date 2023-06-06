package plc2skill.opcua;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

public class OpcUaClientCreator {

	MessageSecurityMode messageSecurityMode;
	SecurityPolicy securityPolicy;

	protected OpcUaClient createClient(String endpointUrl, String user, String password) throws Exception {

		EndpointSelector selector = new EndpointSelector(this, user, password);

		OpcUaClient client = OpcUaClient.create(endpointUrl, endpoints -> selector.filterEndpointsAndTrustServer(endpoints), configBuilder -> selector.getConfig());

		return client;
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
