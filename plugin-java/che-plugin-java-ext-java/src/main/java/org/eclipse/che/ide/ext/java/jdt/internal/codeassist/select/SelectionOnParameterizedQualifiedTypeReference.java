/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.ext.java.jdt.internal.codeassist.select;

import org.eclipse.che.ide.ext.java.jdt.core.compiler.CharOperation;
import org.eclipse.che.ide.ext.java.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference;
import org.eclipse.che.ide.ext.java.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.che.ide.ext.java.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.che.ide.ext.java.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.che.ide.ext.java.jdt.internal.compiler.lookup.TypeBinding;

public class SelectionOnParameterizedQualifiedTypeReference extends ParameterizedQualifiedTypeReference {
	public SelectionOnParameterizedQualifiedTypeReference(char[][] previousIdentifiers, char[] selectionIdentifier, TypeReference[][] typeArguments, TypeReference[] assistTypeArguments, long[] positions) {
		super(
			CharOperation.arrayConcat(previousIdentifiers, selectionIdentifier),
			typeArguments,
			0,
			positions);
		int length =  this.typeArguments.length;
		System.arraycopy(this.typeArguments, 0, this.typeArguments = new TypeReference[length + 1][], 0, length);
		this.typeArguments[length] = assistTypeArguments;
	}

	public TypeBinding resolveType(BlockScope scope, boolean checkBounds) {
		super.resolveType(scope, checkBounds);
		//// removed unnecessary code to solve bug 94653
		//if(this.resolvedType != null && this.resolvedType.isRawType()) {
		//	ParameterizedTypeBinding parameterizedTypeBinding = scope.createParameterizedType(((RawTypeBinding)this.resolvedType).type, new TypeBinding[0], this.resolvedType.enclosingType());
		//	throw new SelectionNodeFound(parameterizedTypeBinding);
		//}
		throw new SelectionNodeFound(this.resolvedType);
	}

	public TypeBinding resolveType(ClassScope scope) {
		super.resolveType(scope);
		//// removed unnecessary code to solve bug 94653
		//if(this.resolvedType != null && this.resolvedType.isRawType()) {
		//	ParameterizedTypeBinding parameterizedTypeBinding = scope.createParameterizedType(((RawTypeBinding)this.resolvedType).type, new TypeBinding[0], this.resolvedType.enclosingType());
		//	throw new SelectionNodeFound(parameterizedTypeBinding);
		//}
		throw new SelectionNodeFound(this.resolvedType);
	}

	public StringBuffer printExpression(int indent, StringBuffer output) {
		output.append("<SelectOnType:");//$NON-NLS-1$
		int length = this.tokens.length;
		for (int i = 0; i < length; i++) {
			if(i != 0) {
				output.append('.');
			}
			output.append(this.tokens[i]);
			TypeReference[] typeArgument = this.typeArguments[i];
			if (typeArgument != null) {
				output.append('<');
				int max = typeArgument.length - 1;
				for (int j = 0; j < max; j++) {
					typeArgument[j].print(0, output);
					output.append(", ");//$NON-NLS-1$
				}
				typeArgument[max].print(0, output);
				output.append('>');
			}

		}
		output.append('>');
		return output;
	}
}
