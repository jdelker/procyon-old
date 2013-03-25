/*
 * GotoRemoval.java
 *
 * Copyright (c) 2013 Mike Strobel
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */

package com.strobel.decompiler.ast;

import com.strobel.core.StrongBox;
import com.strobel.core.VerifyArgument;
import com.strobel.decompiler.ITextOutput;
import com.strobel.util.ContractUtils;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static com.strobel.core.CollectionUtilities.*;
import static com.strobel.decompiler.ast.PatternMatching.*;

final class GotoRemoval {
    private final static Node NULL_NODE = new Node() {
        @Override
        public void writeTo(final ITextOutput output) {
            throw ContractUtils.unreachable();
        }
    };

    final Map<Node, Label> labels = new IdentityHashMap<>();
    final Map<Node, Node> parentLookup = new IdentityHashMap<>();
    final Map<Node, Node> nextSibling = new IdentityHashMap<>();

    public final void removeGotos(final Block method) {
        parentLookup.put(method, NULL_NODE);

        for (final Node node : method.getSelfAndChildrenRecursive(Node.class)) {
            Node previousChild = null;

            for (final Node child : node.getChildren()) {
                if (parentLookup.containsKey(child)) {
                    throw Error.expressionLinkedFromMultipleLocations(child);
                }

                parentLookup.put(child, node);

                if (previousChild != null) {
                    if (previousChild instanceof Label) {
                        labels.put(child, (Label) previousChild);
                    }
                    nextSibling.put(previousChild, child);
                }

                previousChild = child;
            }

            if (previousChild != null) {
                nextSibling.put(previousChild, NULL_NODE);
            }
        }

        boolean modified;

        do {
            modified = false;

            for (final Expression e : method.getSelfAndChildrenRecursive(Expression.class)) {
                if (e.getCode() == AstCode.Goto) {
                    modified |= trySimplifyGoto(e);
                }
            }
        }
        while (modified);

        removeRedundantCode(method);
    }

    private boolean trySimplifyGoto(final Expression gotoExpression) {
        assert gotoExpression.getCode() == AstCode.Goto;
        assert gotoExpression.getOperand() instanceof Label;

        final Node target = enter(gotoExpression, new LinkedHashSet<Node>());

        if (target == null) {
            return false;
        }

        //
        // The goto expression is marked as visited because we do not want to iterate over
        // nodes which we plan to modify.
        //
        // The simulated path always has to start in the same try block in order for the
        // same finally blocks to be executed.
        //

        final Set<Node> visitedNodes = new LinkedHashSet<>();

        visitedNodes.add(gotoExpression);

        if (target == exit(gotoExpression, visitedNodes)) {
            gotoExpression.setCode(AstCode.Nop);
            gotoExpression.setOperand(null);

            if (target instanceof Expression) {
                ((Expression) target).getRanges().addAll(gotoExpression.getRanges());
            }

            gotoExpression.getRanges().clear();
            return true;
        }

        visitedNodes.clear();
        visitedNodes.add(gotoExpression);

        for (final TryCatchBlock tryCatchBlock : getParents(gotoExpression, TryCatchBlock.class)) {
            final Block finallyBlock = tryCatchBlock.getFinallyBlock();

            if (finallyBlock == null) {
                continue;
            }

            if (target == enter(finallyBlock, visitedNodes)) {
                gotoExpression.setCode(AstCode.Nop);
                gotoExpression.setOperand(null);
                gotoExpression.getRanges().clear();
                return true;
            }
        }

        visitedNodes.clear();
        visitedNodes.add(gotoExpression);

        Node breakBlock = null;

        for (final Node parent : getParents(gotoExpression)) {
            if (parent instanceof Loop || parent instanceof Switch) {
                breakBlock = parent;
                break;
            }
        }

        visitedNodes.clear();
        visitedNodes.add(gotoExpression);

        if (breakBlock != null && target == exit(breakBlock, visitedNodes)) {
            gotoExpression.setCode(AstCode.LoopOrSwitchBreak);
            gotoExpression.setOperand(null);
            return true;
        }

        Loop continueBlock = null;

        for (final Node parent : getParents(gotoExpression)) {
            if (parent instanceof Loop) {
                continueBlock = (Loop) parent;
                break;
            }
        }

        visitedNodes.clear();
        visitedNodes.add(gotoExpression);

        if (continueBlock != null && target == enter(continueBlock, visitedNodes)) {
            gotoExpression.setCode(AstCode.LoopContinue);
            gotoExpression.setOperand(null);
            return true;
        }

        return false;
    }

    private Iterable<Node> getParents(final Node node) {
        return getParents(node, Node.class);
    }

