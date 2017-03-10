package org.mapleir.ir.code.stmt;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.mapleir.stdlib.util.TypeUtils;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class NopStmt extends Stmt {
	public NopStmt() {
		super(NOP);
	}

	@Override
	public void onChildUpdated(int ptr) {
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("nop;");
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		return false;
	}

	@Override
	public boolean isAffectedBy(CodeUnit stmt) {
		return false;
	}

	@Override
	public NopStmt copy() {
		return new NopStmt();
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		return s instanceof NopStmt;
	}
}