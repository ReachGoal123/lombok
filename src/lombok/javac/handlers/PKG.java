package lombok.javac.handlers;

import java.lang.reflect.Modifier;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;

import lombok.AccessLevel;
import lombok.core.TransformationsUtil;
import lombok.core.AST.Kind;
import lombok.javac.JavacAST;

class PKG {
	private PKG() {}
	
	static String toGetterName(JCVariableDecl field) {
		CharSequence fieldName = field.name;
		
		boolean isBoolean = field.vartype.toString().equals("boolean");
		
		return TransformationsUtil.toGetterName(fieldName, isBoolean);
	}
	
	static String toSetterName(JCVariableDecl field) {
		CharSequence fieldName = field.name;
		
		return TransformationsUtil.toSetterName(fieldName);
	}
	
	enum MethodExistsResult {
		NOT_EXISTS, EXISTS_BY_USER, EXISTS_BY_LOMBOK;
	}
	
	static MethodExistsResult methodExists(String methodName, JavacAST.Node node) {
		while ( node != null && !(node.get() instanceof JCClassDecl) ) {
			node = node.up();
		}
		
		if ( node != null && node.get() instanceof JCClassDecl ) {
			for ( JCTree def : ((JCClassDecl)node.get()).defs ) {
				if ( def instanceof JCMethodDecl ) {
					if ( ((JCMethodDecl)def).name.contentEquals(methodName) ) {
						JavacAST.Node existing = node.getNodeFor(def);
						if ( existing == null || !existing.isHandled() ) return MethodExistsResult.EXISTS_BY_USER;
						return MethodExistsResult.EXISTS_BY_LOMBOK;
					}
				}
			}
		}
		
		return MethodExistsResult.NOT_EXISTS;
	}
	
	static MethodExistsResult constructorExists(JavacAST.Node node) {
		while ( node != null && !(node.get() instanceof JCClassDecl) ) {
			node = node.up();
		}
		
		if ( node != null && node.get() instanceof JCClassDecl ) {
			for ( JCTree def : ((JCClassDecl)node.get()).defs ) {
				if ( def instanceof JCMethodDecl ) {
					if ( ((JCMethodDecl)def).name.contentEquals("<init>") ) {
						if ( (((JCMethodDecl)def).mods.flags & Flags.GENERATEDCONSTR) != 0 ) continue;
						JavacAST.Node existing = node.getNodeFor(def);
						if ( existing == null || !existing.isHandled() ) return MethodExistsResult.EXISTS_BY_USER;
						return MethodExistsResult.EXISTS_BY_LOMBOK;
					}
				}
			}
		}
		
		return MethodExistsResult.NOT_EXISTS;
	}
	
	static int toJavacModifier(AccessLevel accessLevel) {
		switch ( accessLevel ) {
		case MODULE:
		case PACKAGE:
			return 0;
		default:
		case PUBLIC:
			return Modifier.PUBLIC;
		case PRIVATE:
			return Modifier.PRIVATE;
		case PROTECTED:
			return Modifier.PROTECTED;
		}
	}
	
	static void injectMethod(JavacAST.Node typeNode, JCMethodDecl method) {
		JCClassDecl type = (JCClassDecl) typeNode.get();
		
		if ( method.getName().contentEquals("<init>") ) {
			//Scan for default constructor, and remove it.
			int idx = 0;
			for ( JCTree def : type.defs ) {
				if ( def instanceof JCMethodDecl ) {
					if ( (((JCMethodDecl)def).mods.flags & Flags.GENERATEDCONSTR) != 0 ) {
						JavacAST.Node tossMe = typeNode.getNodeFor(def);
						if ( tossMe != null ) tossMe.up().removeChild(tossMe);
						type.defs = addAllButOne(type.defs, idx);
						break;
					}
				}
				idx++;
			}
		}
		
		type.defs = type.defs.append(method);
		
		typeNode.add(method, Kind.METHOD).recursiveSetHandled();
	}
	
	private static List<JCTree> addAllButOne(List<JCTree> defs, int idx) {
		List<JCTree> out = List.nil();
		int i = 0;
		for ( JCTree def : defs ) {
			if ( i++ != idx ) out = out.append(def);
		}
		return out;
	}
}
