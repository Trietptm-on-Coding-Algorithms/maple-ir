package org.rsdeob.stdlib.collections.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.SetCreator;

public class TarjanDominanceComputor<N extends FastGraphVertex> {

	private static final Sorter<?> sorterImpl = Sorters.get("dfs");
	
	private final FlowGraph<N, ?> graph;
	private final List<N> preOrder;
	private final Map<N, Integer> semiIndices;
	private final Map<N, N> parents;
	private final Map<N, N> propagationMap;
	private final Map<N, N> ancestors;
	private final Map<N, N> idoms;
	private final NullPermeableHashMap<N, Set<N>> semiDoms;
	private final NullPermeableHashMap<N, Set<N>> domChildren;
	private final NullPermeableHashMap<N, Set<N>> frontiers;
	
	public TarjanDominanceComputor(FlowGraph<N, ?> graph) {
		this.graph = graph;
		preOrder = new ArrayList<>();
		semiIndices = new HashMap<>();
		parents = new HashMap<>();
		propagationMap = new HashMap<>();
		ancestors = new HashMap<>();
		idoms = new HashMap<>();
		semiDoms = new NullPermeableHashMap<>(new SetCreator<>());
		domChildren = new NullPermeableHashMap<>(new SetCreator<>());
		frontiers = new NullPermeableHashMap<>(new SetCreator<>());
		
		computePreOrder();
		computeDominators();
		touchTree();
		computeFrontiers();
	}
	
//	public Set<N> dominates(N n) {
//		return domChildren.getNonNull(n);
//	}
	
	public Set<N> semiDoms(N n) {
		return semiDoms.getNonNull(n);
	}
	
	public Set<N> frontier(N n) {
		return frontiers.getNonNull(n);
	}
	
	public N idom(N n) {
		return idoms.get(n);
	}
	
	@SuppressWarnings("unchecked")
	private Sorter<N> sorter() {
		return (Sorter<N>) sorterImpl;
	}
	
	private void touchTree() {
		for(N n : idoms.keySet()) {
			domChildren.getNonNull(idoms.get(n)).add(n);
		}
	}
	
	private Iterator<N> topoSort() {
		LinkedList<N> list = new LinkedList<>();
		for(N n : preOrder) {
			int i = list.indexOf(idoms.get(n));
			if(i == -1) {
				list.add(n);
			} else {
				list.add(i + 1, n);
			}
		}
		return list.descendingIterator();
	}
	
	private void computeFrontiers() {
		Iterator<N> it = topoSort();
		while(it.hasNext()) {
			N n = it.next();
			Set<N> df = frontiers.getNonNull(n);
			
			// DF(local)
			for(N succ : FastGraph.computeSuccessors(graph, n)) {
				if(idoms.get(succ) != n) {
					df.add(succ);
				}
			}
			
			// DF(up)
			for(N forest : domChildren.getNonNull(n)) {
				for(N forestFrontier : frontiers.getNonNull(forest)) {
					if(idoms.get(forestFrontier) != n) {
						df.add(forestFrontier);
					}
				}
			}
		}
	}
	
	private void computePreOrder() {
		Iterator<N> it = sorter().iterator(graph);
		while(it.hasNext()) {
			N n = it.next();
			if(!semiIndices.containsKey(n)) {
				preOrder.add(n);
				semiIndices.put(n, semiIndices.size());
				propagationMap.put(n, n);
				
				for(N succ : FastGraph.computeSuccessors(graph, n)) {
					if(!semiIndices.containsKey(succ)) {
						parents.put(succ, n);
					}
				}
			}
		}
	}
	
	private void computeDominators() {
		int size = semiIndices.size() - 1;
		
		// ignore entry
		
		for(int i=size; i > 0; i--) {
			N n = preOrder.get(i);
			N p = parents.get(n);
			
			int newIndex = semiIndices.get(n);
			for(FastGraphEdge<N> e : graph.getReverseEdges(n)) {
				newIndex = Math.min(newIndex, semiIndices.get(calcSemiDom(e.src)));
			}
			
			semiIndices.put(n, newIndex);
			
			N semiIndex = preOrder.get(newIndex);
			semiDoms.getNonNull(semiIndex).add(n);
			
			ancestors.put(n, p);
			
			for(N v : semiDoms.getNonNull(p)) {
				N u = calcSemiDom(v);
				
				if(semiIndices.get(u) < semiIndices.get(v)) {
					idoms.put(v, u);
				} else {
					idoms.put(v, p);
				}
			}
			
			semiDoms.get(p).clear();
		}
		
		for(int i=1; i <= size; i++) {
			N n = preOrder.get(i);
			if(idoms.get(n) != preOrder.get(semiIndices.get(n))) {
				idoms.put(n, idoms.get(idoms.get(n)));
			}
		}
	}
	
	private N calcSemiDom(N n) {
		propagate(n);
		return propagationMap.get(n);
	}
	
	private void propagate(N n) {
		LinkedList<N> wl = new LinkedList<>();
		wl.add(n);
		N anc = ancestors.get(n);
		
		while(ancestors.containsKey(anc)) {
			wl.push(anc);
			anc = ancestors.get(anc);
		}
		
		anc = wl.pop();
		int bottom = semiIndices.get(propagationMap.get(anc));
		
		while(!wl.isEmpty()) {
			N d = wl.pop();
			int current = semiIndices.get(propagationMap.get(d));
			if(current > bottom) {
				propagationMap.put(d, propagationMap.get(anc));
			} else {
				bottom = current;
			}
			
			anc = d;
		}
	}
}