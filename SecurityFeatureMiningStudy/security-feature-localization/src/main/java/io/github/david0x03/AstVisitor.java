package io.github.david0x03;

import org.eclipse.jdt.core.dom.*;

/**
 * Traverses the AST to locate and register relevant nodes for feature extraction.
 * This visitor identifies API calls and annotations in the source code and records their details.
 */
public class AstVisitor extends ASTVisitor {

    private final ParsedFile fd;

    /**
     * Initializes the AST visitor with a parsed file for recording located nodes.
     *
     * @param fd The parsed file where extracted API calls and missing bindings are recorded.
     */
    public AstVisitor(ParsedFile fd) {
        this.fd = fd;
    }

    @Override
    public boolean visit(ClassInstanceCreation node) {
        var type = ApiCall.APICallType.ClassInstanceCreation;

        ITypeBinding binding = node.resolveTypeBinding();
        if (binding == null) {
            registerMissingBinding(type, node);
            return super.visit(node);
        }

        var className = binding.getQualifiedName();
        var qualifiedName = className;

        fd.addApiCall(node, qualifiedName, type);
        return super.visit(node);
    }

    @Override
    public boolean visit(MethodInvocation node) {
        var type = ApiCall.APICallType.MethodInvocation;

        IMethodBinding binding = node.resolveMethodBinding();
        if (binding == null) {
            registerMissingBinding(type, node);
            return super.visit(node);
        }

        var className = binding.getDeclaringClass().getQualifiedName();
        var qualifiedName = className + "." + node.getName();

        fd.addApiCall(node, qualifiedName, type);
        return super.visit(node);
    }

    // Instance field access
    @Override
    public boolean visit(FieldAccess node) {
        var type = ApiCall.APICallType.FieldAccess;

        IVariableBinding binding = node.resolveFieldBinding();
        if (binding == null) {
            registerMissingBinding(type, node);
            return super.visit(node);
        }

        if (binding.getDeclaringClass() == null) {
            return super.visit(node);
        }

        var className = binding.getDeclaringClass().getQualifiedName();
        var qualifiedName = className + "." + binding.getName();

        fd.addApiCall(node, qualifiedName, type);
        return super.visit(node);
    }

    // Static field access
    @Override
    public boolean visit(QualifiedName node) {
        var type = ApiCall.APICallType.FieldAccess;

        IBinding binding = node.resolveBinding();
        if (binding == null) {
            registerMissingBinding(type, node);
            return super.visit(node);
        }

        if (!(binding instanceof IVariableBinding) || ((IVariableBinding) binding).getDeclaringClass() == null) {
            return super.visit(node);
        }

        var className = ((IVariableBinding) binding).getDeclaringClass().getQualifiedName();
        var qualifiedName = className + "." + binding.getName();

        fd.addApiCall(node, qualifiedName, type);
        return super.visit(node);
    }

    @Override
    public boolean visit(MarkerAnnotation node) {
        visitAnnotation(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(SingleMemberAnnotation node) {
        visitAnnotation(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(NormalAnnotation node) {
        visitAnnotation(node);
        return super.visit(node);
    }

    // Handles all the different annotation types
    private void visitAnnotation(Annotation node) {
        var type = ApiCall.APICallType.Annotation;

        IAnnotationBinding binding = node.resolveAnnotationBinding();
        if (binding == null) {
            registerMissingBinding(type, node);
            return;
        }

        var qualifiedName = binding.getAnnotationType().getQualifiedName();
        fd.addApiCall(node, qualifiedName, type);
    }

    /**
     * Registers a missing binding for a node that could not be resolved.
     *
     * @param type The type of API call associated with the node.
     * @param node The AST node that could not be resolved.
     */
    private void registerMissingBinding(ApiCall.APICallType type, ASTNode node) {
        var missingBinding = new MissingBinding(type, node, fd.getFilePath());
        fd.addMissingBinding(missingBinding);
    }
}