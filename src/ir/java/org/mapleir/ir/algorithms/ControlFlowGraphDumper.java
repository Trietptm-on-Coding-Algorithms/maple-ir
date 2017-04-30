package org.mapleir.ir.algorithms;

import org.mapleir.deob.intraproc.ExceptionAnalysis;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.*;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.stmt.UnconditionalJumpStmt;
import org.mapleir.stdlib.collections.IndexedList;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.algorithms.SimpleDfs;
import org.mapleir.stdlib.collections.graph.algorithms.TarjanSCC;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class ControlFlowGraphDumper {
	private static boolean printedIt = false;
	
	public static void dump(ControlFlowGraph cfg, MethodNode m) {
		// Clear methodnode
		m.instructions.removeAll(true);
		m.tryCatchBlocks.clear();
		m.visitCode();
		for (BasicBlock b : cfg.vertices()) {
			b.resetLabel();
		}

		// Linearize
		IndexedList<BasicBlock> blocks = linearize(cfg);
		if (!new ArrayList<>(blocks).equals(new ArrayList<>(cfg.vertices()))) {
			System.err.println("[warn] Differing linearizations: " + m);
			printOrdering(new ArrayList<>(cfg.vertices()));
			printOrdering(blocks);
		}
		if (m.toString().equals("cmk.bfw(B)Z")) {
			// System.out.println(cfg);
			// printOrdering(new ArrayList<>(cfg.vertices()));
			// printOrdering(blocks);
			// System.exit(1);
		}
		
		// Dump code
		for (BasicBlock b : blocks) {
			m.visitLabel(b.getLabel());
			for (Stmt stmt : b) {
				stmt.toCode(m, null);
			}
		}
		LabelNode terminalLabel = new LabelNode();
		m.visitLabel(terminalLabel.getLabel());
		
		// Verify
		ListIterator<BasicBlock> it = blocks.listIterator();
		while(it.hasNext()) {
			BasicBlock b = it.next();
			
			for(FlowEdge<BasicBlock> e: cfg.getEdges(b)) {
				if(e.getType() == FlowEdges.IMMEDIATE) {
					if(it.hasNext()) {
						BasicBlock n = it.next();
						it.previous();
						
						if(n != e.dst) {
							throw new IllegalStateException("Illegal flow " + e + " > " + n);
						}
					} else {
						throw new IllegalStateException("Trailing " + e);
					}
				}
			}
		}

		printedIt = false;
		for (ExceptionRange<BasicBlock> er : cfg.getRanges()) {
//			System.out.println("RANGE: " + er);
			dumpRange(cfg, m, blocks, er, terminalLabel.getLabel());
		}
		m.visitEnd();
	}
	
	private static void printOrdering(List<BasicBlock> order) {
		for (int i = 0; i < order.size(); i++) {
			BasicBlock b = order.get(i);
			System.err.print(b.getId());
			BasicBlock next = b.getImmediate();
			if (next != null) {
				if (next == order.get(i + 1)) {
					System.err.print("->");
				} else {
					throw new IllegalStateException("WTF");
				}
			} else {
				System.err.print(" ");
			}
		}
		System.err.println();
	}
	
	private static void dumpRange(ControlFlowGraph cfg, MethodNode m, IndexedList<BasicBlock> order, ExceptionRange<BasicBlock> er, Label terminalLabel) {
		// Determine exception type
		Type type = null;
		Set<Type> typeSet = er.getTypes();
		if (typeSet.size() != 1) {
			// TODO: fix base exception
			type = ExceptionAnalysis.THROWABLE;
		} else {
			type = typeSet.iterator().next();
		}
		
		final Label handler = er.getHandler().getLabel();
		List<BasicBlock> range = er.get();
		range.sort(Comparator.comparing(order::indexOf));
		
		Label start = range.get(0).getLabel();
		int rangeIdx = 0, orderIdx = order.indexOf(range.get(rangeIdx));
		for (;;) {
			// check for endpoints
			if (orderIdx + 1 == order.size()) { // end of method
				m.visitTryCatchBlock(start, terminalLabel, handler, type.getInternalName());
				break;
			} else if (rangeIdx + 1 == range.size()) { // end of range
				Label end = order.get(orderIdx + 1).getLabel();
				m.visitTryCatchBlock(start, end, handler, type.getInternalName());
				break;
			}
			
			// check for discontinuity
			BasicBlock nextBlock = range.get(rangeIdx + 1);
			int nextOrderIdx = order.indexOf(nextBlock);
			if (nextOrderIdx - orderIdx > 1 && !printedIt) { // blocks in-between, end the handler and begin anew
				printedIt = true;
				System.err.println("[warn] Had to split up a range: " + m);
				// printOrdering(new ArrayList<>(cfg.vertices()));
				// printOrdering(order);
				// System.out.println();
				// for (BasicBlock b : cfg.vertices()) {
				// 	StringBuilder s = new StringBuilder();
				// 	s.append(b.getId()).append("-> ");
				// 	for (FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
				// 		if (e instanceof TryCatchEdge)
				// 			continue;
				// 		if (e instanceof ImmediateEdge)
				// 			s.append("i:");
				// 		s.append(e.dst.getId()).append(" ");
				// 	}
				// 	while (s.length() < 30)
				// 		s.append(" ");
				// 	s.append(" || ");
				// 	for (FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
				// 		if (!(e instanceof TryCatchEdge))
				// 			continue;
				// 		s.append(e.dst.getId()).append(" ");
				// 	}
				// 	System.out.println(s);
				// }
				// System.err.println(cfg);
				// System.err.println(m);
				// System.err.println(er);
				// System.err.println("Range: " + range);
				// System.err.println("Order: " + order);
				// System.err.println("range, order, next: " + rangeIdx + " " + orderIdx + " " + nextOrderIdx);
				// System.err.println("corresponding blocks: " + range.get(rangeIdx) + " " + nextBlock);
				// for (int i = 0; i < 20; i++)
				// 	System.err.println();
				Label end = order.get(orderIdx + 1).getLabel();
				m.visitTryCatchBlock(start, end, handler, type.getInternalName());
				start = nextBlock.getLabel();
			}

			// next
			rangeIdx++;
			if (nextOrderIdx != -1)
				orderIdx = nextOrderIdx;
		}
	}
	
	private static List<BlockBundle> linearize(Collection<BlockBundle> bundles, BundleGraph fullGraph, BlockBundle entryBundle) {
		BundleGraph subgraph = fullGraph.inducedSubgraph(bundles);
		
		TarjanSCC<BlockBundle> sccComputor = new TarjanSCC<>(subgraph);
		sccComputor.search(entryBundle);
		for(BlockBundle b : bundles) {
			if(sccComputor.low(b) == -1) {
				sccComputor.search(b);
			}
		}
		
		List<BlockBundle> order = new ArrayList<>();
		
		// Flatten
		List<List<BlockBundle>> components = sccComputor.getComponents();
		if (components.size() == 1)
			order.addAll(components.get(0));
		else for (List<BlockBundle> scc : components)
			order.addAll(linearize(scc, subgraph, scc.get(0)));
		return order;
	}
	
	private static IndexedList<BasicBlock> linearize(ControlFlowGraph cfg) {
		if (cfg.getEntries().size() != 1)
			throw new IllegalStateException("CFG doesn't have exactly 1 entry");
		BasicBlock entry = cfg.getEntries().iterator().next();
		
		if (cfg.getMethod().toString().equals("cmk.bfw(B)Z")) {
			System.err.println("aa");
		}
		
		// Build bundle graph
		Map<BasicBlock, BlockBundle> bundles = new HashMap<>();
		Map<BlockBundle, List<BlockBundle>> bunches = new HashMap<>();
		
		// Build bundles
		List<BasicBlock> postorder = new SimpleDfs<>(cfg, entry, SimpleDfs.POST).getPostOrder();
		for (int i = postorder.size() - 1; i >= 0; i--) {
			BasicBlock b = postorder.get(i);
			if (bundles.containsKey(b)) // Already in a bundle
				continue;
			
			if (b.getIncomingImmediateEdge() != null) // Look for heads of bundles only
				continue;
			
			BlockBundle bundle = new BlockBundle();
			while (b != null) {
				bundle.add(b);
				bundles.put(b, bundle);
				b = b.getImmediate();
			}
			
			List<BlockBundle> bunch = new ArrayList<>();
			bunch.add(bundle);
			bunches.put(bundle, bunch);
		}
		
		// Group bundles by exception ranges
		for (ExceptionRange<BasicBlock> range : cfg.getRanges()) {
			BlockBundle prevBundle = null;
			for (BasicBlock b : range.getNodes()) {
				BlockBundle curBundle = bundles.get(b);
				if (prevBundle == null) {
					prevBundle = curBundle;
					continue;
				}
				if (curBundle != prevBundle) {
					List<BlockBundle> bunchA = bunches.get(prevBundle);
					List<BlockBundle> bunchB = bunches.get(curBundle);
					if (bunchA != bunchB) {
						bunchA.addAll(bunchB);
						for (BlockBundle bundle : bunchB) {
							bunches.put(bundle, bunchA);
						}
					}
					prevBundle = curBundle;
				}
			}
		}
		
		// Rebuild bundles
		bundles.clear();
		for (Map.Entry<BlockBundle, List<BlockBundle>> e : bunches.entrySet()) {
			BlockBundle bundle = e.getKey();
			if (bundles.containsKey(bundle.getFirst()))
				continue;
			BlockBundle bunch = new BlockBundle();
			e.getValue().forEach(bunch::addAll);
			for (BasicBlock b : bunch)
				bundles.put(b, bunch);
		}
		
		BundleGraph bundleGraph = new BundleGraph();
		BlockBundle entryBundle = bundles.get(entry);
		bundleGraph.addVertex(entryBundle);
		for (BasicBlock b : postorder) {
			for (FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
				if (e instanceof ImmediateEdge)
					continue;
				BlockBundle src = bundles.get(b);
				bundleGraph.addEdge(src, new FastGraphEdge<>(src, bundles.get(e.dst)));
			}
		}
		
		// Flatten
		IndexedList<BasicBlock> order = new IndexedList<>();
		linearize(new HashSet<>(bundles.values()), bundleGraph, entryBundle).forEach(order::addAll);
		
		// Fix immediates
		for (int i = 0; i < order.size(); i++) {
			BasicBlock b = order.get(i);
			for (FlowEdge<BasicBlock> e : new HashSet<>(cfg.getEdges(b))) {
				BasicBlock dst = e.dst;
				if (e instanceof ImmediateEdge && order.indexOf(dst) != i + 1) {
					b.add(new UnconditionalJumpStmt(dst));
					cfg.removeEdge(b, e);
					cfg.addEdge(b, new UnconditionalJumpEdge<>(b, dst));
					
					System.err.println("[warn] Had to fixup immediate to goto: " + cfg.getMethod());
				}
			}
		}
		
		return order;
	}
	
	// TODO: default graph impl
	private static class BundleGraph extends FastDirectedGraph<BlockBundle, FastGraphEdge<BlockBundle>> {
		@Override
		public boolean excavate(BlockBundle basicBlocks) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public boolean jam(BlockBundle pred, BlockBundle succ, BlockBundle basicBlocks) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public FastGraphEdge<BlockBundle> clone(FastGraphEdge<BlockBundle> edge, BlockBundle oldN, BlockBundle newN) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public FastGraphEdge<BlockBundle> invert(FastGraphEdge<BlockBundle> edge) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public FastGraph<BlockBundle, FastGraphEdge<BlockBundle>> copy() {
			throw new UnsupportedOperationException();
		}
		
		// todo: move up to FastGraph!
		public BundleGraph inducedSubgraph(Collection<BlockBundle> vertices) {
			BundleGraph subgraph = new BundleGraph();
			for (BlockBundle n : vertices) {
				subgraph.addVertex(n);
				for (FastGraphEdge<BlockBundle> e : getEdges(n)) {
					if (vertices.contains(e.dst))
						subgraph.addEdge(n, e);
				}
			}
			return subgraph;
		}
	}
	
	private static class BlockBundle extends ArrayList<BasicBlock> implements FastGraphVertex {
		private BasicBlock first = null;
		
		private BasicBlock getFirst() {
			if (first == null)
				first = get(0);
			return first;
		}
		
		@Override
		public String getId() {
			return getFirst().getId();
		}
		
		@Override
		public int getNumericId() {
			return getFirst().getNumericId();
		}
		
		@Override
		public String toString() {
			StringBuilder s = new StringBuilder();
			for (Iterator<BasicBlock> it = this.iterator(); it.hasNext(); ) {
				BasicBlock b = it.next();
				s.append(b.getId());
				if (it.hasNext())
					s.append("->");
			}
			return s.toString();
		}
		
		@Override
		public int hashCode() {
			return getFirst().hashCode();
		}
		
		@Override
		public boolean equals(Object o) {
			if (!(o instanceof BlockBundle))
				return false;
			return ((BlockBundle) o).getFirst().equals(getFirst());
		}
	}
}
