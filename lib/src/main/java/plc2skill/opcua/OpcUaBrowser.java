package plc2skill.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.toList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.UaException;
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
	
	public OpcUaBrowser(String endpointUrl) throws UaException, InterruptedException, ExecutionException {
		this.client = OpcUaClient.create(endpointUrl);
		client.connect().get();
		this.nsTable = this.client.getNamespaceTable();
		this.referenceTree = this.browseAllNodes(Identifiers.ObjectsFolder, null);	
	}
	
	
	public NodeId findNodeId(String nodeIdWithPlaceholder) throws NodeIdResolvingException {
		String[] nodeIdElements = nodeIdWithPlaceholder.split("\\.");
		// BIG Assumption: localVarName is browseName - this is valid for Codesys, but also for others?
		String localVarName = nodeIdElements[nodeIdElements.length - 1];
		
		List<TreeNode<ReferenceDescription>> flatNodeList = this.referenceTree.toFlatList();
		List<TreeNode<ReferenceDescription>> matchingRefs = flatNodeList.stream()
			.filter(ref -> ( (!ref.isRoot()) && (ref.getData().getBrowseName().getName().equals(localVarName) )))
			.collect(Collectors.toList());

		if (matchingRefs.size() == 0) throw new NodeIdResolvingException(nodeIdWithPlaceholder);
		
		ExpandedNodeId exNodeId = this.findCorrectMatchingRef(nodeIdElements, matchingRefs);
		
		NodeId nodeId = exNodeId.toNodeId(this.nsTable).orElseThrow(() -> new NodeIdResolvingException(localVarName));
		
		return nodeId;
	}
	
	
	public TreeNode<ReferenceDescription> browseAllNodes(NodeId browseRoot, TreeNode<ReferenceDescription> parent) {
		BrowseDescription browse = new BrowseDescription(browseRoot, BrowseDirection.Forward, Identifiers.References, true,
				uint(NodeClass.Object.getValue() | NodeClass.Variable.getValue()), uint(BrowseResultMask.All.getValue()));

		if(parent == null) {
			parent = new TreeNode<ReferenceDescription>(null);
		}
		try {
			BrowseResult browseResult = this.client.browse(browse).get();

			parent.addChildrenData(toList(browseResult.getReferences()));
			List<TreeNode<ReferenceDescription>> refTreeChildren = parent.getChildren();
			for (TreeNode<ReferenceDescription> refNode : refTreeChildren) {
				
				ReferenceDescription ref = refNode.getData();

				// recursively browse to children
				ref.getNodeId().toNodeId(this.nsTable).ifPresent(nodeId -> {
					TreeNode<ReferenceDescription> newReferences = browseAllNodes(nodeId, refNode);
				});

			}
		} catch (InterruptedException | ExecutionException e) {
			logger.error("Browsing nodeId={} failed: {}", e.getMessage(), e);
		}
		return parent;
	}
	
	
	
	private ExpandedNodeId findCorrectMatchingRef(String[] nodeIdElements, List<TreeNode<ReferenceDescription>> matchingRefs) throws NodeIdResolvingException {
		// If there is only one, we can simply return this one's nodeId
		if(matchingRefs.size() == 1) {
			return matchingRefs.get(0).getData().getNodeId();
		}
		// If there are more, we need to compare their parents
		String localVarNameParent = nodeIdElements[nodeIdElements.length-2];
		
		List<TreeNode<ReferenceDescription>> flatNodeList = this.referenceTree.toFlatList();
		
		List<TreeNode<ReferenceDescription>> correctMatches = matchingRefs
				.stream()
				.filter(matchingRef -> {
					String parentBrowseName = matchingRef.getParent().getData().getBrowseName().getName();
					return (parentBrowseName.equals(localVarNameParent));
				})
				.collect(Collectors.toList());
		
		if(correctMatches.size() == 0) {
			String unresolvedNodeId = String.join(".", nodeIdElements);
			throw new NodeIdResolvingException("No matches for the unresolved nodeId " + unresolvedNodeId + ". Please resolve this nodeId manually");
		}
		
		// Browsing can return multiple referenceDescriptions to one node. In the end, we need to return one nodeId -> correctMatches need to be made unique
		Set<ExpandedNodeId> correctMatchNodeIds = correctMatches
				.stream()
				.map(correctMatch -> correctMatch.getData().getNodeId())
				.collect(Collectors.toSet());
		
		
		// There should be exactly one correct matches. If there are more or none, we can only throw an error
		if (correctMatchNodeIds.size() > 1) {
			String unresolvedNodeId = String.join(".", nodeIdElements);
			throw new NodeIdResolvingException("There are '" + correctMatchNodeIds.size() + "' matches for the unresolved nodeId " + unresolvedNodeId + ". Please resolve this nodeId manually");
		}
		
		return correctMatchNodeIds.iterator().next();
	}
	
}
