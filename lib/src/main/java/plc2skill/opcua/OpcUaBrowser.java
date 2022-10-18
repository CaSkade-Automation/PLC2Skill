package plc2skill.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.toList;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpcUaBrowser {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private OpcUaClient client;
	private NamespaceTable nsTable;
	private TreeNode<ReferenceDescription> referenceTree;

	public OpcUaBrowser(String endpointUrl, String user, String password) throws Exception {
		// Create a client with the given credentials, connect and create a namespace table (later needed for resolving nodeIds)
		this.client = OpcUaClientCreator.createClient(endpointUrl, user, password);
		this.client.connect().get();
		this.nsTable = this.client.getNamespaceTable();
		
		// Create the tree-structure of all OPC UA nodes of the server starting with the ObjectsFolder
		this.referenceTree = this.browseAllNodes(Identifiers.ObjectsFolder, null);
	}

	/**
	 * Browse all nodes (recursively) to create the tree structure
	 * @param browseRoot
	 * @param parent
	 * @return
	 */
	public TreeNode<ReferenceDescription> browseAllNodes(NodeId browseRoot, TreeNode<ReferenceDescription> parent) {
		BrowseDescription browse = new BrowseDescription(
				browseRoot, 
				BrowseDirection.Forward, 
				Identifiers.References, 
				true,
				uint(NodeClass.Object.getValue() | NodeClass.Variable.getValue()), 
				uint(BrowseResultMask.All.getValue())
		);

		// On first recursion: Setup empty parent container
		if (parent == null) {
			parent = new TreeNode<ReferenceDescription>(null);
		}
		try {
			// Get all children of current element
			BrowseResult browseResult = this.client.browse(browse).get();
			parent.addChildrenData(toList(browseResult.getReferences()));
			List<TreeNode<ReferenceDescription>> refTreeChildren = parent.getChildren();
			
			// For all found children: Do recursion to browse their children
			for (TreeNode<ReferenceDescription> refNode : refTreeChildren) {

				ReferenceDescription ref = refNode.getData();

				ref.getNodeId().toNodeId(this.nsTable).ifPresent(nodeId -> {
					browseAllNodes(nodeId, refNode);
				});

			}
		} catch (InterruptedException | ExecutionException e) {
			logger.error("Browsing nodeId={} failed: {}", e.getMessage(), e);
		}
		return parent;
	}
	

	/**
	 * Finds the complete nodeId for a given partial nodeId that contains the browseName
	 * @param nodeIdWithPlaceholder Partial nodeId containing a placeholder string
	 * @return
	 * @throws NodeIdResolvingException
	 */
	public NodeId findNodeId(String nodeIdWithPlaceholder) throws NodeIdResolvingException {
		// PlcOpenXml uses POU names and separates them with a "."
		String[] nodeIdElements = nodeIdWithPlaceholder.split("\\.");
		// BIG Assumption: localVarName (last array entry) is used as browseName - this is valid for Codesys, but also for other tools?
		String localVarName = nodeIdElements[nodeIdElements.length - 1];

		// Convert the tree to a flat list to better filter it
		List<TreeNode<ReferenceDescription>> flatNodeList = this.referenceTree.toFlatList();
		List<TreeNode<ReferenceDescription>> matchingRefs = flatNodeList.stream()
				.filter(isMatchingRef(localVarName))
				.collect(Collectors.toList());

		if (matchingRefs.size() == 0)
			throw new NodeIdResolvingException(nodeIdWithPlaceholder);

		ExpandedNodeId exNodeId = this.findCorrectMatchingRef(nodeIdElements, matchingRefs);

		NodeId nodeId = exNodeId.toNodeId(this.nsTable).orElseThrow(() -> new NodeIdResolvingException(localVarName));

		return nodeId;
	}
	
	/**
	 * Wrapper function for a predicate to check whether a reference's browseName matches a given local variable name 
	 * @param localVarName local variable name (inside a POU)
	 * @return
	 */
	private Predicate<TreeNode<ReferenceDescription>> isMatchingRef(String localVarName) {
		return ref -> ((!ref.isRoot()) && (ref.getData().getBrowseName().getName().equals(localVarName)));
	}
	
	
	/**
	 * By comparing only the browseNames, multiple matches may be found. Thus, we need to compare their parents (representing the POU) to get the "real" match
	 * @param nodeIdElements String array containing a partial nodeId that consists of a template string, the application structure (with the POUs) and the local var name
	 * @param matchingRefs A list of matching reference descriptions after comparing only their local variable name
	 * @return
	 * @throws NodeIdResolvingException
	 */
	private ExpandedNodeId findCorrectMatchingRef(String[] nodeIdElements, List<TreeNode<ReferenceDescription>> matchingRefs) throws NodeIdResolvingException {
		// If there is only one, we can simply return this one's nodeId
		if (matchingRefs.size() == 1) {
			return matchingRefs.get(0).getData().getNodeId();
		}
		// If there are more, we need to compare their parents
		String localVarNameParent = nodeIdElements[nodeIdElements.length - 2];

		List<TreeNode<ReferenceDescription>> correctMatches = matchingRefs.stream().filter(matchingRef -> {
			String parentBrowseName = matchingRef.getParent().getData().getBrowseName().getName();
			return (parentBrowseName.equals(localVarNameParent));
		}).collect(Collectors.toList());

		if (correctMatches.size() == 0) {
			String unresolvedNodeId = String.join(".", nodeIdElements);
			throw new NodeIdResolvingException("No matches for the unresolved nodeId " + unresolvedNodeId + ". Please resolve this nodeId manually");
		}

		// Browsing can return multiple referenceDescriptions to one node. In the end, we need to return one nodeId -> correctMatches need to be made unique
		Set<ExpandedNodeId> correctMatchNodeIds = correctMatches.stream().map(correctMatch -> correctMatch.getData().getNodeId()).collect(Collectors.toSet());

		// There should be exactly one correct matches. If there are more or none, we can only throw an error
		if (correctMatchNodeIds.size() > 1) {
			String unresolvedNodeId = String.join(".", nodeIdElements);
			throw new NodeIdResolvingException(
					"There are '" + correctMatchNodeIds.size() + "' matches for the unresolved nodeId " + unresolvedNodeId + ". Please resolve this nodeId manually");
		}

		return correctMatchNodeIds.iterator().next();
	}

}
