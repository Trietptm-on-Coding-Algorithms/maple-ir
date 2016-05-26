package org.rsdeob.stdlib.collections.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.tree.LabelNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;

public abstract class FastBlockGraph extends FastGraph<BasicBlock, FlowEdge<BasicBlock>> {

	private final Map<LabelNode, BasicBlock> blockLabels;
	private final Map<String, BasicBlock> blockIds;
	
	public FastBlockGraph() {
		blockLabels = new HashMap<>();
		blockIds = new HashMap<>();
	}
	
	public BasicBlock getBlock(LabelNode label) {
		return blockLabels.get(label);
	}
	
	public BasicBlock getBlock(String id) {
		return blockIds.get(id);
	}
	
	public Set<BasicBlock> blocks() {
		return vertices();
	}
	
	@Override
	public void addVertex(BasicBlock v) {
		super.addVertex(v);
		
		blockLabels.put(v.getLabel(), v);
		blockIds.put(v.getId(), v);
	}
	
	@Override
	public void removeVertex(BasicBlock v) {
		super.removeVertex(v);
		
		blockLabels.remove(v.getLabel());
		blockIds.remove(v.getId());
		
		if(v.getId().equals("A")) {
			new Exception().printStackTrace();
			System.exit(3);
		}
	}
	
	@Override
	public void addEdge(BasicBlock v, FlowEdge<BasicBlock> e) {
		blockLabels.put(v.getLabel(), v);
		blockIds.put(v.getId(), v);
		
		super.addEdge(v, e);
	}


	@Override
	protected BasicBlock getSource(BasicBlock n, FlowEdge<BasicBlock> e) {
		return e.src;
	}


	@Override
	protected BasicBlock getDestination(BasicBlock n, FlowEdge<BasicBlock> e) {
		return e.dst;
	}
}