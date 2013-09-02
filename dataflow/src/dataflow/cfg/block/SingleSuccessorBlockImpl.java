package dataflow.cfg.block;

import dataflow.analysis.Store;

/**
 * Implementation of a non-special basic block.
 *
 * @author Stefan Heule
 *
 */
public abstract class SingleSuccessorBlockImpl extends BlockImpl implements
        SingleSuccessorBlock {

    /** Internal representation of the successor. */
    protected /*@Nullable*/ BlockImpl successor;

    /**
     * The rule below say that EACH store at the end of a single
     * successor block flow to the corresponding store of the successor.
     */
    protected Store.FlowRule storeFlow = Store.FlowRule.EACH_TO_EACH;

    @Override
    public /*@Nullable*/ Block getSuccessor() {
        return successor;
    }

    /**
     * Set a basic block as the successor of this block.
     */
    public void setSuccessor(BlockImpl successor) {
        this.successor = successor;
        successor.addPredecessor(this);
    }

    @Override
    public Store.FlowRule getStoreFlow() {
        return storeFlow;
    }

    @Override
    public void setStoreFlow(Store.FlowRule rule) {
        storeFlow = rule;
    }
}
