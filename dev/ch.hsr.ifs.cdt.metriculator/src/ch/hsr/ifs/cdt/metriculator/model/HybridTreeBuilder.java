/******************************************************************************
* Copyright (c) 2011 Institute for Software, HSR Hochschule fuer Technik 
* Rapperswil, University of applied sciences and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html 
*
* Contributors:
* 	Ueli Kunz <kunz@ideadapt.net>, Jules Weder <julesweder@gmail.com> - initial API and implementation
******************************************************************************/

package ch.hsr.ifs.cdt.metriculator.model;

import java.util.HashMap;

import org.eclipse.cdt.core.dom.ast.IBinding;

import ch.hsr.ifs.cdt.metriculator.model.nodes.AbstractNode;
import ch.hsr.ifs.cdt.metriculator.model.nodes.ILogicNode;
import ch.hsr.ifs.cdt.metriculator.model.nodes.WorkspaceNode;

public class HybridTreeBuilder extends TreeBuilder {

	private HashMap<String,AbstractNode> descendants                   = new HashMap<String,AbstractNode>();
	private HashMap<IBinding, AbstractNode> declarations               = new HashMap<IBinding, AbstractNode>();
	private HashMap<IBinding, AbstractNode> removedDeclarationBindings = new HashMap<IBinding, AbstractNode>();

	public HybridTreeBuilder(String workspace){
		root = new WorkspaceNode(workspace);
	}

	@Override
	public AbstractNode addChild(AbstractNode parent, AbstractNode child){

		AbstractNode defNode = getDefinitionForDeclarationOf(child, parent);
		if(defNode != null){
			return parent; 
		}
		
		String childsHybridId = combine(TreeBuilder.PATH_SEPARATOR, parent.getHybridId(), child.getScopeUniqueName());
		AbstractNode existing = parent.getChildBy(childsHybridId);

		if(sameNodeExists(child, existing)){
			mergeChildrenOf(child, existing);
			child = existing;
		}else{
			child.setHybridId(childsHybridId);
			child = parent.add(child);
			PreOrderTreeVisitor visitor = new PreOrderTreeVisitor() {
				@Override
				void visitNode(AbstractNode n) {
					descendants.put(n.getHybridId(), n);
				}
			};
			visitor.visit(child);
		}

		return child;
	}

	private boolean sameNodeExists(AbstractNode child, AbstractNode existing) {
		boolean sameNodeExists = false;
		if(existing != null){
			sameNodeExists = true;
			
			if(child instanceof ILogicNode && existing.getNodeInfo().hasInfos()){
				sameNodeExists = existing.getNodeInfo().equals(child.getNodeInfo());
			}
		}
		
		if(foundForwardDeclarationAssociatedWith(child) && existing != null){
			if((existing.getNodeInfo().isFunctionDeclarator() && child.getNodeInfo().isFunctionDefinition()) ||
				existing.getNodeInfo().isElaboratedTypeSpecifier() && child.getNodeInfo().isCompositeTypeSpecifier()){
				sameNodeExists = false;
			}
		}
		return sameNodeExists;
	}

	private AbstractNode getDefinitionForDeclarationOf(AbstractNode child, AbstractNode parent) {
		if(child.getNodeInfo().isElaboratedTypeSpecifier() || (child.getNodeInfo().isFunctionDeclarator() && !(parent.getNodeInfo().isFunctionDefinition()))){
			IBinding binding = child.getNodeInfo().getBinding();
			if(child.getNodeInfo().isElaboratedTypeSpecifier()){
				binding = child.getNodeInfo().getTypeBinding();
			}
			if(removedDeclarationBindings.containsKey(binding)){
				return removedDeclarationBindings.get(binding);
			}else{
				if(!child.getNodeInfo().isFriend()){
					declarations.put(binding, child);
				}
			}
		}
		return null;
	}

	private boolean foundForwardDeclarationAssociatedWith(AbstractNode child) {
		if(declarations.size() > 0){
			IBinding binding = null;
			if(child.getNodeInfo().isFunctionDefinition()){
				binding = child.getNodeInfo().getBinding();
			}
			if(child.getNodeInfo().isCompositeTypeSpecifier()){
				binding = child.getNodeInfo().getTypeBinding();
			}
			AbstractNode foundDecl = declarations.get(binding);
			if(foundDecl != null){
				declarations.remove(binding);
				removedDeclarationBindings.put(binding, child);
				foundDecl.removeFromParent();
				foundDecl = null;
				return true;
			}
		}
		return false;
	}


	private void mergeChildrenOf(AbstractNode node, AbstractNode intoParent){
		for(AbstractNode n : node.getChildren()){
			addChild(intoParent, n);
		}
	}

	public AbstractNode getChildBy(String hybridId){
		return descendants.get(hybridId);
	}
}