    private <T extends Node> Iterable<T> getParents(final Node node, final Class<T> parentType) {
        return new Iterable<T>() {
            @Override
            public final Iterator<T> iterator() {
                return new Iterator<T>() {
                    T current = updateCurrent(node);

                    @SuppressWarnings("unchecked")
                    private T updateCurrent(Node node) {
                        while (node != null && node != NULL_NODE) {
                            node = parentLookup.get(node);

                            if (parentType.isInstance(node)) {
                                return (T) node;
                            }
                        }

                        return null;
                    }

                    @Override
                    public final boolean hasNext() {
                        return current != null;
                    }

                    @Override
                    public final T next() {
                        final T next = current;

                        if (next == null) {
                            throw new NoSuchElementException();
                        }

                        current = updateCurrent(next);
                        return next;
                    }

                    @Override
                    public final void remove() {
                        throw ContractUtils.unsupported();
                    }
                };
            }
        };
    }

    private Node enter(final Node node, final Set<Node> visitedNodes) {
        VerifyArgument.notNull(node, "node");
        VerifyArgument.notNull(visitedNodes, "visitedNodes");

        if (!visitedNodes.add(node)) {
            //
            // Infinite loop.
            //
            return null;
        }

        if (node instanceof Label) {
            return exit(node, visitedNodes);
        }

        if (node instanceof Expression) {
            final Expression e = (Expression) node;

            switch (e.getCode()) {
                case Goto: {
                    final Label target = (Label) e.getOperand();

                    //
                    // Early exit -- same try block.
                    //
                    if (firstOrDefault(getParents(e, TryCatchBlock.class)) ==
                        firstOrDefault(getParents(target, TryCatchBlock.class))) {

                        return enter(target, visitedNodes);
                    }

                    //
                    // Make sure we are not entering a try block.
                    //
                    final List<TryCatchBlock> sourceTryBlocks = toList(getParents(e, TryCatchBlock.class));
                    final List<TryCatchBlock> targetTryBlocks = toList(getParents(target, TryCatchBlock.class));

                    Collections.reverse(sourceTryBlocks);
                    Collections.reverse(targetTryBlocks);

                    //
                    // Skip blocks we are already in.
                    //
                    int i = 0;

                    while (i < sourceTryBlocks.size() &&
                           i < targetTryBlocks.size() &&
                           sourceTryBlocks.get(i) == targetTryBlocks.get(i)) {
                        i++;
                    }

                    if (i == targetTryBlocks.size()) {
                        return enter(target, visitedNodes);
                    }

                    final TryCatchBlock targetTryBlock = targetTryBlocks.get(i);

                    //
                    // Check that the goto points to the start.
                    //
                    TryCatchBlock current = targetTryBlock;

                    while (current != null) {
                        for (final Node n : current.getTryBlock().getBody()) {
                            if (n instanceof Label) {
                                if (n == target) {
                                    return targetTryBlock;
                                }
                            }
                            else if (!match(n, AstCode.Nop)) {
                                current = n instanceof TryCatchBlock ? (TryCatchBlock) n : null;
                                break;
                            }
                        }
                    }

                    return null;
                }

                default: {
                    return e;
                }
            }
        }

        if (node instanceof Block) {
            final Block block = (Block) node;

            if (block.getEntryGoto() != null) {
                return enter(block.getEntryGoto(), visitedNodes);
            }

            if (block.getBody().isEmpty()) {
                return exit(block, visitedNodes);
            }

            return enter(block.getBody().get(0), visitedNodes);
        }

        if (node instanceof Condition) {
            return ((Condition) node).getCondition();
        }

        if (node instanceof Loop) {
            final Loop loop = (Loop) node;

            if (loop.getCondition() != null) {
                return loop.getCondition();
            }

            return enter(loop.getBody(), visitedNodes);
        }

        if (node instanceof TryCatchBlock) {
            return node;
        }

        if (node instanceof Switch) {
            return ((Switch) node).getCondition();
        }

        throw Error.unsupportedNode(node);
    }

    private Node exit(final Node node, final Set<Node> visitedNodes) {
        VerifyArgument.notNull(node, "node");
        VerifyArgument.notNull(visitedNodes, "visitedNodes");

        final Node parent = parentLookup.get(node);

        if (parent == null || parent == NULL_NODE) {
            //
            // Exited main body.
            //
            return null;
        }

        if (parent instanceof Block) {
            final Node nextNode = nextSibling.get(node);

            if (nextNode != null && nextNode != NULL_NODE) {
                return enter(nextNode, visitedNodes);
            }

            return exit(parent, visitedNodes);
        }

        if (parent instanceof Condition) {
            return exit(parent, visitedNodes);
        }

        if (parent instanceof TryCatchBlock) {
            //
            // Finally blocks are completely ignored.  We rely on the fact that try blocks
            // cannot be entered.
            //
            return exit(parent, visitedNodes);
        }

        if (parent instanceof Switch) {
            //
            // Implicit exit from switch is not allowed.
            //
            return null;
        }

        if (parent instanceof Loop) {
            return enter(parent, visitedNodes);
        }

        throw Error.unsupportedNode(parent);
    }

