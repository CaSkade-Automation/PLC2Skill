package plc2skill.opcua;

public class NodeIdResolvingException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	NodeIdResolvingException(String unresolvedId) {
		super("The incomplete nodeId " + unresolvedId + " could not be resolved and will contain unreplaced template strings.");
	}

}
