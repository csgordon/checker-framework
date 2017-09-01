package org.checkerframework.checker.genericeffects;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Stack;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.TreeUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;

public class GenericEffectVisitor extends BaseTypeVisitor<GenericEffectTypeFactory> {

    protected final boolean debugSpew;
    private GenericEffectLattice genericEffect;
    private GenericEffectExtension extension;
    // effStack and currentMethods should always be the same size.
    protected final Stack<Class<? extends Annotation>> effStack;
    protected final Stack<MethodTree> currentMethods;

    //fields for compiler arguments
    boolean ignoringEffects;
    boolean ignoringWarnings;
    boolean ignoringErrors;

    /**
     * Constructor that takes passes the checker to the superclass and takes in a GenericEffectExtension object.
     * The effect stack for methods and variables are set up in the constructor.
     *
     * @param checker The checker that allows the Casting Effects Checker to function.
     * @param ext     An GenericEffectExtension object that provides the developer with more functions dealing with specific tree nodes.
     */
    public GenericEffectVisitor(BaseTypeChecker checker, GenericEffectExtension ext) {
        super(checker);
        assert (checker instanceof GenericEffectChecker);
        debugSpew = checker.getLintOption("debugSpew", false);

        effStack = new Stack<Class<? extends Annotation>>();
        currentMethods = new Stack<MethodTree>();

        extension = ext;

        ignoringEffects = checker.getOption("ignoreEffects") != null;
        ignoringWarnings = checker.getOption("ignoreWarnings") != null;
        ignoringErrors = checker.getOption("ignoreErrors") != null;

        genericEffect = ((GenericEffectChecker) checker).getEffectLattice();
    }

    /**
     * Method to instantiate the factory class for the checker.
     *
     * @return The type factory of the checker.
     */
    @Override
    protected GenericEffectTypeFactory createTypeFactory() {
        return new GenericEffectTypeFactory(checker, debugSpew);
    }

    /**
     * This method is here because the inherited version of this method complains about the way that certain
     * checks are done.
     * TODO: Please document the use of this with respect to the generic effect checker better.
     * Note: The GuiEffectChecker uses a similar setup and provides more documentation.
     *
     * @param method
     * @param node
     */
    @Override
    protected void checkMethodInvocability(
            AnnotatedExecutableType method, MethodInvocationTree node) {
    }


    /**
     * Method override validity is checked manually by the type factory during visitation, so
     * the method is overridden here.
     * TODO: Please document the use of this with respect to the generic effect checker better.
     * Note: The GuiEffectChecker uses a similar setup and provides more documentation.
     *
     * @param overriderTree
     * @param enclosingType
     * @param overridden
     * @param overriddenType
     * @param p
     * @return
     */
    @Override
    protected boolean checkOverride(
            MethodTree overriderTree,
            AnnotatedTypeMirror.AnnotatedDeclaredType enclosingType,
            AnnotatedTypeMirror.AnnotatedExecutableType overridden,
            AnnotatedTypeMirror.AnnotatedDeclaredType overriddenType,
            Void p) {
        return true;
    }

    /**
     * TODO: Please document the use off this with respect to the generic effect checker better.
     * Note: The GuiEffectChecker uses a similar setup and provides more documentation.
     *
     * @param node
     */
    @Override
    public void processClassTree(ClassTree node) {
        // Fix up context for static initializers of new class
        currentMethods.push(null);
        effStack.push(genericEffect.getBottomMostEffectInLattice());
        super.processClassTree(node);
        currentMethods.pop();
        effStack.pop();
    }

    /**
     * Method that visits method tree nodes and adds their effects to the stacks set up in the constructor.
     *
     * @param node The method tree node that was encountered during checking.
     * @param p    Void
     * @return Void
     */
    @Override
    public Void visitMethod(MethodTree node, Void p) {

        ExecutableElement methElt = TreeUtils.elementFromDeclaration(node);
        if (debugSpew) {
            System.err.println("\nVisiting method " + methElt);
        }

        assert (methElt != null);

        ArrayList<Class<? extends Annotation>> validEffects = genericEffect.getValidEffects();
        AnnotationMirror annotatedEffect;

        for (Class<? extends Annotation> OkEffect : validEffects) {
            annotatedEffect = atypeFactory.getDeclAnnotation(methElt, OkEffect);

            (atypeFactory)
                    .checkEffectOverride(
                            (TypeElement) (methElt.getEnclosingElement()), methElt, true, node);

            if (annotatedEffect == null) {
                atypeFactory
                        .fromElement(methElt)
                        .addAnnotation(atypeFactory.getDeclaredEffect(methElt));
            }
        }

        currentMethods.push(node);

        effStack.push(atypeFactory.getDeclaredEffect(methElt));

        if (debugSpew) {
            System.err.println(
                    "Pushing " + effStack.peek() + " onto the stack when checking " + methElt);
        }

        Void ret = super.visitMethod(node, p);
        currentMethods.pop();
        effStack.pop();
        return ret;
    }

