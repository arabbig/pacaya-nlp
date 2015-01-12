package edu.jhu.gm.data;

import edu.jhu.gm.feat.FactorTemplateList;

/**
 * A factory of instances for a graphical model represented as factor
 * graphs.
 * 
 * @author mgormley
 * 
 */
public interface FgExampleFactory {

    /** Gets the i'th example. */
    public LFgExample get(int i, FactorTemplateList fts);

    /** Gets the number of examples. */
    public int size();

}