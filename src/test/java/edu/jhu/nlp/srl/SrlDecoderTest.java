package edu.jhu.nlp.srl;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import edu.jhu.nlp.data.conll.SrlGraph;
import edu.jhu.nlp.srl.SrlFactorGraphBuilder.RoleVar;
import edu.jhu.nlp.srl.SrlFactorGraphBuilder.SenseVar;
import edu.jhu.pacaya.gm.model.Var.VarType;
import edu.jhu.pacaya.gm.model.VarConfig;
import edu.jhu.pacaya.util.collections.QLists;

public class SrlDecoderTest {

    @Test
    public void testGetSrlGraph() {
        int n = 3;
        VarConfig vc = new VarConfig();
        vc.put(new SenseVar(VarType.PREDICTED, 2, "s-1", QLists.getList("false","true"), 1), 1);
        vc.put(new RoleVar(VarType.PREDICTED, 2, "r-1_0", QLists.getList("false","true"), 1, 0), 1);
        vc.put(new RoleVar(VarType.PREDICTED, 2, "r-1_2", QLists.getList("false","true"), 1, 2), 0);
        // Self-loop
        vc.put(new SenseVar(VarType.PREDICTED, 2, "s-2", QLists.getList("false","true"), 2), 1);
        vc.put(new RoleVar(VarType.PREDICTED, 2, "r-2_2", QLists.getList("false","true"), 2, 2), 1);
        SrlGraph g = SrlDecoder.getSrlGraphFromVarConfig(vc, n);
        
        System.out.println(g);
        assertEquals(2, g.getNumPreds());
        assertEquals(2, g.getNumArgs());
        
        assertEquals("true", g.getPredAt(1).getLabel());
        assertEquals("true", g.getArgAt(0).getEdges().get(0).getLabel());
        assertEquals("false", g.getArgAt(2).getEdges().get(0).getLabel());
        assertEquals("true", g.getPredAt(2).getLabel());
        assertEquals("true", g.getArgAt(2).getEdges().get(1).getLabel());
    }
    
}
