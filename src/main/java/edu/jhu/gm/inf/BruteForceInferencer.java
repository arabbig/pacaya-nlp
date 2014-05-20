package edu.jhu.gm.inf;

import edu.jhu.gm.inf.BeliefPropagation.FgInferencerFactory;
import edu.jhu.gm.model.DenseFactor;
import edu.jhu.gm.model.ExplicitFactor;
import edu.jhu.gm.model.Factor;
import edu.jhu.gm.model.FactorGraph;
import edu.jhu.gm.model.Var;
import edu.jhu.gm.model.VarSet;

/**
 * Inference by brute force summation.
 * 
 * @author mgormley
 *
 */
public class BruteForceInferencer implements FgInferencer {
    
    public static class BruteForceInferencerPrm implements FgInferencerFactory {
        public boolean logDomain = true;
        public BruteForceInferencerPrm(boolean logDomain) {
            this.logDomain = logDomain;
        }
        public FgInferencer getInferencer(FactorGraph fg) {
            return new BruteForceInferencer(fg, this.logDomain);
        }
        public boolean isLogDomain() {
            return logDomain;
        }
    }
    
    private FactorGraph fg;
    private DenseFactor joint;
    private boolean logDomain;
    
    public BruteForceInferencer(FactorGraph fg, boolean logDomain) {
        this.fg = fg;
        this.logDomain = logDomain;
    }

    /**
     * Gets the product of all the factors in the factor graph. If working in
     * the log-domain, this will do factor addition.
     * 
     * @param logDomain
     *            Whether to work in the log-domain.
     * @return The product of all the factors.
     */
    private static DenseFactor getProductOfAllFactors(FactorGraph fg, boolean logDomain) {
        DenseFactor joint = new DenseFactor(new VarSet(), logDomain ? 0.0 : 1.0);
        for (int a=0; a<fg.getNumFactors(); a++) {
            Factor f = fg.getFactor(a);
            DenseFactor factor = safeGetDenseFactor(f);
            assert !factor.containsBadValues(logDomain) : factor;
            if (logDomain) {
                joint.add(factor);
            } else {
                joint.prod(factor);
            }
        }
        return joint;
    }

    /** Gets this factor as a DenseFactor. This will construct such a factor if it is not already one. */
    public static DenseFactor safeGetDenseFactor(Factor f) {
        DenseFactor factor;
        if (f instanceof ExplicitFactor) {
            factor = (DenseFactor) f;
        } else {
            // Create a DenseFactor which the values of this non-explicitly represented factor.
            factor = new DenseFactor(f.getVars());
            for (int c=0; c<factor.size(); c++) {
                factor.setValue(c, f.getUnormalizedScore(c));
            }
        }
        return factor;
    }
    
    @Override
    public void run() {        
        joint = getProductOfAllFactors(fg, logDomain);
    }

    /** Gets the unnormalized joint factor over all variables. */
    public DenseFactor getJointFactor() {
        return joint;
    }
    
    @Override
    public DenseFactor getMarginals(Var var) {
        if (logDomain) {
            return joint.getLogMarginal(new VarSet(var), true);
        } else {
            return joint.getMarginal(new VarSet(var), true);
        }
    }

    @Override
    public DenseFactor getMarginals(Factor factor) {
        if (logDomain) {
            return joint.getLogMarginal(factor.getVars(), true);
        } else {
            return joint.getMarginal(factor.getVars(), true);
        }        
    }

    @Override
    public DenseFactor getMarginalsForVarId(int varId) {
        return getMarginals(fg.getVar(varId));
    }

    @Override
    public DenseFactor getMarginalsForFactorId(int factorId) {
        return getMarginals(fg.getFactor(factorId));
    }

    @Override
    public double getPartition() {
        if (joint.getVars().size() == 0) {
            return logDomain ? 0.0 : 1.0;
        }
        if (logDomain) {
            return joint.getLogSum();
        } else {
            return joint.getSum();
        }
    }

    @Override
    public boolean isLogDomain() {
        return logDomain;
    }

}
