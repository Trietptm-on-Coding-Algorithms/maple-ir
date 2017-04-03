package org.mapleir.deob.passes.eval;

import org.mapleir.deob.passes.FieldRSADecryptionPass;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.CastExpr;
import org.mapleir.ir.code.expr.ComparisonExpr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.NegationExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.stdlib.util.TypeUtils;
import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.Set;

import static org.mapleir.ir.code.Opcode.ARITHMETIC;
import static org.mapleir.ir.code.Opcode.CAST;
import static org.mapleir.ir.code.Opcode.COMPARE;
import static org.mapleir.ir.code.Opcode.CONST_LOAD;
import static org.mapleir.ir.code.Opcode.LOCAL_LOAD;
import static org.mapleir.ir.code.Opcode.NEGATE;

public class ExpressionEvaluator {
	BridgeFactory bridgeFactory;
	
	public static boolean isValidSet(Set<?> set) {
		return set != null && set.size() > 0;
	}
	
	private static <T> Set<T> returnCleanSet(Set<T> set) {
		if(set != null && set.size() > 0) {
			return set;
		} else {
			return null;
		}
	}
	
	public ExpressionEvaluator() {
		bridgeFactory = new BridgeFactory();
	}
	
	// todo: delete this method
	public Object evalMultiplication(Expr c, ConstantExpr cc, Expr k, ConstantExpr ck) {
		Bridge bridge = bridgeFactory.getArithmeticBridge(c.getType(), k.getType(), c.getType().getSort() > k.getType().getSort()? c.getType() : k.getType(), ArithmeticExpr.Operator.MUL);

		/*System.out.println("eval: " + bridge.method + " " + cc.getConstant().getClass() + " " + ck.getConstant().getClass());
		System.out.println("   actual: " + cc.getType() + ", " +  ck.getType());
		System.out.println("      " + cc.getConstant() +"  " + ck.getConstant());*/
		
		return bridge.eval(cc.getConstant(), ck.getConstant());
	}
	
	public Expr eval(LocalsPool pool, Expr e) {
		if(e.getOpcode() == CONST_LOAD) {
			return e.copy();
		} else if(e.getOpcode() == ARITHMETIC) {
			ArithmeticExpr ae = (ArithmeticExpr) e;
			Expr l = ae.getLeft();
			Expr r = ae.getRight();
			
			Expr le = eval(pool, l);
			Expr re = eval(pool, r);
			
			if(le != null && re != null) {
				ConstantExpr lc = (ConstantExpr) le;
				ConstantExpr rc = (ConstantExpr) re;
				
				Bridge b = bridgeFactory.getArithmeticBridge(lc.getType(), rc.getType(), ae.getType(), ae.getOperator());
				
				ConstantExpr cr = new ConstantExpr(b.eval(lc.getConstant(), rc.getConstant()), ae.getType());
				return cr;
			}
		} else if(e.getOpcode() == NEGATE) {
			NegationExpr neg = (NegationExpr) e;
			Expr e2 = eval(pool, neg.getExpression());
			
			if(e2 != null) {
				ConstantExpr ce = (ConstantExpr) e2;
				Bridge b = bridgeFactory.getNegationBridge(e2.getType());
				
				ConstantExpr cr = new ConstantExpr(b.eval(ce.getConstant()), ce.getType());
				return cr;
			}
		} else if(e.getOpcode() == LOCAL_LOAD) {
			VarExpr v = (VarExpr) e;
			Local l = v.getLocal();
			
			AbstractCopyStmt def = pool.defs.get(l);
			Expr rhs = def.getExpression();
			
			if(rhs.getOpcode() == LOCAL_LOAD) {
				VarExpr v2 = (VarExpr) rhs;
				
				// synthetic copies lhs = rhs;
				if(v2.getLocal() == l) {
					return null;
				}
			}
			
			return eval(pool, rhs);
		} else if(e.getOpcode() == CAST) {
			CastExpr cast = (CastExpr) e;
			Expr e2 = eval(pool, cast.getExpression());
			
			if(e2 != null) {
				ConstantExpr ce = (ConstantExpr) e2;
				
				if(!ce.getType().equals(cast.getExpression().getType())) {
					throw new IllegalStateException(ce.getType() + " : " + cast.getExpression().getType());
				}
				Type from = ce.getType();
				Type to = cast.getType();
				
				boolean p1 = TypeUtils.isPrimitive(from);
				boolean p2 = TypeUtils.isPrimitive(to);
				
				if(p1 != p2) {
					throw new IllegalStateException(from + " to " + to);
				}
				
				if(!p1 && !p2) {
					return null;
				}
				
				Bridge b = bridgeFactory.getCastBridge(from, to);
				
				ConstantExpr cr = new ConstantExpr(b.eval(ce.getConstant()), to);
				return cr;
			}
		} else if(e.getOpcode() == COMPARE) {
			ComparisonExpr comp = (ComparisonExpr) e;
			
			Expr l = comp.getLeft();
			Expr r = comp.getRight();
			
			Expr le = eval(pool, l);
			Expr re = eval(pool, r);
			
			if(le != null && re != null) {
				ConstantExpr lc = (ConstantExpr) le;
				ConstantExpr rc = (ConstantExpr) re;
				
				Bridge b = bridgeFactory.getComparisonBridge(lc.getType(), rc.getType(), comp.getComparisonType());
				
				ConstantExpr cr = new ConstantExpr(b.eval(lc.getConstant(), rc.getConstant()), Type.INT_TYPE);
				return cr;
			}
		}
		
		return null;
	}
	
