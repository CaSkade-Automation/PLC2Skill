package plc2skill.opcua;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TreeNode<T> implements Iterable<TreeNode<T>> {

	private T data;
	private TreeNode<T> parent;
	private List<TreeNode<T>> children = new ArrayList<TreeNode<T>>();
	private List<TreeNode<T>> elementsIndex = new ArrayList<TreeNode<T>>();	// Flat list to better search for elements
	
	public TreeNode(T data) {
		this.data = data;
		this.elementsIndex.add(this);
	}
	
	private void setParent(TreeNode<T> parent) {
		this.parent = parent;
	}
	
	public void addChild(TreeNode<T> newChildNode) {
		newChildNode.setParent(this);
		this.children.add(newChildNode);
		this.addToIndex(newChildNode);
	}
	
	public void addChildrenData(List<T> newChildNodesData) {
		for (T childData : newChildNodesData) {
			TreeNode<T> newNode = new TreeNode<T>(childData);
			this.addChild(newNode);
		}
	}
	
	public void addChildren(List<TreeNode<T>> newChildNodes) {
		for (TreeNode<T> newChildNode: newChildNodes) {
			this.addChild(newChildNode);
		}
	}
	
	private void addToIndex(TreeNode<T> node) {
		this.elementsIndex.add(node);
		if (!this.isRoot()) {
			parent.addToIndex(node);
		}
	}
	
	public T getData() {
		return this.data;
	}
	
	public TreeNode<T> getParent() {
		return this.parent;
	}
	
	/**
	 * Returns an ancestor of a given depth
	 * @param level Level of ancestry. 0: element, 1: parent, 2: grandparent etc.
	 * @return
	 */
	public TreeNode<T> getAncestor(int level) {
		TreeNode<T> ancestor = this;
		for (int i = 1; i <= level; i++) {
			ancestor = ancestor.parent;
		}
		return ancestor;
	}
	
	public boolean isLeaf() {
		return (this.children.size() == 0);
	}
	
	public boolean isRoot() {
		return (this.parent == null);
	}
	
	public List<TreeNode<T>> getChildren() {
		return this.children;
	}
	
	public List<TreeNode<T>> toFlatList() {
		return this.elementsIndex;
	}
	
	@Override
	public Iterator<TreeNode<T>> iterator() {
		TreeNodeIter<T> iter = new TreeNodeIter<T>(this);
		return iter;
	}
}
