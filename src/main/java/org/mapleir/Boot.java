package org.mapleir;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarOutputStream;

import org.mapleir.byteio.CompleteResolvingJarDumper;
import org.mapleir.deobimpl2.CallgraphPruningPass;
import org.mapleir.deobimpl2.ConstantExpressionEvaluatorPass;
import org.mapleir.deobimpl2.ConstantExpressionReorderPass;
import org.mapleir.deobimpl2.ConstantParameterPass;
import org.mapleir.deobimpl2.DeadCodeEliminationPass;
import org.mapleir.deobimpl2.MethodRenamerPass;
import org.mapleir.deobimpl2.cxt.MapleDB;
import org.mapleir.ir.ControlFlowGraphDumper;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.Expr;
import org.mapleir.deobimpl2.cxt.IContext;
import org.mapleir.stdlib.app.ApplicationClassSource;
import org.mapleir.stdlib.app.InstalledRuntimeClassSource;
import org.mapleir.stdlib.call.CallTracer;
import org.mapleir.stdlib.deob.IPass;
import org.mapleir.stdlib.deob.PassGroup;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;
import org.topdank.byteio.out.JarDumper;

public class Boot {

	public static boolean logging = false;
	private static long timer;
	private static Deque<String> sections;
	
	private static double lap() {
		long now = System.nanoTime();
		long delta = now - timer;
		timer = now;
		return (double)delta / 1_000_000_000L;
	}
	
	public static void section0(String endText, String sectionText, boolean quiet) {
		if(sections.isEmpty()) {
			lap();
			if(!quiet)
				System.out.println(sectionText);
		} else {
			/* remove last section. */
			sections.pop();
			if(!quiet) {
				System.out.printf(endText, lap());
				System.out.println("\n" + sectionText);
			} else {
				lap();
			}
		}

		/* push the new one. */
		sections.push(sectionText);
	}
	
	public static void section0(String endText, String sectionText) {
		if(sections.isEmpty()) {
			lap();
			System.out.println(sectionText);
		} else {
			/* remove last section. */
			sections.pop();
			System.out.printf(endText, lap());
			System.out.println("\n" + sectionText);
		}

		/* push the new one. */
		sections.push(sectionText);
	}
	
