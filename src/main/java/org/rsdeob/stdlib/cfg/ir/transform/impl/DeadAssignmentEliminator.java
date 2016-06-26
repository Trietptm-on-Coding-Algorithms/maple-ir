package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.rsdeob.stdlib.cfg.ir.Local;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

public class DeadAssignmentEliminator {

	public static int run(CodeAnalytics analytics) {
		StatementGraph graph = analytics.statementGraph;
		LivenessAnalyser la = analytics.liveness;
		RootStatement root = analytics.root;
		
		AtomicInteger dead = new AtomicInteger();
		for(Statement stmt : new HashSet<>(graph.vertices())) {
			if(stmt instanceof CopyVarStatement) {
				Map<Local, Boolean> out = la.out(stmt);
				CopyVarStatement copy = (CopyVarStatement) stmt;
				VarExpression var = copy.getVariable();
				
				if(!out.get(var.getLocal())) {
					root.delete(root.indexOf(copy));
					graph.excavate(copy);
					dead.incrementAndGet();
				}
			}
		}
		return dead.get();
	}
}