	public Expr simplify(LocalsPool pool, ArithmeticExpr e) {
		Expr r = e.getRight();
		
		Expr re = eval(pool, r);
		
		if(re instanceof ConstantExpr) {
			ConstantExpr ce =(ConstantExpr) re;
			
			Object o = ce.getConstant();
			
			if(o instanceof Integer || o instanceof Long) {
				if(FieldRSADecryptionPass.__eq((Number) o, 1, o instanceof Long)) {
					return e.getLeft().copy();
				} else if(FieldRSADecryptionPass.__eq((Number) o, 0, o instanceof Long)) {
					return new ConstantExpr(0, ce.getType());
				}
			}
		}
		
		return null;
	}
	
	public Set<ConstantExpr> evalPossibleValues(LocalValueResolver resolver, Expr e) {
		if(e.getOpcode() == CONST_LOAD) {
			Set<ConstantExpr> set = new HashSet<>();
			set.add((ConstantExpr) e);
			return set;
		} else if(e.getOpcode() == ARITHMETIC) {
			ArithmeticExpr ae = (ArithmeticExpr) e;
			Expr l = ae.getLeft();
			Expr r = ae.getRight();
			
			Set<ConstantExpr> le = evalPossibleValues(resolver, l);
			Set<ConstantExpr> re = evalPossibleValues(resolver, r);
			
			if(isValidSet(le) && isValidSet(re)) {
				Set<ConstantExpr> results = new HashSet<>();
				
				for(ConstantExpr lc : le) {
					for(ConstantExpr rc : re) {
						Bridge b = bridgeFactory.getArithmeticBridge(lc.getType(), rc.getType(), ae.getType(), ae.getOperator());
						results.add(new ConstantExpr(b.eval(lc.getConstant(), rc.getConstant()), ae.getType()));
					}
				}
				
				return returnCleanSet(results);
			}
		} else if(e.getOpcode() == NEGATE) {
			NegationExpr neg = (NegationExpr) e;
			Set<ConstantExpr> vals = evalPossibleValues(resolver, neg.getExpression());
			
			if(isValidSet(vals)) {
				Set<ConstantExpr> results = new HashSet<>();
				
				for(ConstantExpr c : vals) {
					Bridge b = bridgeFactory.getNegationBridge(c.getType());
					results.add(new ConstantExpr(b.eval(c.getConstant()), c.getType()));
				}
				
				return returnCleanSet(results);
			}
		} else if(e.getOpcode() == LOCAL_LOAD) {
			VarExpr v = (VarExpr) e;
			Local l = v.getLocal();
			
			Set<Expr> defExprs = resolver.getValues(l);

			if(isValidSet(defExprs)) {
				Set<ConstantExpr> vals = new HashSet<>();
				
				for(Expr defE : defExprs) {
					if(defE.getOpcode() == LOCAL_LOAD) {
						VarExpr v2 = (VarExpr) defE;
						
						// synthetic copies lhs = rhs;
						if(v2.getLocal() == l) {
							continue;
						}
					}
					
					Set<ConstantExpr> set2 = evalPossibleValues(resolver, defE);
					if(isValidSet(set2)) {
						vals.addAll(set2);
					}
				}
				
				return returnCleanSet(vals);
			}
		} else if(e.getOpcode() == CAST) {
			CastExpr cast = (CastExpr) e;
			Set<ConstantExpr> set = evalPossibleValues(resolver, cast.getExpression());
			
			if(isValidSet(set)) {
				Set<ConstantExpr> results = new HashSet<>();
				
				for(ConstantExpr ce : set) {
					if(!ce.getType().equals(cast.getExpression().getType())) {
						throw new IllegalStateException(ce.getType() + " : " + cast.getExpression().getType());
					}
					Type from = ce.getType();
					Type to = cast.getType();
					
					boolean p1 = TypeUtils.isPrimitive(from);
					boolean p2 = TypeUtils.isPrimitive(to);
					
					if(p1 != p2) {
						throw new IllegalStateException(from + " to " + to);
					}
					
					if(!p1 && !p2) {
						return null;
					}
					
					Bridge b = bridgeFactory.getCastBridge(from, to);
					
					results.add(new ConstantExpr(b.eval(ce.getConstant()), to));
				}
				
				return returnCleanSet(results);
			}
		} else if(e.getOpcode() == COMPARE) {
//			throw new UnsupportedOperationException("todo lmao");
//			ComparisonExpr comp = (ComparisonExpr) e;

//			Expr l = comp.getLeft();
//			Expr r = comp.getRight();
//
//			Expr le = eval(pool, l);
//			Expr re = eval(pool, r);
//
//			if(le != null && re != null) {
//				ConstantExpr lc = (ConstantExpr) le;
//				ConstantExpr rc = (ConstantExpr) re;
//
//				Bridge b = getComparisonBridge(lc.getType(), rc.getType(), comp.getComparisonType());
//
//				System.out.println(b.method);
//				System.out.println(comp + " -> " + b.eval(lc.getConstant(), rc.getConstant()));
//				ConstantExpr cr = new ConstantExpr((int)b.eval(lc.getConstant(), rc.getConstant()));
//				return cr;
//			}
		}
		
		return null;
	}
	