    @SuppressWarnings("ConstantConditions")
    public static void removeRedundantCode(final Block method) {
        //
        // Remove dead labels and NOPs.
        //

        final Set<Label> liveLabels = new LinkedHashSet<>();
        final StrongBox<Label> target = new StrongBox<>();

    outer:
        for (final Expression e : method.getSelfAndChildrenRecursive(Expression.class)) {
            if (e.isBranch()) {
                if (matchGetOperand(e, AstCode.Goto, target)) {
                    //
                    // See if the goto is an explicit jump to an outer finally.  If so, remove it.
                    //
                    for (final TryCatchBlock tryCatchBlock : method.getSelfAndChildrenRecursive(TryCatchBlock.class)) {
                        final Block finallyBlock = tryCatchBlock.getFinallyBlock();

                        if (finallyBlock == null) {
                            continue;
                        }

                        final Node firstInBody = firstOrDefault(finallyBlock.getBody());

                        if (firstInBody == target.get()) {
                            continue outer;
                        }
                    }
                }
                liveLabels.addAll(e.getBranchTargets());
            }
        }

        for (final Block block : method.getSelfAndChildrenRecursive(Block.class)) {
            final List<Node> body = block.getBody();

            for (int i = 0; i < body.size(); i++) {
                final Node n = body.get(i);

                if (match(n, AstCode.Nop) ||
                    match(n, AstCode.Leave) ||
                    n instanceof Label && !liveLabels.contains(n)) {

                    body.remove(i--);
                }
            }
        }

        //
        // Remove redundant continue statements.
        //

        for (final Loop loop : method.getSelfAndChildrenRecursive(Loop.class)) {
            final Block body = loop.getBody();

            if (matchLast(body, AstCode.LoopContinue)) {
                body.getBody().remove(body.getBody().size() - 1);
            }
        }

        //
        // Remove redundant break at end of case.  Remove empty case blocks.
        //

        for (final Switch switchNode : method.getSelfAndChildrenRecursive(Switch.class)) {
            CaseBlock defaultCase = null;

            final List<CaseBlock> caseBlocks = switchNode.getCaseBlocks();

            for (final CaseBlock caseBlock : caseBlocks) {
                assert caseBlock.getEntryGoto() == null;

                if (caseBlock.getValues().isEmpty()) {
                    defaultCase = caseBlock;
                }

                final List<Node> caseBody = caseBlock.getBody();
                final int size = caseBody.size();

                if (size >= 2) {
                    if (caseBody.get(size - 2).isUnconditionalControlFlow() &&
                        match(caseBody.get(size - 1), AstCode.LoopOrSwitchBreak)) {

                        caseBody.remove(size - 1);
                    }
                }
            }

            if (defaultCase == null ||
                defaultCase.getBody().size() == 1 && match(firstOrDefault(defaultCase.getBody()), AstCode.LoopOrSwitchBreak)) {

                for (int i = 0; i < caseBlocks.size(); i++) {
                    final List<Node> body = caseBlocks.get(i).getBody();

                    if (body.size() == 1 && match(firstOrDefault(body), AstCode.LoopOrSwitchBreak)) {
                        caseBlocks.remove(i--);
                    }
                }
            }
        }

        //
        // Remove redundant return at end of method.
        //

        final List<Node> methodBody = method.getBody();
        final Node lastStatement = lastOrDefault(methodBody);

        if (match(lastStatement, AstCode.Return) &&
            ((Expression) lastStatement).getArguments().isEmpty()) {

            methodBody.remove(methodBody.size() - 1);
        }

        //
        // Remove unreachable return statements.
        //

        boolean modified = false;

        for (final Block block : method.getSelfAndChildrenRecursive(Block.class)) {
            final List<Node> blockBody = block.getBody();

            for (int i = 0; i < blockBody.size() - 1; i++) {
                if (blockBody.get(i).isUnconditionalControlFlow() &&
                    match(blockBody.get(i + 1), AstCode.Return)) {

                    modified = true;
                    blockBody.remove(i-- + 1);
                }
            }
        }

        if (modified) {
            //
            // More removals might be possible.
            //
            new GotoRemoval().removeGotos(method);
        }
    }
}
