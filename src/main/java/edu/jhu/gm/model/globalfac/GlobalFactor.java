package edu.jhu.gm.model.globalfac;

import edu.jhu.gm.inf.FgInferencer;
import edu.jhu.gm.inf.Messages;
import edu.jhu.gm.model.Factor;
import edu.jhu.gm.model.IFgModel;
import edu.jhu.gm.model.FactorGraph.FgNode;

/**
 * A constraint global factor.
 * 
 * Unlike a full global factor, this does not have any parameters or features.
 * 
 * @author mgormley
 */
public interface GlobalFactor extends Factor {

    /**
     * Creates all the messages from this global factor to all its variables.
     * 
     * @param parent The node for this global factor.
     * @param msgs The message containers.
     */
    void createMessages(FgNode parent, Messages[] msgs);

    /**
     * Gets the expected log beliefs for this factor. We include factor's potential function in the
     * expectation since for most constraint factors \chi(x_a) \in \{0,1\}.
     * 
     * E[ln(b(x_a) / \chi(x_a)) ] = \sum_{x_a} b(x_a) ln (b(x_a) / \chi(x_a))
     * 
     * Note: The value should be returned as a real, though the messages may be in a different
     * semiring.
     */
    double getExpectedLogBelief(FgNode parent, Messages[] msgs);
    
    /**
     * Computes all the message adjoints. This method will only be called once
     * per iteration (unlike createMessages).
     * 
     * @param parent The node for this global factor.
     * @param msgs The messages.
     * @param msgsAdj The adjoints of the messages.
     */
    void backwardCreateMessages(FgNode parent, Messages[] msgs, Messages[] msgsAdj);

    /**
     * Adds the expected feature counts for this factor, given the marginal distribution 
     * specified by the inferencer for this factor.
     * 
     * @param counts The object collecting the feature counts.
     * @param multiplier The multiplier for the added feature accounts.
     * @param inferencer The inferencer from which the marginal distribution is taken.
     * @param factorId The id of this factor within the inferencer.
     */
    void addExpectedFeatureCounts(IFgModel counts, double multiplier, FgInferencer inferencer, int factorId);

}