	private static void section(String text) {
		section0("...took %fs.%n", text);
	}
	
//	public static void main2(String[] args) throws IOException {
//		cfgs = new HashMap<>();
//		sections = new LinkedList<>();
//		/* if(args.length < 1) {
//			System.err.println("Usage: <rev:int>");
//			System.exit(1);
//			return;
//		} */
//		
//		File f = new File("res/allatori.jar");
//		
//		section("Preparing to run on " + f.getAbsolutePath());
//		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
//		dl.download();
//		
//		section("Building jar class hierarchy.");
//		ClassTree tree = new ClassTree(dl.getJarContents().getClassContents());
//		
//		section("Initialising context.");
//
//		InvocationResolver resolver = new InvocationResolver(tree);
//		IContext cxt = new IContext() {
//			@Override
//			public ClassTree getClassTree() {
//				return tree;
//			}
//
//			@Override
//			public ControlFlowGraph getNonNull(MethodNode m) {
//				if(cfgs.containsKey(m)) {
//					return cfgs.get(m);
//				} else {
//					ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
//					cfgs.put(m, cfg);
//					return cfg;
//				}
//			}
//
//			@Override
//			public Set<MethodNode> keySet() {
//				return cfgs.keySet();
//			}
//
//			@Override
//			public InvocationResolver getInvocationResolver() {
//				return resolver;
//			}
//
//			@Override
//			public ApplicationClassSource getApplication() {
//				throw new UnsupportedOperationException();
//			}
//		};
//		
//		section("Expanding callgraph and generating cfgs.");
////		CallTracer tracer = new IRCallTracer(cxt) {
////			@Override
////			protected void processedInvocation(MethodNode caller, MethodNode callee, Expr call) {
////				/* the cfgs are generated by calling Icontext.getCFGS().getIR()
////				 * in IRCallTracer.traceImpl(). */
////			}
////		};
////		for(MethodNode m : findEntries(tree)) {
////			tracer.trace(m);
////		}
//		
//		for(ClassNode cn : dl.getJarContents().getClassContents()) {
//			for(MethodNode m : cn.methods) {
//				cxt.getCFGS().getIR(m);
//			}
//		}
//		
//		section0("...generated " + cfgs.size() + " cfgs in %fs.%n", "Preparing to transform.");
//		
//		PassGroup masterGroup = new PassGroup("MasterController");
//		for(IPass p : getTransformationPasses()) {
//			masterGroup.add(p);
//		}
//		run(cxt, masterGroup);
//			
//
////		for(Entry<MethodNode, ControlFlowGraph> e : cfgs.entrySet()) {
////			MethodNode mn = e.getKey();
////			ControlFlowGraph cfg = e.getValue();
////			
////			if(mn.toString().equals("a.akt(Lx;I)V")) {
////				BufferedWriter bw = new BufferedWriter(new FileWriter(new File("C:/Users/Bibl/Desktop/test224.txt")));
////				bw.write(cfg.toString());
////				bw.close();
////			}
////			
////		}
//		
//		section("Retranslating SSA IR to standard flavour.");
//		for(Entry<MethodNode, ControlFlowGraph> e : cfgs.entrySet()) {
//			MethodNode mn = e.getKey();
//			ControlFlowGraph cfg = e.getValue();
//			
//			BoissinotDestructor.leaveSSA(cfg);
//			cfg.getLocals().realloc(cfg);
//			ControlFlowGraphDumper.dump(cfg, mn);
//		}
//		
//		section("Rewriting jar.");
//		JarDumper dumper = new CompleteResolvingJarDumper(dl.getJarContents());
//		dumper.dump(new File("out/allatori_out.jar"));
//		
//		section("Finished.");
//	}
	
	interface I5 {
		
	}
	interface I4 extends I5 {
		void m();
	}
	interface I3 extends I5 {
		void m();
	}
	interface I2 extends I3 {
		@Override
		void m();
	}
	interface I6 extends I4 {
		@Override
		void m();
	}
	interface I7 extends I2 {
		@Override
		void m();
	}
	interface I1 extends I4 {
		@Override
		void m();
	}
	interface I8 extends I6, I1 {
		@Override
		void m();
	}
	interface E extends I5 {
		void m();
	}
	class A implements I1, I2 {
		@Override
		public void m() {
		}
	}
	class B extends A implements I6 {
		@Override
		public void m() {
		}
	}
	class C extends A implements I7, E {
		@Override
		public void m() {
		}
	}
	class D extends B implements I8, I7 {
		@Override
		public void m() {
		}
	}
	