    /**
     * Method that can be used in a visitor method to see if a node is enclosed by a method.
     *
     * @return A boolean representing whether the node is enclosed by a method (true) or not (false).
     */
    private boolean hasEnclosingMethod() {
        MethodTree callerTree = TreeUtils.enclosingMethod(getCurrentPath());
        return callerTree != null;
    }

    /**
     * Method to check is a target effect and a caller effect are invalid according to the lattice.
     * The method also checks which effects are to be ignored.
     *
     * @param targetEffect Target effect of node.
     * @param callerEffect Caller effect of node.
     * @return Boolean value representing whether the effects are invalid (true) or not (false)
     */
    private boolean isInvalid(Class<? extends Annotation> targetEffect, Class<? extends Annotation> callerEffect) {
        if (ignoringEffects)
            targetEffect = extension.checkIgnoredEffects(checker.getOption("ignoreEffects"), targetEffect);
        if (!genericEffect.LE(targetEffect, callerEffect))
            return true;
        return false;
    }

    /**
     * Method that reports an error as specified by given parameters. The method also checks which errors are to be ignored.
     *
     * @param node Node for which error should be reported.
     * @param targetEffect Target effect of node.
     * @param callerEffect Caller effect of node.
     * @param failureMsg Error message to be reported.
     */
    private void checkError(Tree node, Class<? extends Annotation> targetEffect, Class<? extends Annotation> callerEffect, String failureMsg) {
        if (!ignoringErrors)
            checker.report(Result.failure(failureMsg, targetEffect, callerEffect), node);
        else if (ignoringErrors && !extension.isIgnored(checker.getOption("ignoreErrors"), failureMsg))
            checker.report(Result.failure(failureMsg, targetEffect, callerEffect), node);
    }

    /**
     * Method that reports a warning as specified by the given parameters. The method also checks which warnings are to be ignored.
     *
     * @param node Node for which warning should be reported.
     * @param targetEffect Target effect of node.
     * @param callerEffect Caller effect of node.
     * @param warningMsg Warning message to be reported.
     */
    private void checkWarning(Tree node, Class<? extends Annotation> targetEffect, Class<? extends Annotation> callerEffect, String warningMsg) {
        if (!ignoringWarnings)
            checker.report(Result.warning(warningMsg, targetEffect, callerEffect), node);
        else if (ignoringWarnings && !extension.isIgnored(checker.getOption("ignoreWarnings"), warningMsg))
            checker.report(Result.warning(warningMsg, targetEffect, callerEffect), node);
    }

    /**
     * Method that is used by visitor methods to get the effect of a method that a node is within.
     *
     * @return Effect of a method that a node is within.
     */
    private Class<? extends Annotation> getMethodCallerEffect() {
        MethodTree callerTree = TreeUtils.enclosingMethod(getCurrentPath());
        ExecutableElement callerElt = TreeUtils.elementFromDeclaration(callerTree);
        return atypeFactory.getDeclaredEffect(callerElt);
    }

    /**
     * Method that is used in a visitor method to get the default effect a class that a node is within.
     *
     * @return The default effect of a class that a node is within.
     */
    private Class<? extends Annotation> getDefaultClassEffect() {
        ClassTree clsTree = TreeUtils.enclosingClass(getCurrentPath());
        Element clsElt = TreeUtils.elementFromDeclaration(clsTree);
        return atypeFactory.getDefaultEffect(clsElt);
    }

    /**
     * TODO: This is not supported yet but should be treated similar to a method.
     *
     * @param node
     * @param p
     * @return
     */
    @Override
    public Void visitLambdaExpression(LambdaExpressionTree node, Void p) {
        return super.visitLambdaExpression(node, p);
    }

    /**
     * TODO: Determine if this requires the same effect checks as for methods.
     *
     * @param node
     * @param p
     * @return
     */
    @Override
    public Void visitMemberReference(MemberReferenceTree node, Void p) {
        return super.visitMemberReference(node, p);
    }