	public Boolean evaluateConditional(ConditionalJumpStmt cond, Set<ConstantExpr> leftSet, Set<ConstantExpr> rightSet) {
		Boolean val = null;
		
		for(ConstantExpr lc : leftSet) {
			for(ConstantExpr rc : rightSet) {
				if(TypeUtils.isPrimitive(lc.getType()) && TypeUtils.isPrimitive(rc.getType())) {
					Bridge bridge = bridgeFactory.getConditionalEvalBridge(lc.getType(), rc.getType(), cond.getComparisonType());
					/*System.out.println("eval: " + bridge.method + " " + lc.getConstant().getClass() + " " + rc.getConstant().getClass());
					System.out.println("   actual: " + lc.getType() + ", " +  rc.getType());
					System.out.println("      " + lc.getConstant() +"  " + rc.getConstant());*/
					
					boolean branchVal = (boolean) bridge.eval(lc.getConstant(), rc.getConstant());
					
					if(val != null) {
						if(val != branchVal) {
							return null;
						}
					} else {
						val = branchVal;
					}
				} else {
					/*System.err.println("something::");
					System.err.println("  " + cond);
					System.err.println("  leftset: " + leftSet);
					System.err.println("  rightSet: " + rightSet);|
					return;*/
					throw new UnsupportedOperationException();
				}
			}
		}

		return val;
	}
}
