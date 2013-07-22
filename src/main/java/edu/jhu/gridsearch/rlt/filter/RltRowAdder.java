package edu.jhu.gridsearch.rlt.filter;

import ilog.concert.IloException;

import java.util.Collection;

import edu.jhu.gridsearch.rlt.Rlt;
import edu.jhu.util.tuple.OrderedPair;
import edu.jhu.util.tuple.UnorderedPair;

public interface RltRowAdder {

    void init(Rlt rlt, long numUnfilteredRows) throws IloException;

    Collection<OrderedPair> getRltRowsForEq(int startFac, int endFac, int numVars, RowType type);
    Collection<UnorderedPair> getRltRowsForLeq(int startFac1, int endFac1, int startFac2, int endFac2, RowType type);
    
}
