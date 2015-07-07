package edu.jhu.nlp.depparse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import edu.jhu.nlp.data.DepEdgeMask;
import edu.jhu.nlp.depparse.DepParseFactorGraphBuilder.DepParseFactorGraphBuilderPrm;
import edu.jhu.pacaya.gm.data.UnlabeledFgExample;
import edu.jhu.pacaya.gm.feat.FeatureExtractor;
import edu.jhu.pacaya.gm.inf.ErmaBp;
import edu.jhu.pacaya.gm.inf.ErmaBp.ErmaBpPrm;
import edu.jhu.pacaya.gm.model.FactorGraph;
import edu.jhu.pacaya.gm.model.FgModel;
import edu.jhu.pacaya.gm.model.Var;
import edu.jhu.pacaya.gm.model.Var.VarType;
import edu.jhu.pacaya.gm.model.VarTensor;
import edu.jhu.pacaya.gm.model.globalfac.LinkVar;
import edu.jhu.pacaya.gm.train.SimpleVCFeatureExtractor;
import edu.jhu.pacaya.util.FeatureNames;
import edu.jhu.pacaya.util.collections.QLists;
import edu.jhu.prim.Primitives;


public class DepParseFactorGraphBuilderTest {

    @Test
    public void testFirstOrderDepParser() {
        DepParseFactorGraphBuilderPrm prm = getDefaultDepParseFactorGraphBuilderPrm();
        FactorGraph sfg = getJointNlpFg(prm);        
        assertEquals(9, sfg.getFactors().size());
        assertTrue(sfg.getBipgraph().isAcyclic());
    }

    protected DepParseFactorGraphBuilderPrm getDefaultDepParseFactorGraphBuilderPrm() {
        DepParseFactorGraphBuilderPrm prm = new DepParseFactorGraphBuilderPrm();
        prm.linkVarType = VarType.PREDICTED;
        prm.unaryFactors = true;
        prm.pruneEdges = false;
        prm.grandparentFactors = false;
        prm.arbitrarySiblingFactors = false;
        prm.headBigramFactors = false;
        return prm;
    }

    @Test
    public void testAddGrandparentFactors() {
        // Grandparents only
        DepParseFactorGraphBuilderPrm prm = getDefaultDepParseFactorGraphBuilderPrm();
        prm.grandparentFactors = true;
        {
        prm.excludeNonprojectiveGrandparents = true;
        FactorGraph sfg = getJointNlpFg(prm);        
        assertEquals(9 + 10, sfg.getFactors().size());
        assertFalse(sfg.getBipgraph().isAcyclic());
        }{
        prm.excludeNonprojectiveGrandparents = false;
        FactorGraph sfg = getJointNlpFg(prm);        
        assertEquals(9 + 12, sfg.getFactors().size());
        assertFalse(sfg.getBipgraph().isAcyclic());
        }
    }
    
    @Test
    public void testAddArbitrarySiblingFactors() {
        // Arbitrary Siblings only 
        DepParseFactorGraphBuilderPrm prm = getDefaultDepParseFactorGraphBuilderPrm();
        prm.arbitrarySiblingFactors = true;
        FactorGraph sfg = getJointNlpFg(prm);        
        assertEquals(9 + 6, sfg.getFactors().size());
        assertFalse(sfg.getBipgraph().isAcyclic());
    }
    
    @Test
    public void testAddHeadBigramFactors() {
        // Head-bigrams only 
        DepParseFactorGraphBuilderPrm prm = getDefaultDepParseFactorGraphBuilderPrm();
        prm.headBigramFactors = true;
        FactorGraph sfg = getJointNlpFg(prm);        
        assertEquals(9 + 18, sfg.getFactors().size());
        assertFalse(sfg.getBipgraph().isAcyclic());
    }

    @Test
    public void testSecondOrderDepParser() {
        DepParseFactorGraphBuilderPrm prm = getDefaultDepParseFactorGraphBuilderPrm();
        FactorGraph sfg;
        // Siblings and Grandparents 
        prm.excludeNonprojectiveGrandparents = false;
        prm.grandparentFactors = true;
        prm.arbitrarySiblingFactors = true;
        sfg = getJointNlpFg(prm);     
        assertEquals(9 + 12 + 6, sfg.getFactors().size());
        assertFalse(sfg.getBipgraph().isAcyclic());
    }
    
    @Test
    public void testSecondOrderDepParserPruned() {
        DepParseFactorGraphBuilderPrm prm = getDefaultDepParseFactorGraphBuilderPrm();       
        FactorGraph sfg;
        
        // Siblings and Grandparents 
        prm.excludeNonprojectiveGrandparents = false;
        prm.grandparentFactors = true;
        prm.arbitrarySiblingFactors = true;
        prm.pruneEdges = true;
        sfg = getJointNlpFg(prm);     
        assertEquals(11, sfg.getFactors().size());
        System.out.println(sfg.getFactors());
        // This pruned version is a tree.
        assertTrue(sfg.getBipgraph().isAcyclic());
        
        sfg.updateFromModel(new FgModel(1000));
        
        ErmaBpPrm bpPrm = new ErmaBpPrm();
        ErmaBp bp = new ErmaBp(sfg, bpPrm);
        bp.run();
        
        // Marginals should yield a left-branching tree.        
        System.out.println("\n\nVariable marginals:\n");
        for (Var v : sfg.getVars()) {
            VarTensor marg = bp.getMarginals(v);
            if (v instanceof LinkVar) {
                LinkVar link = (LinkVar) v;
                if (link.getParent() + 1 == link.getChild()) {
                    // Is left-branching edge.
                    assertTrue(!Primitives.equals(1.0, marg.getValue(LinkVar.FALSE), 1e-13)); 
                    assertTrue(!Primitives.equals(0.0, marg.getValue(LinkVar.TRUE), 1e-13)); 
                } else {
                    // Not left-branching edge.
                    assertEquals(1.0, marg.getValue(LinkVar.FALSE), 1e-13); 
                    assertEquals(0.0, marg.getValue(LinkVar.TRUE), 1e-13); 
                }
            }
            System.out.println(marg);
        }
    }


    public static FactorGraph getJointNlpFg(DepParseFactorGraphBuilderPrm prm) {
        // --- These won't even be used in these tests ---
        FeatureExtractor fe = new SimpleVCFeatureExtractor(new FeatureNames()); 
        // ---                                         ---
        List<String> words = QLists.getList("w1", "w2", "w3");
        // Prune all but a left branching tree.
        DepEdgeMask depEdgeMask = new DepEdgeMask(words.size(), false);
        for (int c=0; c<words.size(); c++) {
            depEdgeMask.setIsKept(c-1, c, true);
        }
        
        FactorGraph fg = new FactorGraph();        
        DepParseFactorGraphBuilder builder = new DepParseFactorGraphBuilder(prm);
        builder.build(words, depEdgeMask, fe, fg);
        
        fe.init(new UnlabeledFgExample(fg));
        return fg;
    }
    
}
