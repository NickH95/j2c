package se.arnetheduck.j2c.transform;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.text.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public final class TransformUtil {
	private static final String PUBLIC = "public:";
	private static final String PROTECTED = "protected:";
	private static final String PACKAGE = "public: /* package */";
	private static final String PRIVATE = "private:";

	public static String cname(String jname) {
		return jname.replace(".", "::");
	}

	public static String qualifiedCName(ITypeBinding tb) {
		IPackageBinding pkg = tb.isArray() ? tb.getElementType().getPackage()
				: tb.getErasure().getPackage();
		return cname(pkg == null ? name(tb) : (pkg.getName() + "." + name(tb)));
	}

	public static String name(ITypeBinding tb) {
		if (tb.isArray()) {
			return name(tb.getComponentType()) + "Array";
		}

		if (tb.isAnonymous()) {
			String c = tb.getDeclaringClass() == null ? "c" + tb.hashCode()
					: name(tb.getDeclaringClass());
			String m = tb.getDeclaringMethod() == null ? "m" + tb.hashCode()
					: tb.getDeclaringMethod().getName();

			return c + "_" + m;
		}

		if (tb.isNested()) {
			return name(tb.getDeclaringClass()) + "_"
					+ tb.getErasure().getName();
		}

		if (tb.isPrimitive()) {
			return primitive(tb.getName());
		}

		return tb.getErasure().getName();
	}

	public static String[] packageName(ITypeBinding tb) {
		IPackageBinding pkg = tb.isArray() ? tb.getElementType().getPackage()
				: tb.getPackage();
		return pkg == null ? new String[0] : pkg.getNameComponents();
	}

	public static String qualifiedName(ITypeBinding tb) {
		IPackageBinding pkg = tb.isArray() ? tb.getElementType().getPackage()
				: tb.getPackage();
		return pkg == null ? name(tb) : (pkg.getName() + "." + name(tb));
	}

	public static String include(ITypeBinding tb) {
		return include(headerName(tb));
	}

	public static String include(IPackageBinding pb) {
		return include(headerName(pb));
	}

	public static String include(String s) {
		return "#include \"" + s + "\"";
	}

	public static String include(Type t) {
		return include(headerName(t));
	}

	public static String headerName(ITypeBinding tb) {
		return qualifiedName(tb) + ".h";
	}

	public static String headerName(IPackageBinding pb) {
		return pb.getName() + ".h";
	}

	public static String headerName(Type t) {
		return headerName(t.resolveBinding());
	}

	public static String implName(ITypeBinding tb) {
		return qualifiedName(tb) + ".cpp";
	}

	public static Object constantValue(VariableDeclarationFragment node) {
		IVariableBinding vb = node.resolveBinding();
		ITypeBinding tb = vb.getType();

		Expression initializer = node.getInitializer();
		Object v = initializer == null ? null : initializer
				.resolveConstantExpressionValue();
		if (isConstExpr(tb) && Modifier.isStatic(vb.getModifiers())
				&& Modifier.isFinal(vb.getModifiers()) && v != null) {
			return checkConstant(v);
		}

		return null;
	}

	public static Object constantValue(IVariableBinding vb) {
		ITypeBinding tb = vb.getType();

		Object v = vb.getConstantValue();
		if (isConstExpr(tb) && Modifier.isStatic(vb.getModifiers())
				&& Modifier.isFinal(vb.getModifiers()) && v != null) {
			return checkConstant(v);
		}

		return null;
	}

	public static Object checkConstant(Object cv) {
		if (cv instanceof Character) {
			char ch = (char) cv;
			if ((ch < ' ') || ch > 127) {
				return (int) ch;
			}

			return "'" + ch + "'";
		} else if (cv instanceof Long) {
			return cv + "ll";
		}

		return cv;
	}

	private static boolean isConstExpr(ITypeBinding tb) {
		return tb.getName().equals("int") || tb.getName().equals("char")
				|| tb.getName().equals("long") || tb.getName().equals("byte")
				|| tb.getName().equals("short");
	}

	public static String fieldModifiers(int modifiers, boolean header,
			boolean hasInitializer) {
		String ret = "";
		if (header && Modifier.isStatic(modifiers)) {
			ret += "static ";
		}

		if (Modifier.isFinal(modifiers) && hasInitializer) {
			ret += "const ";
		}

		return ret;
	}

	public static String methodModifiers(int modifiers, int typeModifiers) {
		if (Modifier.isStatic(modifiers)) {
			return "static ";
		}

		if (Modifier.isFinal(modifiers | typeModifiers)
				|| Modifier.isPrivate(modifiers)) {
			return "";
		}

		return "virtual ";
	}

	public static String variableModifiers(int modifiers) {
		return fieldModifiers(modifiers, false, false);
	}

	public static String typeArguments(Collection<Type> parameters) {
		if (parameters.isEmpty()) {
			return "";
		}

		return "/* <" + toCSV(parameters) + "> */";
	}

	public static String typeParameters(Collection<TypeParameter> parameters) {
		if (parameters.isEmpty()) {
			return "";
		}

		return "/* <" + toCSV(parameters) + "> */";
	}

	public static String throwsDecl(Collection<Name> parameters) {
		if (parameters.isEmpty()) {
			return "";
		}

		return "/*  throws(" + toCSV(parameters) + ") */";
	}

	public static String annotations(Collection<Annotation> annotations) {
		if (annotations.isEmpty()) {
			return "";
		}

		return "/* " + toCSV(annotations, " ") + " */";
	}

	public static String toCSV(Collection<?> c) {
		return toCSV(c, ", ");
	}

	public static String toCSV(Collection<?> c, String separator) {
		StringBuilder builder = new StringBuilder();
		String s = "";
		for (Object o : c) {
			builder.append(s);
			s = separator;
			builder.append(o);
		}

		return builder.toString();
	}

	public static String primitive(String primitve) {
		return primitive(PrimitiveType.toCode(primitve));
	}

	public static String primitive(PrimitiveType.Code code) {
		if (code == PrimitiveType.BOOLEAN) {
			return "bool";
		} else if (code == PrimitiveType.BYTE) {
			return "int8_t";
		} else if (code == PrimitiveType.CHAR) {
			return "wchar_t";
		} else if (code == PrimitiveType.DOUBLE) {
			return "double";
		} else if (code == PrimitiveType.FLOAT) {
			return "float";
		} else if (code == PrimitiveType.INT) {
			return "int32_t";
		} else if (code == PrimitiveType.LONG) {
			return "int64_t";
		} else if (code == PrimitiveType.SHORT) {
			return "int16_t";
		} else if (code == PrimitiveType.VOID) {
			return "void";
		} else {
			throw new RuntimeException("Unknown primitive type");
		}
	}

	public static boolean addDep(ITypeBinding dep, Collection<ITypeBinding> deps) {
		if (dep == null) {
			return false;
		}

		if (dep.isNullType()) {
			return false;
		}

		if (dep.isPrimitive()) {
			return false;
		}

		dep = dep.getErasure();

		return deps.add(dep);
	}

	public static String printAccess(PrintWriter out, int access,
			String lastAccess) {
		if ((access & Modifier.PRIVATE) > 0) {
			if (!PRIVATE.equals(lastAccess)) {
				lastAccess = PRIVATE;
				out.println(lastAccess);
			}
		} else if ((access & Modifier.PROTECTED) > 0) {
			if (!PROTECTED.equals(lastAccess)) {
				lastAccess = PROTECTED;
				out.println(lastAccess);
			}
		} else if ((access & Modifier.PUBLIC) > 0) {
			if (!PUBLIC.equals(lastAccess)) {
				lastAccess = PUBLIC;
				out.println(lastAccess);
			}
		} else {
			if (!PACKAGE.equals(lastAccess)) {
				lastAccess = PACKAGE;
				out.println(lastAccess);
			}
		}

		return lastAccess;
	}

	public static String ref(ITypeBinding tb) {
		return tb.isPrimitive() ? "" : "*";
	}

	public static String ref(Type t) {
		return t.isPrimitiveType() ? "" : "*";
	}

	private static Collection<String> keywords = Arrays.asList("delete",
			"register", "union");

	/** Filter out C++ keywords */
	public static String keywords(String name) {
		if (keywords.contains(name)) {
			return name + "_";
		}

		return name;
	}

	public static String methodUsing(IMethodBinding mb) {
		ITypeBinding tb = mb.getDeclaringClass();

		boolean needsUsing = false;
		for (IMethodBinding b : methods(tb.getSuperclass(), mb.getName())) {
			if (mb.getParameterTypes().length != b.getParameterTypes().length) {
				needsUsing = true;
				break;
			}

			for (int i = 0; i < mb.getParameterTypes().length && !needsUsing; ++i) {
				if (!mb.getParameterTypes()[i].getQualifiedName().equals(
						b.getParameterTypes()[i].getQualifiedName())) {
					needsUsing = true;
				}
			}

			if (needsUsing) {
				break;
			}
		}

		if (needsUsing) {
			return "using super::" + mb.getName() + ";";
		}

		return null;
	}

	public static List<IMethodBinding> methods(ITypeBinding tb, String name) {
		if (tb == null) {
			return Collections.emptyList();
		}

		List<IMethodBinding> ret = new ArrayList<IMethodBinding>();
		for (IMethodBinding mb : tb.getDeclaredMethods()) {
			if (mb.getName().equals(name)) {
				ret.add(mb);
			}
		}

		ret.addAll(methods(tb.getSuperclass(), name));
		return ret;
	}

	public static String indent(int n) {
		String ret = "";
		for (int i = 0; i < n; i++)
			ret += "    ";
		return ret;
	}

	public static String inherit(ITypeBinding tb) {
		if (tb.isInterface()
				|| tb.getQualifiedName().equals(Object.class.getName())) {
			return "virtual ";
		}

		return "";
	}

	public static PrintWriter openHeader(IPath root, ITypeBinding tb)
			throws IOException {

		FileOutputStream fos = new FileOutputStream(root.append(
				TransformUtil.headerName(tb)).toFile());

		PrintWriter pw = new PrintWriter(fos);

		pw.println("// Generated from " + tb.getJavaElement().getPath());

		pw.println("#pragma once");
		pw.println();

		pw.println(TransformUtil.include("forward.h"));
		pw.println();

		return pw;
	}

	public static List<ITypeBinding> getBases(ITypeBinding tb,
			ITypeBinding object) {
		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();

		addDep(tb.getSuperclass(), ret);

		for (ITypeBinding ib : tb.getInterfaces()) {
			addDep(ib, ret);
		}

		if (ret.isEmpty()
				&& !tb.getQualifiedName().equals(Object.class.getName())) {
			ret.add(object);
		}

		return ret;
	}

	public static List<ITypeBinding> getBases(AST ast, ITypeBinding tb) {
		return getBases(tb, ast.resolveWellKnownType(Object.class.getName()));
	}
}