	public static void main1234(String[] args) throws Throwable {/*
		Class<?>[] cls = new Class<?>[] {
			I5.class, I4.class, I3.class, I2.class, I6.class,
			I7.class, I1.class, I8.class, A.class, B.class,
			C.class, D.class, E.class
		};
		
		Set<ClassNode> cns = new HashSet<>();
		for(Class<?> c : cls) {
			ClassReader cr = new ClassReader(c.getName());
			ClassNode cn = new ClassNode();
			cr.accept(cn, 0);
			cns.add(cn);
		}
		
		ApplicationClassSource app = new ApplicationClassSource("app", cns);
		app.addLibraries(new InstalledRuntimeClassSource(app));
		
		InvocationResolver ir = new InvocationResolver(app);
		IContext cxt = new IContext() {
			@Override
			public InvocationResolver getInvocationResolver() {
				return ir;
			}
			@Override
			public ControlFlowGraph getNonNull(MethodNode m) {
				return null;
			}
			@Override
			public ApplicationClassSource getApplication() {
				return app;
			}
			@Override
			public Set<MethodNode> keySet() {
				return null;
			}
		};
		
		Set<MethodNode> set = MethodRenamerPass.getHierarchyMethodChain(cxt, app.findClassNode("org/mapleir/Boot$A"), "m", "()V");
		List<MethodNode> list = new ArrayList<>(set);
		Collections.sort(list, new Comparator<MethodNode>() {
			@Override
			public int compare(MethodNode o1, MethodNode o2) {
				return o1.owner.name.substring(o1.owner.name.lastIndexOf('/')).compareTo(o2.owner.name.substring(o2.owner.name.lastIndexOf('/')));
			}
		});
		System.out.println(list);
	*/}
	public static void main(String[] args) throws Exception {
		/* Class<?>[] cs = new Class<?>[] {A1.class, A2.class, A.class, B1.class, C1.class, D.class, E.class, B2.class, C2.class};
		Set<ClassNode> classes = new HashSet<>();
		for(Class<?> c : cs) {
			ClassReader cr = new ClassReader(c.getName());
			ClassNode cn = new ClassNode();
			cr.accept(cn, 0);
			classes.add(cn);
		}
		
		ApplicationClassSource source = new ApplicationClassSource("main", classes);
		InvocationResolver res = new InvocationResolver(source);
		
		System.out.println(res.resolveVirtualCalls("org/mapleir/Boot$A", "call", "()V"));
		
		if("".equals(""))
			return;*/
		
		sections = new LinkedList<>();
		logging = true;
		/* if(args.length < 1) {
			System.err.println("Usage: <rev:int>");
			System.exit(1);
			return;
		} */
		
		File f = locateRevFile(135);
		
		// section("Preparing to run on " + f.getAbsolutePath());
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
		dl.download();
		
//		System.out.println("loading rt");
		section("loading rt");
		
		section("test");
		String name = f.getName().substring(0, f.getName().length() - 4);
		
//		{
//			SingleJarDownloader<ClassNode> dl2 = new SingleJarDownloader<>(new JarInfo(new File("res/rt.jar")));
//			dl2.download();
//			ApplicationClassSource app = new ApplicationClassSource("rt", dl2.getJarContents().getClassContents());
//			app.addLibraries(new InstalledRuntimeClassSource(app));
//			app.getStructures();
//			System.out.println(app.getStructures());
//			BufferedReader br = new BufferedReader(new FileReader(new File("out/tree.json")));
//			String l = br.readLine();
//			br.close();
//
//			PreLoadedStructures str = new Gson().fromJson(l, PreLoadedStructures.class);
//			section("done");
//		}
		
//		final int outerIters = 20;
//		final int innerIters = 1000;
//		long total = 0;
//
//		for(int j=0; j < outerIters; j++) {
//			long n = System.nanoTime();
//
//			int k = 0;
//			for(int i=0; i < innerIters; i++) {
//				ApplicationClassSource app = new ApplicationClassSource(name, dl.getJarContents().getClassContents());
//				 InstalledRuntimeClassSource jre = new InstalledRuntimeClassSource(app);
//				app.addLibraries(jre);
////				k = app.getStructures().size();
//			}
//			double t = (double)(System.nanoTime() - n) / (double)innerIters;
//			total += System.nanoTime() - n;
//			System.out.printf(" inner: %fms%n", (double)t/1000000D);
//			System.out.printf(" strucsize:%d%n", k);
//		}
//		System.out.printf("avg: %fms%n", ((double)(total/outerIters)/(double)innerIters)/1000000D);
		
		
//		if("".equals("")) {
//			return;
//		}
		// section("Building jar class hierarchy.");
		
		ApplicationClassSource app = new ApplicationClassSource(name, dl.getJarContents().getClassContents());
		InstalledRuntimeClassSource jre = new InstalledRuntimeClassSource(app);
		app.addLibraries(jre);
		section("Initialising context.");
		
		IContext cxt = new MapleDB(app);
		
//		ClassNode cn = app.findClassNode("ab");
//		MethodRenamerPass.getHierarchyMethodChain(cxt, cn, "c", "(Ljava/lang/Object;ZB)[B");
//		System.exit(0);
		
		section("Expanding callgraph and generating cfgs.");
		
		CallTracer tracer = new IRCallTracer(cxt) {
			@Override
			protected void processedInvocation(MethodNode caller, MethodNode callee, Expr call) {
				/* the cfgs are generated by calling Icontext.getCFGS().getIR()
				 * in IRCallTracer.traceImpl(). */
			}
		};
		for(MethodNode m : findEntries(app)) {
			tracer.trace(m);
		}
		// System.out.println(new SimpleDfs<>(cxt.getApplication().getStructures(), cxt.getApplication().findClass("java/lang/Object").node, SimpleDfs.REVERSE | SimpleDfs.PRE | SimpleDfs.POST).getPreOrder());
		
		{
//			ClassTree tree = cxt.getApplication().getStructures();
//			DotConfiguration<ClassTree, ClassNode, InheritanceEdge> config = new BasicDotConfiguration<>(GraphType.DIRECTED);
//			DotWriter<ClassTree, ClassNode, InheritanceEdge> writer = new DotWriter<>(config, tree);
//			writer.setName("thingy22").export();
		}
		
		section0("...generated " + cxt.getCFGS().size() + " cfgs in %fs.%n", "Preparing to transform.");
		
		PassGroup masterGroup = new PassGroup("MasterController");
		for(IPass p : getTransformationPasses()) {
			masterGroup.add(p);
		}
		run(cxt, masterGroup);

		
//		for(ClassNode cn : app.iterate()) {
//			for(MethodNode m : cn.methods) {
//				if(m.toString().startsWith("bo.b(Ljava/awt/Component;)V")) {
//					ControlFlowGraph cfg = cxt.getCFGS().getIR(m);
//					
//					BoissinotDestructor.leaveSSA(cfg);
//					cfg.getLocals().realloc(cfg);
//					ControlFlowGraphDumper.dump(cfg, m);
//
//					System.out.println(cfg);
//					System.out.println(m +"::");
//					ControlFlowGraphBuilder.build(m);
//				}
//			}
//		}
		
		section("Retranslating SSA IR to standard flavour.");
		for(Entry<MethodNode, ControlFlowGraph> e : cxt.getCFGS().entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			BoissinotDestructor.leaveSSA(cfg);
			cfg.getLocals().realloc(cfg);
			ControlFlowGraphDumper.dump(cfg, mn);
		}
		
		section("Rewriting jar.");
		JarDumper dumper = new CompleteResolvingJarDumper(dl.getJarContents(), app) {
			@Override
			public int dumpResource(JarOutputStream out, String name, byte[] file) throws IOException {
				if(name.startsWith("META-INF")) {
					System.out.println(" ignore " + name);
					return 0;
				}
				return super.dumpResource(out, name, file);
			}
		};
		dumper.dump(new File("out/osb.jar"));
		
		section("Finished.");
	}
	
