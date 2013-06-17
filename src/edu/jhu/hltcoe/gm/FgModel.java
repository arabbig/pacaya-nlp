package edu.jhu.hltcoe.gm;

import edu.jhu.hltcoe.util.Alphabet;

/**
 * A model for a factor graph in the exponential family.
 * 
 * @author mgormley
 *
 */
public class FgModel {

    /**
     * A feature in a factor graph model.
     * 
     * @author mgormley
     *
     */
    public static class Feature {
        
    }
    
    /** The model parameters. */
    private double[] params;
    /** A mapping of feature objects to model parameter indices. */
    private Alphabet<Feature> alphabet;
    
    /** Gets the number of parameters in this model. */
    public int getNumParams() {
        return params.length;
    }
    
}