    /**
     * TODO: Determine if this requires the same effect checks as for methods.
     *
     * @param node
     * @param p
     * @return
     */
    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void p) {
        return super.visitMemberSelect(node, p);
    }

    /**
     * Method that visits all the method invocation tree nodes and raises failures/warnings for unsafe method invocations.
     *
     * @param node Method invocation tree node that is found during checking.
     * @param p Void
     * @return Void
     */
    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
        if (hasEnclosingMethod()) {
            ExecutableElement elt = TreeUtils.elementFromUse(node);
            Class<? extends Annotation> targetEffect = atypeFactory.getDeclaredEffect(elt);
            Class<? extends Annotation> callerEffect = getMethodCallerEffect();
            if (isInvalid(targetEffect, callerEffect))
                checkError(node, targetEffect, callerEffect, "call.invalid.effect");
        }
        else {
            ExecutableElement elt = TreeUtils.elementFromUse(node);
            Class<? extends Annotation> targetEffect = atypeFactory.getDeclaredEffect(elt);
            Class<? extends Annotation> callerEffect = getDefaultClassEffect();
            if (isInvalid(targetEffect, callerEffect))
                checkError(node, targetEffect, callerEffect, "call.invalid.effect");
        }
        return super.visitMethodInvocation(node, p);
    }

    /**
     * Method to check if the constructor call is made from a valid context.
     *
     * @param node New class tree node that is found during checking.
     * @param p    Void
     * @return Void
     */
    @Override
    public Void visitNewClass(NewClassTree node, Void p) {
        if (hasEnclosingMethod()) {
            ExecutableElement elt = TreeUtils.elementFromUse(node);
            Class<? extends Annotation> targetEffect = atypeFactory.getDeclaredEffect(elt);
            Class<? extends Annotation> callerEffect = getMethodCallerEffect();
            if (isInvalid(targetEffect, callerEffect))
                checkError(node, targetEffect, callerEffect, "constructor.call.invalid");
        }
        else {
            ExecutableElement elt = TreeUtils.elementFromUse(node);
            Class<? extends Annotation> targetEffect = atypeFactory.getDeclaredEffect(elt);
            Class<? extends Annotation> callerEffect = getDefaultClassEffect();
            if (isInvalid(targetEffect, callerEffect))
                checkError(node, targetEffect, callerEffect, "constructor.call.invalid");
        }
        return super.visitNewClass(node, p);
    }

    /**
     * The methods below this comment follow the same format. Each method is a different visit method
     * for a different kind of tree node. Using the extensions class the developer can activate specific
     * visitor methods depending on what they want to check.
     * <p>
     * The methods work by first checking if the node being checked is enclosed by a method. If it is then
     * the method obtains the effect of the node and checks it against the method's effect. If the node is not
     * enclosed by a method, then it checks at the variable level against the class annotation.
     *
     * @param node Specific tree node that is to be checked.
     * @param p    Void
     * @return Void
     */
    @Override
    public Void visitArrayAccess(ArrayAccessTree node, Void p) {
        //checks if check is active
        if (extension.doesArrayAccessCheck()) {
            //checks if node is enclosed by method
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkArrayAccess(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                //checks if the effect of the node is less than or equal to the methods
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                //checks if the node should output any warnings
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
            //if node is not within a method, then node is compared to default effect of class
            else {
                Class<? extends Annotation> targetEffect = extension.checkArrayAccess(node);
                //gets node's default effect
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                //checks if effect of the node is less than or equal to the default effects
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                //checks if the node should output and warnings
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitArrayAccess(node, p);
    }

    @Override
    public Void visitAssignment(AssignmentTree node, Void p) {
        if (extension.doesAssignmentCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkAssignment(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
            else {
                Class<? extends Annotation> targetEffect = extension.checkAssignment(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitAssignment(node, p);
    }

    @Override
    public Void visitBinary(BinaryTree node, Void p) {
        if (extension.doesBinaryCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkBinary(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
            else {
                Class<? extends Annotation> targetEffect = extension.checkBinary(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitBinary(node, p);
    }

    @Override
    public Void visitCompoundAssignment(CompoundAssignmentTree node, Void p) {
        if (extension.doesCompoundAssignmentCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkCompoundAssignment(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
            else {
                Class<? extends Annotation> targetEffect = extension.checkCompoundAssignment(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitCompoundAssignment(node, p);
    }

    @Override
    public Void visitConditionalExpression(ConditionalExpressionTree node, Void p) {
        if (extension.doesConditionalExpressionCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkConditionalExpression(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
            else {
                Class<? extends Annotation> targetEffect = extension.checkConditionalExpression(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitConditionalExpression(node, p);
    }

    @Override
    public Void visitInstanceOf(InstanceOfTree node, Void p) {
        if (extension.doesInstanceOfCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkInstanceOf(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
            else {
                Class<? extends Annotation> targetEffect = extension.checkInstanceOf(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitInstanceOf(node, p);
    }

    @Override
    public Void visitLiteral(LiteralTree node, Void p) {
        if (extension.doesLiteralCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkLiteral(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
            else {
                Class<? extends Annotation> targetEffect = extension.checkLiteral(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitLiteral(node, p);
    }

    @Override
    public Void visitNewArray(NewArrayTree node, Void p) {
        if (extension.doesNewArrayCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkNewArray(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
            else {
                Class<? extends Annotation> targetEffect = extension.checkNewArray(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitNewArray(node, p);
    }

    @Override
    public Void visitTypeCast(TypeCastTree node, Void p) {
        if (extension.doesTypeCastCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkTypeCast(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
            else {
                Class<? extends Annotation> targetEffect = extension.checkTypeCast(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
            return super.visitTypeCast(node, p);
        }
        return null;
    }

    @Override
    public Void visitUnary(UnaryTree node, Void p) {
        if (extension.doesUnaryCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkUnary(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
            else {
                Class<? extends Annotation> varTargetEffect = extension.checkUnary(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(varTargetEffect, callerEffect))
                    checkError(node, varTargetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, varTargetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitUnary(node, p);
    }

}