/*
 * Copyright (C) 2013 Tim Vaughan <tgvaughan@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package beast.evolution.tree;

import beast.core.Description;
import beast.core.Input;
import beast.core.StateNode;
import com.google.common.collect.Lists;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("A multi-type phylogenetic tree.")
public class MultiTypeTree extends Tree {

    /*
     * Plugin inputs:
     */
    public Input<String> typeLabelInput = new Input<String>(
            "typeLabel",
            "Label for type traits (e.g. deme)", "deme");
    public Input<Integer> nTypesInput = new Input<Integer>(
            "nTypes",
            "Number of distinct types to consider.", Input.Validate.REQUIRED);

    /*
     * Non-input fields:
     */
    protected String typeLabel;
    protected int nTypes;
    protected MultiTypeNode multiTypeRoot;
    protected MultiTypeNode[] multiTypeNodes;
    protected MultiTypeNode[] storedMultiTypeNodes;
    protected MultiTypeNode storedMultiTypeRoot;

    public MultiTypeTree() { };
    
    public MultiTypeTree(MultiTypeNode rootNode) {
        setRoot(rootNode);
        nodeCount = rootNode.getNodeCount();
        initArrays();
    }

    @Override
    public void initAndValidate() throws Exception {
        typeLabel = typeLabelInput.get();
        nTypes = nTypesInput.get();
    }

    @Override
    protected final void initArrays() {
        // initialise tree-as-array representation + its stored variant
        multiTypeNodes = new MultiTypeNode[nodeCount];
        listNodes(multiTypeRoot, multiTypeNodes);
        storedMultiTypeNodes = new MultiTypeNode[nodeCount];
        MultiTypeNode copy = multiTypeRoot.copy();
        listNodes(copy, storedMultiTypeNodes);

        super.initArrays();
    }

    /**
     * Convert multi-type tree to array representation.
     *
     * @param node Root of sub-tree to convert.
     * @param nodes Array to populate with tree nodes.
     */
    void listNodes(MultiTypeNode node, MultiTypeNode[] nodes) {
        nodes[node.getNr()] = node;
        node.m_tree = this;
        node.multiTypeTree = this;
        if (!node.isLeaf()) {
            listNodes(node.getLeft(), nodes);
            if (node.getRight()!=null)
                listNodes(node.getRight(), nodes);
        }
    }

    @Override
    public MultiTypeNode[] getNodesAsArray() {
        return multiTypeNodes;
    }

    /**
     * Deep copy, returns a completely new multi-type tree.
     *
     * @return a deep copy of this multi-type tree
     */
    @Override
    public MultiTypeTree copy() {
        MultiTypeTree tree = new MultiTypeTree();
        tree.m_sID = m_sID;
        tree.index = index;
        tree.multiTypeRoot = multiTypeRoot.copy();
        tree.root = multiTypeRoot;
        tree.nodeCount = nodeCount;
        tree.internalNodeCount = internalNodeCount;
        tree.leafNodeCount = leafNodeCount;
        tree.nTypes = nTypes;
        tree.typeLabel = typeLabel;
        return tree;
    }

    /**
     * Copy all values from an existing multi-type tree.
     *
     * @param other
     */
    @Override
    public void assignFrom(StateNode other) {
        MultiTypeTree mtTree = (MultiTypeTree) other;
        MultiTypeNode[] mtNodes = new MultiTypeNode[mtTree.getNodeCount()];
        for (int i = 0; i<mtTree.getNodeCount(); i++)
            mtNodes[i] = new MultiTypeNode();
        m_sID = mtTree.m_sID;
        multiTypeRoot = mtNodes[mtTree.multiTypeRoot.getNr()];
        multiTypeRoot.assignFrom(mtNodes, mtTree.multiTypeRoot);
        multiTypeRoot.multiTypeParent = null;
        multiTypeRoot.m_Parent = null;
        root = multiTypeRoot;

        nodeCount = mtTree.nodeCount;
        internalNodeCount = mtTree.internalNodeCount;
        leafNodeCount = mtTree.leafNodeCount;
        initArrays();
    }

    /**
     * Retrieve total number of allowed types on tree.
     *
     * @return total type/deme count.
     */
    public int getNTypes() {
        return nTypes;
    }

    @Override
    public MultiTypeNode getRoot() {
        return multiTypeRoot;
    }

    /**
     * Set new root of multi-type tree.
     *
     * @param root New root node.
     */
    public final void setRoot(MultiTypeNode root) {

        this.multiTypeRoot = root;
        this.root = root;
        nodeCount = this.root.getNodeCount();
        // ensure root is the last node
        if (multiTypeNodes!=null&&root.m_iLabel!=multiTypeNodes.length-1) {
            int rootPos = multiTypeNodes.length-1;
            MultiTypeNode tmp = multiTypeNodes[rootPos];
            multiTypeNodes[rootPos] = root;
            multiTypeNodes[root.m_iLabel] = tmp;
            tmp.m_iLabel = root.m_iLabel;
            multiTypeNodes[rootPos].m_iLabel = rootPos;
        }
    }

    @Override
    public MultiTypeNode getNode(int iNodeNr) {
        return multiTypeNodes[iNodeNr];
    }

    /**
     * @return a list of external (leaf) nodes contained in this tree
     */
    public List<MultiTypeNode> getExternalMultiTypeNodes() {
        ArrayList<MultiTypeNode> externalNodes = new ArrayList<MultiTypeNode>();
        for (int i = 0; i<getNodeCount(); i++) {
            MultiTypeNode node = getNode(i);
            if (node.isLeaf())
                externalNodes.add(node);
        }
        return externalNodes;
    }

    /**
     * @return a list of internal (ancestral) nodes contained in this tree,
     * including the root node
     */
    public List<MultiTypeNode> getInternalMultiTypeNodes() {
        ArrayList<MultiTypeNode> internalNodes = new ArrayList<MultiTypeNode>();
        for (int i = 0; i<getNodeCount(); i++) {
            MultiTypeNode node = getNode(i);
            if (!node.isLeaf())
                internalNodes.add(node);
        }
        return internalNodes;
    }

    @Override
    public int getNodeCount() {
        return multiTypeRoot.getNodeCount();
    }

    /**
     * Generates a new tree in which the colours along the branches are
     * indicated by the traits of single-child nodes.
     *
     * This method is useful for interfacing trees coloured externally using the
     * a ColouredTree instance with methods designed to act on trees coloured
     * using single-child nodes and their metadata fields.
     *
     * Caveat: assumes more than one node exists on tree (i.e. leaf != root)
     *
     * @return Flattened tree.
     */
    public Tree getFlattenedTree() {

        // Create new tree to modify.  Note that copy() doesn't
        // initialise the node array lists, so initArrays() must
        // be called manually.
        Tree flatTree = copy();
        flatTree.initArrays();

        int nextNodeNr = getNodeCount();
        Node colourChangeNode;

        for (MultiTypeNode mtNode : getNodesAsArray()) {

            int nodeNum = mtNode.getNr();

            if (mtNode.isRoot()) {
                flatTree.getNode(nodeNum).setMetaData(typeLabel,
                        mtNode.getLeft().getFinalType());
                continue;
            }

            Node startNode = flatTree.getNode(nodeNum);

            startNode.setMetaData(typeLabel,
                    mtNode.getNodeType());
            startNode.m_sMetaData = String.format("%s=%d",
                    typeLabel, mtNode.getNodeType());


            Node endNode = startNode.getParent();
            endNode.setMetaData(typeLabel,
                    mtNode.getParent().getNodeType());
            endNode.m_sMetaData = String.format("%s=%d",
                    typeLabel, mtNode.getParent().getNodeType());

            Node branchNode = startNode;
            for (int i = 0; i<mtNode.getChangeCount(); i++) {

                // Create and label new node:
                colourChangeNode = new Node();
                colourChangeNode.setNr(nextNodeNr);
                colourChangeNode.setID(String.valueOf(nextNodeNr));
                nextNodeNr++;

                // Connect to child and parent:
                branchNode.setParent(colourChangeNode);
                colourChangeNode.addChild(branchNode);

                // Ensure height and colour trait are set:
                colourChangeNode.setHeight(mtNode.getChangeTime(i));
                colourChangeNode.setMetaData(typeLabel,
                        mtNode.getChangeType(i));
                colourChangeNode.m_sMetaData = String.format("%s=%d",
                        typeLabel, mtNode.getChangeType(i));

                // Update branchNode:
                branchNode = colourChangeNode;
            }

            // Ensure final branchNode is connected to the original parent:
            branchNode.setParent(endNode);
            if (endNode.getLeft()==startNode)
                endNode.setLeft(branchNode);
            else
                endNode.setRight(branchNode);
        }

        return flatTree;
    }

    /**
     * Initialise colours and tree topology from Tree object in which colour
     * changes are marked by single-child nodes and colours are stored in
     * metadata tags.
     *
     * @param flatTree
     */
    public void initFromFlatTree(Tree flatTree) throws Exception {


        // Obtain number of nodes and leaves in tree to construct:
        int nNodes = getTrueNodeCount(flatTree.root);
        int nLeaves = flatTree.root.getLeafNodeCount();

        // Initialise lists of available node numbers:
        List<Integer> leafNrs = new ArrayList<Integer>();
        List<Integer> internalNrs = new ArrayList<Integer>();

        for (int i = 0; i<nLeaves; i++)
            leafNrs.add(i);

        for (int i = 0; i<nNodes; i++)
            if (i<nLeaves)
                leafNrs.add(i);
            else
                internalNrs.add(i);

        // Build new coloured tree:

        List<Node> activeFlatTreeNodes = new ArrayList<Node>();
        List<Node> nextActiveFlatTreeNodes = new ArrayList<Node>();
        List<MultiTypeNode> activeTreeNodes = new ArrayList<MultiTypeNode>();
        List<MultiTypeNode> nextActiveTreeNodes = new ArrayList<MultiTypeNode>();

        // Populate active node lists with root:
        activeFlatTreeNodes.add(flatTree.getRoot());
        MultiTypeNode newRoot = new MultiTypeNode();
        activeTreeNodes.add(newRoot);

        while (!activeFlatTreeNodes.isEmpty()) {

            nextActiveFlatTreeNodes.clear();
            nextActiveTreeNodes.clear();

            for (int idx = 0; idx<activeFlatTreeNodes.size(); idx++) {
                Node flatTreeNode = activeFlatTreeNodes.get(idx);
                MultiTypeNode treeNode = activeTreeNodes.get(idx);

                Node thisFlatNode = flatTreeNode;
                List<Integer> colours = new ArrayList<Integer>();
                List<Double> times = new ArrayList<Double>();

                while (thisFlatNode.getChildCount()==1) {
                    int col = (int) Math.round(
                            (Double) thisFlatNode.getMetaData(typeLabel));
                    colours.add(col);
                    times.add(thisFlatNode.getHeight());

                    thisFlatNode = thisFlatNode.getLeft();
                }

                // Order changes from youngest to oldest:
                colours = Lists.reverse(colours);
                times = Lists.reverse(times);

                switch (thisFlatNode.getChildCount()) {
                    case 0:
                        // Leaf at base of branch
                        treeNode.setNr(leafNrs.get(0));
                        treeNode.setID(thisFlatNode.getID());
                        leafNrs.remove(0);
                        break;

                    case 2:
                        // Non-leaf at base of branch
                        treeNode.setNr(internalNrs.get(0));
                        internalNrs.remove(0);

                        nextActiveFlatTreeNodes.add(thisFlatNode.getLeft());
                        nextActiveFlatTreeNodes.add(thisFlatNode.getRight());

                        MultiTypeNode daughter = new MultiTypeNode();
                        MultiTypeNode son = new MultiTypeNode();
                        treeNode.setLeft(daughter);
                        treeNode.setRight(son);
                        nextActiveTreeNodes.add(daughter);
                        nextActiveTreeNodes.add(son);

                        break;
                }

                // Add type changes to multi-type tree branch:
                for (int i = 0; i<colours.size(); i++)
                    treeNode.addChange(colours.get(i), times.get(i));

                // Set node type at base of multi-type tree branch:
                int nodeType = (int) Math.round(
                        (Double) thisFlatNode.getMetaData(typeLabel));
                treeNode.setNodeType(nodeType);

                // Set node height:
                treeNode.setHeight(thisFlatNode.getHeight());
            }

            // Replace old active node lists with new:
            activeFlatTreeNodes.clear();
            activeFlatTreeNodes.addAll(nextActiveFlatTreeNodes);

            activeTreeNodes.clear();
            activeTreeNodes.addAll(nextActiveTreeNodes);

        }

        // Assign tree topology:
        assignFromWithoutID(new MultiTypeTree(newRoot));
        initArrays();
    }

    /**
     * Obtain total number of non-single-child nodes in subtree under node.
     *
     * @param node Root of subtree.
     * @return
     */
    private static int getTrueNodeCount(Node node) {
        if (node.isLeaf())
            return 1;

        if (node.getChildCount()>1)
            return 1+getTrueNodeCount(node.getLeft())
                    +getTrueNodeCount(node.getRight());

        return getTrueNodeCount(node.getLeft());
    }

    /**
     * Return string representation of coloured tree.
     *
     * @return Coloured tree string in Newick format.
     */
    @Override
    public String toString() {
        return getFlattenedTree().getRoot().toNewick(null);
    }

    /////////////////////////////////////////////////
    //           StateNode implementation          //
    /////////////////////////////////////////////////
    @Override
    protected void store() {
        storedMultiTypeRoot = storedMultiTypeNodes[multiTypeRoot.getNr()];
        int iRoot = multiTypeRoot.getNr();

        storeNodes(0, iRoot);
        
        storedMultiTypeRoot.m_fHeight = multiTypeNodes[iRoot].m_fHeight;
        storedMultiTypeRoot.m_Parent = null;
        storedMultiTypeRoot.multiTypeParent = null;

        if (multiTypeRoot.getLeft()!=null)
            storedMultiTypeRoot.setLeft(storedMultiTypeNodes[multiTypeRoot.getLeft().getNr()]);
        else
            storedMultiTypeRoot.setLeft(null);
        if (multiTypeRoot.getRight()!=null)
            storedMultiTypeRoot.setRight(storedMultiTypeNodes[multiTypeRoot.getRight().getNr()]);
        else
            storedMultiTypeRoot.setRight(null);
        
        storedMultiTypeRoot.changeTimes.clear();
        storedMultiTypeRoot.changeTimes.addAll(multiTypeNodes[iRoot].changeTimes);

        storedMultiTypeRoot.changeTypes.clear();
        storedMultiTypeRoot.changeTypes.addAll(multiTypeNodes[iRoot].changeTypes);
        
        storedMultiTypeRoot.nTypeChanges = multiTypeNodes[iRoot].nTypeChanges;
        storedMultiTypeRoot.nodeType = multiTypeNodes[iRoot].nodeType;
        
        storeNodes(iRoot+1, nodeCount);
    }

    /**
     * helper to store *
     */
    private void storeNodes(int iStart, int iEnd) {
        for (int i = iStart; i<iEnd; i++) {
            MultiTypeNode sink = storedMultiTypeNodes[i];
            MultiTypeNode src = multiTypeNodes[i];
            sink.m_fHeight = src.m_fHeight;
            sink.m_Parent = storedMultiTypeNodes[src.multiTypeParent.getNr()];
            sink.multiTypeParent = storedMultiTypeNodes[src.multiTypeParent.getNr()];
            if (src.getLeft()!=null) {
                sink.setLeft(storedMultiTypeNodes[src.getLeft().getNr()]);
                if (src.getRight()!=null)
                    sink.setRight(storedMultiTypeNodes[src.getRight().getNr()]);
                else
                    sink.setRight(null);
            }
            
            sink.changeTimes.clear();
            sink.changeTimes.addAll(src.changeTimes);
            
            sink.changeTypes.clear();
            sink.changeTypes.addAll(src.changeTypes);
            
            sink.nTypeChanges = src.nTypeChanges;
            sink.nodeType = src.nodeType;
        }
    }

    @Override
    public void restore() {
        MultiTypeNode[] tmp = storedMultiTypeNodes;
        storedMultiTypeNodes = multiTypeNodes;
        multiTypeNodes = tmp;
        multiTypeRoot = multiTypeNodes[storedMultiTypeRoot.getNr()];
        m_bHasStartedEditing = false;
    }

    /////////////////////////////////////////////////
    // Methods implementing the Loggable interface //
    /////////////////////////////////////////////////
    @Override
    public void init(PrintStream printStream) throws Exception {

        printStream.println("#NEXUS\n");
        printStream.println("Begin taxa;");
        printStream.println("\tDimensions ntax="+getLeafNodeCount()+";");
        printStream.println("\t\tTaxlabels");
        for (int i = 0; i<getLeafNodeCount(); i++)
            printStream.println("\t\t\t"+getNodesAsArray()[i].getID());
        printStream.println("\t\t\t;");
        printStream.println("End;");

        printStream.println("Begin trees;");
        printStream.println("\tTranslate");
        for (int i = 0; i<getLeafNodeCount(); i++) {
            printStream.print("\t\t\t"+getNodesAsArray()[i].getNr()
                    +" "+getNodesAsArray()[i].getID());
            if (i<getLeafNodeCount()-1)
                printStream.print(",");
            printStream.print("\n");
        }
        printStream.print("\t\t\t;");
    }

    @Override
    public void log(int i, PrintStream printStream) {
        Tree flatTree = getFlattenedTree();
        printStream.print("tree STATE_"+i+" = ");
        String sNewick = flatTree.getRoot().toNewick(null);
        printStream.print(sNewick);
        printStream.print(";");


    }

    @Override
    public void close(PrintStream printStream) {
        printStream.println("End;");
    }
}
