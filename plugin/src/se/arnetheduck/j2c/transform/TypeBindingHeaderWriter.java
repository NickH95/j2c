package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class TypeBindingHeaderWriter {
	private final IPath root;
	private final ITypeBinding type;
	private final Transformer ctx;

	public TypeBindingHeaderWriter(IPath root, Transformer ctx,
			ITypeBinding type) {
		this.root = root;
		this.ctx = ctx;
		this.type = type;
	}

	public void write() throws Exception {
		printClass(type);
	}

	private void printClass(ITypeBinding tb) throws Exception {
		ctx.headers.add(tb);

		for (ITypeBinding nb : tb.getDeclaredTypes()) {
			printClass(nb);
		}

		PrintWriter pw = TransformUtil.openHeader(root, tb);

		List<ITypeBinding> bases = TransformUtil.getBases(tb,
				ctx.resolve(Object.class));

		for (ITypeBinding b : bases) {
			pw.println(TransformUtil.include(b));
		}

		pw.println();

		pw.print("class ");
		pw.println(TransformUtil.qualifiedCName(tb));

		String sep = ": public ";
		for (ITypeBinding b : bases) {
			ctx.hardDep(b);

			pw.print(TransformUtil.indent(1));
			pw.print(sep);
			sep = ", public ";
			pw.print(TransformUtil.virtual(b));
			pw.println(TransformUtil.relativeCName(b, tb));
		}

		pw.println("{");

		if (tb.getSuperclass() != null) {
			pw.print(TransformUtil.indent(1));
			pw.print("typedef ");
			pw.print(TransformUtil.relativeCName(tb.getSuperclass(), tb));
			pw.println(" super;");
		}

		String lastAccess = null;
		for (IVariableBinding vb : tb.getDeclaredFields()) {
			lastAccess = TransformUtil.printAccess(pw, vb.getModifiers(),
					lastAccess);
			printField(pw, vb);
		}

		pw.println();

		Set<String> usings = new HashSet<String>();

		for (IMethodBinding mb : tb.getDeclaredMethods()) {
			lastAccess = TransformUtil.printAccess(pw, mb.getModifiers(),
					lastAccess);
			printMethod(pw, tb, mb, usings);
		}

		if (tb.getQualifiedName().equals("java.lang.Object")) {
			pw.println("public:");
			pw.print(TransformUtil.indent(1));
			pw.println("virtual ~Object() { }");
		}

		printBridgeMethods(pw, tb);

		pw.println("};");

		if (tb.getQualifiedName().equals("java.lang.String")) {
			pw.println("java::lang::String *join(java::lang::String *lhs, java::lang::String *rhs);");
			for (String type : new String[] { "java::lang::Object *", "bool ",
					"int8_t ", "wchar_t ", "double ", "float ", "int32_t ",
					"int64_t ", "int16_t " }) {
				pw.println("java::lang::String *join(java::lang::String *lhs, "
						+ type + "rhs);");
				pw.println("java::lang::String *join(" + type
						+ "lhs, java::lang::String *rhs);");
			}

			pw.println("java::lang::String *lit(const wchar_t *chars);");
		}

		pw.close();
	}

	private void printField(PrintWriter pw, IVariableBinding vb) {
		ctx.softDep(vb.getType());

		pw.print(TransformUtil.indent(1));

		Object constant = TransformUtil.constantValue(vb);
		pw.print(TransformUtil.fieldModifiers(vb.getModifiers(), true,
				constant != null));

		pw.print(TransformUtil.relativeCName(vb.getType(),
				vb.getDeclaringClass()));
		pw.print(" ");

		pw.print(TransformUtil.ref(vb.getType()));
		pw.print(vb.getName());
		pw.print("_");

		if (constant != null) {
			pw.print(" = ");
			pw.print(constant);
		}

		pw.println(";");
	}

	private void printMethod(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, Set<String> usings) throws Exception {
		if (tb.isInterface() && !baseHasSame(mb, tb)) {
			return;
		}

		if (Modifier.isPrivate(mb.getModifiers())) {
			pw.println("/* private: xxx " + mb.getName() + "(...) */");
			return;
		}

		pw.print(TransformUtil.indent(1));

		if (!mb.isConstructor()) {
			ITypeBinding rt = mb.getReturnType();
			ctx.softDep(rt);

			pw.print(TransformUtil.methodModifiers(mb.getModifiers(),
					tb.getModifiers()));

			pw.print(TransformUtil.relativeCName(rt, tb));
			pw.print(" ");
			pw.print(TransformUtil.ref(rt));
		}

		pw.print(mb.isConstructor() ? TransformUtil.name(tb) : TransformUtil
				.keywords(mb.getMethodDeclaration().getName()));

		pw.print("(");
		for (int i = 0; i < mb.getParameterTypes().length; ++i) {
			if (i > 0)
				pw.print(", ");

			ITypeBinding pb = mb.getParameterTypes()[i];
			ctx.softDep(pb);

			pw.print(TransformUtil.relativeCName(pb, tb));
			pw.print(" ");
			pw.print(TransformUtil.ref(pb));
			pw.print("a" + i);
		}

		pw.print(")");
		if (Modifier.isAbstract(mb.getModifiers())) {
			pw.print(" = 0");
		}

		pw.println(";");

		String using = TransformUtil.methodUsing(mb);
		if (using != null) {
			if (usings.add(using)) {
				pw.print(TransformUtil.indent(1));
				pw.println(using);
			}
		}
	}

	void printBridgeMethods(PrintWriter pw, ITypeBinding tb) throws Exception {
		// ITypeBinding doesn't support synthetic methods but it seems IType
		// does, see:
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=369848
		IType t = (IType) tb.getJavaElement();

		for (IMethod m : t.getMethods()) {
			if (Flags.isBridge(m.getFlags())
					&& !isReturnCovariant(m, t.getMethods())) {
				pw.print(TransformUtil.indent(1));
				pw.print(TransformUtil.cname(Signature.toString(m
						.getReturnType())));

				pw.print(" ");
				pw.print(ref(m.getReturnType()));
				pw.print(m.getElementName());
				pw.print("(");
				String sep = "";
				for (ILocalVariable p : m.getParameters()) {
					pw.print(sep);
					sep = ", ";
					pw.print(TransformUtil.cname(Signature.toString(p
							.getTypeSignature())));
					pw.print(" ");
					pw.print(ref(p.getTypeSignature()));
					pw.print(p.getElementName());

				}

				pw.println(");");
			}
		}
	}

	private String ref(String typeSignature) {
		switch (typeSignature) {
		case Signature.SIG_BOOLEAN:
		case Signature.SIG_BYTE:
		case Signature.SIG_CHAR:
		case Signature.SIG_DOUBLE:
		case Signature.SIG_FLOAT:
		case Signature.SIG_INT:
		case Signature.SIG_LONG:
		case Signature.SIG_SHORT:
		case Signature.SIG_VOID:
			return "";
		}

		return "*";
	}

	/**
	 * Return true if the method is a covariant return type bridge.
	 */
	private boolean isReturnCovariant(IMethod m, IMethod[] methods)
			throws JavaModelException {
		for (IMethod x : methods) {
			if (x.getElementName().equals(m.getElementName())
					&& Arrays.equals(m.getParameterTypes(),
							x.getParameterTypes())
					&& !m.getReturnType().equals(x.getReturnType())) {
				return true;
			}
		}
		return false;
	}

	/** Check if super-interface has the same method already */
	private static boolean baseHasSame(IMethodBinding mb, ITypeBinding tb) {
		for (ITypeBinding ib : tb.getInterfaces()) {
			for (IMethodBinding imb : ib.getDeclaredMethods()) {
				if (mb.overrides(imb)) {
					return false;
				}
			}

			if (!baseHasSame(mb, ib)) {
				return false;
			}
		}

		return true;
	}
}