	private static void run(IContext cxt, PassGroup group) {
		group.accept(cxt, null, new ArrayList<>());
	}
	
	private static IPass[] getTransformationPasses() {
		return new IPass[] {
				new CallgraphPruningPass(),
//				new ConcreteStaticInvocationPass(),
				new MethodRenamerPass(),
//				new ConstantParameterPass()
//				new ClassRenamerPass(),
//				new FieldRenamerPass(),
//				new ConstantExpressionReorderPass(),
//				new FieldRSADecryptionPass(),
//				new PassGroup("Interprocedural Optimisations")
//					.add(new ConstantParameterPass())
				new ConstantExpressionReorderPass(),
//				new FieldRSADecryptionPass(),
				new ConstantParameterPass(),
				new ConstantExpressionEvaluatorPass(),
				new DeadCodeEliminationPass()
//				new PassGroup("Interprocedural Optimisations")
				
		};
	}
	
	static File locateRevFile(int rev) {
		return new File("res/gamepack" + rev + ".jar");
	}
	
	private static Set<MethodNode> findEntries(ApplicationClassSource source) {
		Set<MethodNode> set = new HashSet<>();
		/* searches only app classes. */
		for(ClassNode cn : source.iterate())  {
			for(MethodNode m : cn.methods) {
				if(m.name.length() > 2 && !m.name.equals("<init>")) {
					set.add(m);
				}
			}
		}
		return set;
	}
}