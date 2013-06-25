package edu.jhu.gm;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

import edu.jhu.gm.Var.VarType;
import edu.jhu.gm.data.BayesNetReaderTest;

public class FactorGraphTest {

    @Test
    public void testIsUndirectedTree() throws IOException {
        FactorGraph fg = getLinearChainGraph();        
        for (int i=0; i<fg.getNumNodes(); i++) {
            assertEquals(true, fg.isUndirectedTree(fg.getNode(i)));
        }
        fg = BayesNetReaderTest.readSimpleFg();
        for (int i=0; i<fg.getNumNodes(); i++) {
            assertEquals(false, fg.isUndirectedTree(fg.getNode(i)));
        }
    }
    
    @Test
    public void testGetConnectedComponents() throws IOException {
        FactorGraph fg = new FactorGraph();

        // Create three tags.
        Var t0 = new Var(VarType.PREDICTED, 2, "t0", null);
        Var t1 = new Var(VarType.PREDICTED, 2, "t1", null);
        Var t2 = new Var(VarType.PREDICTED, 2, "t2", null);

        // Emission factors. 
        Factor emit0 = new Factor(new VarSet(t0)); 
        Factor emit1 = new Factor(new VarSet(t1)); 
        Factor emit2 = new Factor(new VarSet(t2)); 
        
        // Transition factors.
        Factor tran0 = new Factor(new VarSet(t0, t1)); 
        Factor tran1 = new Factor(new VarSet(t1, t2)); 
        
        fg.addFactor(emit0);
        assertEquals(1, fg.getConnectedComponents().size());
        fg.addFactor(emit1);
        assertEquals(2, fg.getConnectedComponents().size());
        fg.addFactor(emit2);
        assertEquals(3, fg.getConnectedComponents().size());
        fg.addFactor(tran0);
        assertEquals(2, fg.getConnectedComponents().size());
        fg.addFactor(tran1);
        assertEquals(1, fg.getConnectedComponents().size());
    }
    
    @Test
    public void testConstruction() {
        FactorGraph fg = getLinearChainGraph();
        
        assertEquals(5, fg.getNumFactors());
        assertEquals(3, fg.getNumVars());
        assertEquals(8, fg.getNumNodes());
        // There is a pair of edges for each emission factor and a 
        assertEquals(3*2 + 2*2*2, fg.getNumEdges());
    }

    /** Gets a simple linear chain CRF consisting of 3 words and 3 tags. */
    public static FactorGraph getLinearChainGraph() {
        FactorGraph fg = new FactorGraph();

        // Create three words.
        Var w0 = new Var(VarType.OBSERVED, 2, "w0", null);
        Var w1 = new Var(VarType.OBSERVED, 2, "w1", null);
        Var w2 = new Var(VarType.OBSERVED, 2, "w2", null);
        
        // Create three tags.
        Var t0 = new Var(VarType.PREDICTED, 2, "t0", null);
        Var t1 = new Var(VarType.PREDICTED, 2, "t1", null);
        Var t2 = new Var(VarType.PREDICTED, 2, "t2", null);

        // Emission factors. 
        Factor emit0 = new Factor(new VarSet(t0)); 
        Factor emit1 = new Factor(new VarSet(t1)); 
        Factor emit2 = new Factor(new VarSet(t2)); 

        emit0.setValue(0, 0.1);
        emit0.setValue(1, 0.9);
        emit1.setValue(0, 0.3);
        emit1.setValue(1, 0.7);
        emit2.setValue(0, 0.5);
        emit2.setValue(1, 0.5);
        
        // Transition factors.
        Factor tran0 = new Factor(new VarSet(t0, t1)); 
        Factor tran1 = new Factor(new VarSet(t1, t2)); 
        
        tran0.set(1);
        tran0.setValue(0, 0.2);
        tran0.setValue(1, 0.3);
        tran0.setValue(2, 0.4);
        tran0.setValue(3, 0.5);
        tran1.set(1);
        tran1.setValue(0, 1.2);
        tran1.setValue(1, 1.3);
        tran1.setValue(2, 1.4);
        tran1.setValue(3, 1.5);
                
        fg.addFactor(emit0);
        fg.addFactor(emit1);
        fg.addFactor(emit2);
        fg.addFactor(tran0);
        fg.addFactor(tran1);
        return fg;
    }

}